package javapad.ui.editor;

import javapad.lang.CodeMask;

public class BracketMatcher
{
	static final String OPEN = "([{";
	static final String CLOSE = ")]}";

	// true for every character that is real code, false inside strings, char literals and comments
	public static boolean[] codeMask(String text)
	{
		return CodeMask.flags(text);
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
