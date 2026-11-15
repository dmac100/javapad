package javapad.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Formats Java the way ScratchPad's sources are written: tabs, opening braces on the line of the statement,
// "} else {", and "if(" / "for(" / "while(" without a space. Line breaks inside statements are kept.
public class JavaFormatter
{
	static final Pattern KEYWORD_PAREN = Pattern.compile("\\b(if|for|while|switch|catch|synchronized|try)[ \\t]+\\(");
	static final Pattern PAREN_BRACE = Pattern.compile("(\\)|\\belse|\\btry|\\bfinally|\\bdo)\\{");
	static final Pattern CASE = Pattern.compile("^(case\\b|default\\b\\s*(:|->))");
	static final Pattern ANNOTATION = Pattern.compile("^@[\\w.]+(\\(.*\\))?$");
	static final Pattern WORD_SWITCH = Pattern.compile("\\bswitch\\b");

	static final int NORMAL = 0;
	static final int BLOCK_COMMENT = 1;
	static final int TEXT_BLOCK = 2;

	public static class Result
	{
		public final String text;
		// the last formatted line (0-based), which moves up when lines are joined
		public final int lastLine;

		Result(String text, int lastLine)
		{
			this.text = text;
			this.lastLine = lastLine;
		}
	}

	static class Scan
	{
		String[] code;
		int[] start;
		boolean[] lineComment;
	}

	static class Frame
	{
		char kind;
		int ref;
		boolean isSwitch;
	}

	public static String format(String text)
	{
		return format(text, 0, Integer.MAX_VALUE).text;
	}

	// Formats lines from..to (0-based, inclusive); the rest of the file only provides context.
	public static Result format(String text, int from, int to)
	{
		boolean crlf = text.contains("\r\n");
		String[] split = text.replace("\r\n", "\n").split("\n", -1);
		List<String> lines = new ArrayList<String>(java.util.Arrays.asList(split));
		to = Math.min(to, lines.size() - 1);
		from = Math.max(from, 0);
		to = join(lines, from, to);
		to = spacing(lines, from, to);
		indent(lines, from, to);
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < lines.size(); i++)
		{
			if(i > 0)
			{
				sb.append(crlf ? "\r\n" : "\n");
			}
			sb.append(lines.get(i));
		}
		return new Result(sb.toString(), to);
	}

	// Masks strings, chars and comments with spaces and records the state each line starts in.
	static Scan scan(List<String> lines)
	{
		int n = lines.size();
		Scan s = new Scan();
		s.code = new String[n];
		s.start = new int[n];
		s.lineComment = new boolean[n];
		int state = NORMAL;
		for(int i = 0; i < n; i++)
		{
			String line = lines.get(i);
			s.start[i] = state;
			char[] out = line.toCharArray();
			int j = 0;
			while(j < line.length())
			{
				char c = line.charAt(j);
				if(state == BLOCK_COMMENT)
				{
					if(c == '*' && j + 1 < line.length() && line.charAt(j + 1) == '/')
					{
						out[j] = ' ';
						out[j + 1] = ' ';
						j += 2;
						state = NORMAL;
						continue;
					}
					out[j] = ' ';
					j++;
				}
				else if(state == TEXT_BLOCK)
				{
					if(c == '\\')
					{
						out[j] = ' ';
						if(j + 1 < line.length())
						{
							out[j + 1] = ' ';
						}
						j += 2;
						continue;
					}
					if(line.startsWith("\"\"\"", j))
					{
						out[j] = ' ';
						out[j + 1] = ' ';
						out[j + 2] = ' ';
						j += 3;
						state = NORMAL;
						continue;
					}
					out[j] = ' ';
					j++;
				}
				else if(c == '/' && j + 1 < line.length() && line.charAt(j + 1) == '/')
				{
					s.lineComment[i] = true;
					for(int k = j; k < line.length(); k++)
					{
						out[k] = ' ';
					}
					j = line.length();
				}
				else if(c == '/' && j + 1 < line.length() && line.charAt(j + 1) == '*')
				{
					out[j] = ' ';
					out[j + 1] = ' ';
					j += 2;
					state = BLOCK_COMMENT;
				}
				else if(line.startsWith("\"\"\"", j))
				{
					out[j] = ' ';
					out[j + 1] = ' ';
					out[j + 2] = ' ';
					j += 3;
					state = TEXT_BLOCK;
				}
				else if(c == '"' || c == '\'')
				{
					out[j] = ' ';
					j++;
					while(j < line.length() && line.charAt(j) != c)
					{
						if(line.charAt(j) == '\\')
						{
							out[j] = ' ';
							j++;
							if(j >= line.length())
							{
								break;
							}
						}
						out[j] = ' ';
						j++;
					}
					if(j < line.length())
					{
						out[j] = ' ';
						j++;
					}
				}
				else
				{
					j++;
				}
			}
			s.code[i] = new String(out);
		}
		return s;
	}

	static boolean plain(Scan s, int i)
	{
		return s.start[i] == NORMAL;
	}

	// "{" on its own line moves up to the end of the previous line; "}" then "else"/"catch"/"finally" become "} else".
	static int join(List<String> lines, int from, int to)
	{
		Scan s = scan(lines);
		int i = Math.max(from + 1, 1);
		// work on a copy of the scan arrays that shrinks with the lines
		List<String> code = new ArrayList<String>(java.util.Arrays.asList(s.code));
		List<Boolean> comment = new ArrayList<Boolean>();
		List<Integer> start = new ArrayList<Integer>();
		for(int k = 0; k < lines.size(); k++)
		{
			comment.add(s.lineComment[k]);
			start.add(s.start[k]);
		}
		while(i <= to && i < lines.size())
		{
			String here = code.get(i).trim();
			String before = code.get(i - 1).trim();
			boolean ok = start.get(i) == NORMAL && start.get(i - 1) == NORMAL && !comment.get(i - 1) && !before.isEmpty() && !here.isEmpty();
			boolean brace = false;
			if(ok && here.startsWith("{"))
			{
				char last = before.charAt(before.length() - 1);
				brace = last != ';' && last != '{' && last != '}' && last != ':';
			}
			boolean keep = ok && before.equals("}") && (here.startsWith("else") || here.startsWith("catch") || here.startsWith("finally"));
			if(brace || keep)
			{
				lines.set(i - 1, stripEnd(lines.get(i - 1)) + " " + lines.get(i).trim());
				code.set(i - 1, stripEnd(code.get(i - 1)) + " " + code.get(i).trim());
				comment.set(i - 1, comment.get(i));
				lines.remove(i);
				code.remove(i);
				comment.remove(i);
				start.remove(i);
				to--;
			}
			else
			{
				i++;
			}
		}
		return to;
	}

	static int spacing(List<String> lines, int from, int to)
	{
		Scan s = scan(lines);
		for(int i = from; i <= to && i < lines.size(); i++)
		{
			if(!plain(s, i))
			{
				continue;
			}
			String line = lines.get(i);
			String code = s.code[i];
			for(Pattern p : new Pattern[] {KEYWORD_PAREN, PAREN_BRACE})
			{
				Matcher m = p.matcher(code);
				List<int[]> hits = new ArrayList<int[]>();
				while(m.find())
				{
					hits.add(new int[] {m.start(), m.end()});
				}
				for(int h = hits.size() - 1; h >= 0; h--)
				{
					int a = hits.get(h)[0];
					int b = hits.get(h)[1];
					String fixed;
					if(p == KEYWORD_PAREN)
					{
						fixed = code.substring(a, b).replaceAll("[ \\t]+\\(", "(");
					}
					else
					{
						fixed = code.substring(a, b - 1) + " {";
					}
					line = line.substring(0, a) + fixed + line.substring(b);
					code = code.substring(0, a) + fixed + code.substring(b);
				}
			}
			lines.set(i, line);
			s.code[i] = code;
		}
		return to;
	}

	static void indent(List<String> lines, int from, int to)
	{
		Scan s = scan(lines);
		List<Frame> stack = new ArrayList<Frame>();
		boolean ended = true;
		int stmt = 0;
		int commentIndent = 0;
		for(int i = 0; i < lines.size(); i++)
		{
			String orig = lines.get(i);
			String code = s.code[i];
			boolean inRange = i >= from && i <= to;
			String trimmed = orig.trim();
			String t = code.trim();
			int level;
			if(s.start[i] == BLOCK_COMMENT)
			{
				if(inRange && trimmed.startsWith("*"))
				{
					lines.set(i, tabs(commentIndent) + " " + stripEnd(trimmed));
				}
				else if(inRange && trimmed.isEmpty())
				{
					lines.set(i, "");
				}
				scanLine(code, stack, 0, stmt);
				continue;
			}
			if(s.start[i] == TEXT_BLOCK)
			{
				scanLine(code, stack, 0, stmt);
				continue;
			}
			Frame top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
			boolean closer = t.startsWith("}") || t.startsWith(")") || t.startsWith("]");
			boolean newStatement = false;
			if(top == null)
			{
				level = ended ? 0 : 1;
				newStatement = ended;
			}
			else if(top.kind != '{')
			{
				level = closer ? top.ref : top.ref + 1;
			}
			else if(t.startsWith("}"))
			{
				level = top.ref;
				newStatement = true;
			}
			else
			{
				// "case" lines are one level in from the switch and their statements another level in
				level = top.ref + 1;
				if(top.isSwitch && !CASE.matcher(t).find())
				{
					level++;
				}
				if(ended)
				{
					newStatement = true;
				}
				else
				{
					level++;
				}
			}
			if(t.isEmpty())
			{
				// blank line or a comment on its own
				if(trimmed.isEmpty())
				{
					if(inRange)
					{
						lines.set(i, "");
					}
				}
				else
				{
					int shown = existing(orig);
					if(inRange)
					{
						lines.set(i, tabs(level) + stripEnd(trimmed));
						shown = level;
					}
					if(trimmed.startsWith("/*") && !trimmed.contains("*/"))
					{
						commentIndent = shown;
					}
				}
				scanLine(code, stack, 0, stmt);
				continue;
			}
			if(!inRange)
			{
				level = existing(orig);
			}
			else
			{
				lines.set(i, tabs(level) + stripEnd(trimmed));
			}
			if(newStatement)
			{
				stmt = level;
			}
			scanLine(code, stack, level, stmt);
			Frame after = stack.isEmpty() ? null : stack.get(stack.size() - 1);
			char last = t.charAt(t.length() - 1);
			if(last == ';' || last == '{' || last == '}')
			{
				ended = true;
			}
			else if(last == ':' && (CASE.matcher(t).find() || t.matches("\\w+\\s*:")))
			{
				ended = true;
			}
			else if(last == ',' && (after == null || after.kind == '{'))
			{
				ended = true;
			}
			else if(ANNOTATION.matcher(t).matches())
			{
				ended = true;
			}
			else
			{
				ended = false;
			}
		}
	}

	// Pushes and pops the brackets on one masked line; returns whether anything was pushed.
	static boolean scanLine(String code, List<Frame> stack, int level, int stmt)
	{
		boolean pushed = false;
		for(int k = 0; k < code.length(); k++)
		{
			char c = code.charAt(k);
			if(c == '{' || c == '(' || c == '[')
			{
				Frame f = new Frame();
				f.kind = c;
				Frame top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
				if(c == '{')
				{
					f.ref = top != null && top.kind != '{' ? level : stmt;
					f.isSwitch = WORD_SWITCH.matcher(code.substring(0, k)).find();
				}
				else
				{
					f.ref = level;
				}
				stack.add(f);
				pushed = true;
			}
			else if((c == '}' || c == ')' || c == ']') && !stack.isEmpty())
			{
				stack.remove(stack.size() - 1);
			}
		}
		return pushed;
	}

	static int existing(String line)
	{
		int width = 0;
		for(int i = 0; i < line.length(); i++)
		{
			char c = line.charAt(i);
			if(c == '\t')
			{
				width += 4;
			}
			else if(c == ' ')
			{
				width++;
			}
			else
			{
				break;
			}
		}
		return width / 4;
	}

	static String tabs(int n)
	{
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < n; i++)
		{
			sb.append('\t');
		}
		return sb.toString();
	}

	static String stripEnd(String s)
	{
		int end = s.length();
		while(end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\t'))
		{
			end--;
		}
		return s.substring(0, end);
	}
}
