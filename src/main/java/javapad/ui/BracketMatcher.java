package javapad.ui;

public class BracketMatcher
{
	static final String OPEN = "([{";
	static final String CLOSE = ")]}";

	// true for every character that is real code, false inside strings, char literals and comments
	public static boolean[] codeMask(String text)
	{
		boolean[] code = new boolean[text.length()];
		JavaLexer lexer = new JavaLexer();
		int state = JavaLexer.STATE_NORMAL;
		int start = 0;
		while(start <= text.length())
		{
			int end = text.indexOf('\n', start);
			if(end < 0)
			{
				end = text.length();
			}
			String line = text.substring(start, end);
			int[] styles = lexer.styles(line, state);
			state = lexer.endState;
			for(int i = 0; i < line.length(); i++)
			{
				int s = styles[i];
				code[start + i] = s != JavaLexer.STRING && s != JavaLexer.COMMENT && s != JavaLexer.DOC;
			}
			if(end < text.length())
			{
				code[end] = true;
			}
			start = end + 1;
		}
		return code;
	}

	// The bracket next to the caret (before it, else after it) and its partner, as {bracket, partner}; null if none.
	public static int[] find(String text, boolean[] code, int caret)
	{
		int[] candidates = {caret - 1, caret};
		for(int i : candidates)
		{
			if(i < 0 || i >= text.length() || !code[i])
			{
				continue;
			}
			char c = text.charAt(i);
			int open = OPEN.indexOf(c);
			if(open >= 0)
			{
				int j = scan(text, code, i, c, CLOSE.charAt(open), 1);
				return j < 0 ? null : new int[] {i, j};
			}
			int close = CLOSE.indexOf(c);
			if(close >= 0)
			{
				int j = scan(text, code, i, c, OPEN.charAt(close), -1);
				return j < 0 ? null : new int[] {i, j};
			}
		}
		return null;
	}

	static int scan(String text, boolean[] code, int from, char same, char other, int step)
	{
		int depth = 0;
		for(int k = from; k >= 0 && k < text.length(); k += step)
		{
			if(!code[k])
			{
				continue;
			}
			char c = text.charAt(k);
			if(c == same)
			{
				depth++;
			}
			else if(c == other)
			{
				depth--;
				if(depth == 0)
				{
					return k;
				}
			}
		}
		return -1;
	}
}
