package javapad.ui;

import java.util.ArrayList;
import java.util.List;

// Splits program output into text runs with their ANSI SGR (colour/bold/underline) styles. State carries across calls,
// so an escape sequence or a colour that spans two chunks of output still works. Other escape sequences are dropped.
public class AnsiParser
{
	public static final int DEFAULT = -1;
	public static final int RGB = 0x1000000;

	public static class Style
	{
		public int fg = DEFAULT;
		public int bg = DEFAULT;
		public boolean bold;
		public boolean italic;
		public boolean underline;
		public boolean reverse;

		Style copy()
		{
			Style s = new Style();
			s.fg = fg;
			s.bg = bg;
			s.bold = bold;
			s.italic = italic;
			s.underline = underline;
			s.reverse = reverse;
			return s;
		}

		public boolean plain()
		{
			return fg == DEFAULT && bg == DEFAULT && !bold && !italic && !underline && !reverse;
		}
	}

	public static class Span
	{
		public final String text;
		public final Style style;

		Span(String text, Style style)
		{
			this.text = text;
			this.style = style;
		}
	}

	private Style current = new Style();
	private String carry = "";

	public List<Span> parse(String input)
	{
		List<Span> spans = new ArrayList<Span>();
		String s = carry + input;
		carry = "";
		StringBuilder run = new StringBuilder();
		int i = 0;
		while(i < s.length())
		{
			char c = s.charAt(i);
			if(c != '\u001b')
			{
				run.append(c);
				i++;
				continue;
			}
			int end = escapeEnd(s, i);
			if(end < 0)
			{
				carry = s.length() - i > 256 ? "" : s.substring(i);
				break;
			}
			if(s.startsWith("[", i + 1) && s.charAt(end - 1) == 'm')
			{
				flush(spans, run);
				apply(s.substring(i + 2, end - 1));
			}
			i = end;
		}
		flush(spans, run);
		return spans;
	}

	void flush(List<Span> spans, StringBuilder run)
	{
		if(run.length() > 0)
		{
			spans.add(new Span(run.toString(), current));
			run.setLength(0);
		}
	}

	// Index just past the escape sequence starting at i, or -1 if it is not complete yet.
	static int escapeEnd(String s, int i)
	{
		if(i + 1 >= s.length())
		{
			return -1;
		}
		char kind = s.charAt(i + 1);
		if(kind == '[')
		{
			for(int j = i + 2; j < s.length(); j++)
			{
				char c = s.charAt(j);
				if(c >= 0x40 && c <= 0x7e)
				{
					return j + 1;
				}
			}
			return -1;
		}
		if(kind == ']')
		{
			for(int j = i + 2; j < s.length(); j++)
			{
				if(s.charAt(j) == '\u0007')
				{
					return j + 1;
				}
				if(s.charAt(j) == '\u001b')
				{
					return j + 1 < s.length() ? j + 2 : -1;
				}
			}
			return -1;
		}
		if("()*+#%".indexOf(kind) >= 0)
		{
			// character set selection and similar: ESC ( B
			return i + 3 <= s.length() ? i + 3 : -1;
		}
		return i + 2;
	}

	void apply(String params)
	{
		Style s = current.copy();
		String[] parts = params.isEmpty() ? new String[] {"0"} : params.split("[;:]", -1);
		int[] n = new int[parts.length];
		for(int k = 0; k < parts.length; k++)
		{
			try
			{
				n[k] = parts[k].isEmpty() ? 0 : Integer.parseInt(parts[k]);
			}
			catch(NumberFormatException e)
			{
				n[k] = 0;
			}
		}
		for(int k = 0; k < n.length; k++)
		{
			int v = n[k];
			if(v == 0)
			{
				s = new Style();
			}
			else if(v == 1)
			{
				s.bold = true;
			}
			else if(v == 3)
			{
				s.italic = true;
			}
			else if(v == 4)
			{
				s.underline = true;
			}
			else if(v == 7)
			{
				s.reverse = true;
			}
			else if(v == 22)
			{
				s.bold = false;
			}
			else if(v == 23)
			{
				s.italic = false;
			}
			else if(v == 24)
			{
				s.underline = false;
			}
			else if(v == 27)
			{
				s.reverse = false;
			}
			else if(v >= 30 && v <= 37)
			{
				s.fg = v - 30;
			}
			else if(v >= 90 && v <= 97)
			{
				s.fg = v - 90 + 8;
			}
			else if(v >= 40 && v <= 47)
			{
				s.bg = v - 40;
			}
			else if(v >= 100 && v <= 107)
			{
				s.bg = v - 100 + 8;
			}
			else if(v == 39)
			{
				s.fg = DEFAULT;
			}
			else if(v == 49)
			{
				s.bg = DEFAULT;
			}
			else if(v == 38 || v == 48)
			{
				int color = DEFAULT;
				if(k + 2 < n.length && n[k + 1] == 5)
				{
					color = Math.max(0, Math.min(255, n[k + 2]));
					k += 2;
				}
				else if(k + 4 < n.length && n[k + 1] == 2)
				{
					color = RGB | (clamp(n[k + 2]) << 16) | (clamp(n[k + 3]) << 8) | clamp(n[k + 4]);
					k += 4;
				}
				if(v == 38)
				{
					s.fg = color;
				}
				else
				{
					s.bg = color;
				}
			}
		}
		current = s;
	}

	static int clamp(int v)
	{
		return Math.max(0, Math.min(255, v));
	}
}
