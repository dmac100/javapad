package javapad.lang;

// Line ending conversion: every "\r\n", "\r" and "\n" in a text becomes the same ending.
public class LineEndings
{
	public static final String UNIX = "\n";
	public static final String WINDOWS = "\r\n";

	public static String convert(String text, String ending)
	{
		StringBuilder sb = new StringBuilder(text.length() + 16);
		int i = 0;
		while(i < text.length())
		{
			char c = text.charAt(i);
			if(c == '\r')
			{
				sb.append(ending);
				if(i + 1 < text.length() && text.charAt(i + 1) == '\n')
				{
					i++;
				}
			}
			else if(c == '\n')
			{
				sb.append(ending);
			}
			else
			{
				sb.append(c);
			}
			i++;
		}
		return sb.toString();
	}
}
