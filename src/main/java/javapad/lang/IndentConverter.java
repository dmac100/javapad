package javapad.lang;

// Converts the leading whitespace of lines between tabs and spaces, with a tab as 4 columns. Text after the indentation is left alone.
public class IndentConverter
{
	static final int TAB = 4;

	public static String toTabs(String text, int from, int to)
	{
		return convert(text, from, to, true);
	}

	public static String toSpaces(String text, int from, int to)
	{
		return convert(text, from, to, false);
	}

	// Converts lines from..to (0-based, inclusive).
	static String convert(String text, int from, int to, boolean tabs)
	{
		String[] lines = text.split("\n", -1);
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < lines.length; i++)
		{
			if(i > 0)
			{
				sb.append('\n');
			}
			sb.append(i >= from && i <= to ? line(lines[i], tabs) : lines[i]);
		}
		return sb.toString();
	}

	static String line(String line, boolean tabs)
	{
		int width = 0;
		int i = 0;
		while(i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t'))
		{
			width += line.charAt(i) == '\t' ? TAB - width % TAB : 1;
			i++;
		}
		String rest = line.substring(i);
		// a carriage return before the newline stays in the text after the indentation
		StringBuilder sb = new StringBuilder();
		if(tabs)
		{
			for(int k = 0; k < width / TAB; k++)
			{
				sb.append('\t');
			}
			for(int k = 0; k < width % TAB; k++)
			{
				sb.append(' ');
			}
		}
		else
		{
			for(int k = 0; k < width; k++)
			{
				sb.append(' ');
			}
		}
		return sb.append(rest).toString();
	}
}
