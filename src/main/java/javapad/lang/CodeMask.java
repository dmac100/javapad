package javapad.lang;

import java.util.Arrays;
import java.util.List;

// Which characters of Java source are real code, as opposed to strings, char literals and comments.
public class CodeMask
{
	public static final int NORMAL = 0;
	public static final int BLOCK_COMMENT = 1;
	public static final int TEXT_BLOCK = 2;

	public String[] code;
	public int[] start;
	public boolean[] lineComment;
	// per line, per character: true for code
	public boolean[][] isCode;

	// Scans the lines once: code[] is each line with strings, chars and comments blanked out, start[] the state each line begins in.
	public static CodeMask of(List<String> lines)
	{
		int n = lines.size();
		CodeMask s = new CodeMask();
		s.code = new String[n];
		s.start = new int[n];
		s.lineComment = new boolean[n];
		s.isCode = new boolean[n][];
		int state = NORMAL;
		for(int i = 0; i < n; i++)
		{
			String line = lines.get(i);
			s.start[i] = state;
			char[] out = line.toCharArray();
			boolean[] flag = new boolean[line.length()];
			Arrays.fill(flag, true);
			int j = 0;
			while(j < line.length())
			{
				char c = line.charAt(j);
				if(state == BLOCK_COMMENT)
				{
					if(c == '*' && j + 1 < line.length() && line.charAt(j + 1) == '/')
					{
						blank(out, flag, j);
						blank(out, flag, j + 1);
						j += 2;
						state = NORMAL;
						continue;
					}
					blank(out, flag, j);
					j++;
				}
				else if(state == TEXT_BLOCK)
				{
					if(c == '\\')
					{
						blank(out, flag, j);
						if(j + 1 < line.length())
						{
							blank(out, flag, j + 1);
						}
						j += 2;
						continue;
					}
					if(line.startsWith("\"\"\"", j))
					{
						blank(out, flag, j);
						blank(out, flag, j + 1);
						blank(out, flag, j + 2);
						j += 3;
						state = NORMAL;
						continue;
					}
					blank(out, flag, j);
					j++;
				}
				else if(c == '/' && j + 1 < line.length() && line.charAt(j + 1) == '/')
				{
					s.lineComment[i] = true;
					for(int k = j; k < line.length(); k++)
					{
						blank(out, flag, k);
					}
					j = line.length();
				}
				else if(c == '/' && j + 1 < line.length() && line.charAt(j + 1) == '*')
				{
					blank(out, flag, j);
					blank(out, flag, j + 1);
					j += 2;
					state = BLOCK_COMMENT;
				}
				else if(line.startsWith("\"\"\"", j))
				{
					blank(out, flag, j);
					blank(out, flag, j + 1);
					blank(out, flag, j + 2);
					j += 3;
					state = TEXT_BLOCK;
				}
				else if(c == '"' || c == '\'')
				{
					blank(out, flag, j);
					j++;
					while(j < line.length() && line.charAt(j) != c)
					{
						if(line.charAt(j) == '\\')
						{
							blank(out, flag, j);
							j++;
							if(j >= line.length())
							{
								break;
							}
						}
						blank(out, flag, j);
						j++;
					}
					if(j < line.length())
					{
						blank(out, flag, j);
						j++;
					}
				}
				else
				{
					j++;
				}
			}
			s.code[i] = new String(out);
			s.isCode[i] = flag;
		}
		return s;
	}


	static void blank(char[] out, boolean[] flag, int at)
	{
		out[at] = ' ';
		flag[at] = false;
	}

	// One flag per character of the text; the newlines count as code.
	public static boolean[] flags(String text)
	{
		List<String> lines = Arrays.asList(text.split("\n", -1));
		CodeMask mask = of(lines);
		boolean[] out = new boolean[text.length()];
		int at = 0;
		for(int i = 0; i < lines.size(); i++)
		{
			boolean[] line = mask.isCode[i];
			for(int j = 0; j < line.length; j++)
			{
				out[at++] = line[j];
			}
			if(at < out.length)
			{
				out[at++] = true;
			}
		}
		return out;
	}
}
