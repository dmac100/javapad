package javapad.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BracketMatcherTest
{
	static int[] find(String text, int caret)
	{
		return BracketMatcher.find(text, BracketMatcher.codeMask(text), caret);
	}

	@Test
	public void matchesForwardAndBackward()
	{
		String t = "f(a, (b), c)";
		assertArrayEquals(new int[] {1, 11}, find(t, 1));
		assertArrayEquals(new int[] {11, 1}, find(t, 12));
		assertArrayEquals(new int[] {5, 7}, find(t, 5));
	}

	@Test
	public void prefersTheBracketBeforeTheCaret()
	{
		assertArrayEquals(new int[] {1, 2}, find("(()", 2));
		assertArrayEquals(new int[] {0, 1}, find("()", 1));
	}

	@Test
	public void ignoresBracketsInStringsCharsAndComments()
	{
		String t = "f(\")\" + ')' /* ) */ + 1) // )";
		assertArrayEquals(new int[] {1, t.indexOf("1)") + 1}, find(t, 1));
	}

	@Test
	public void ignoresBracketsInMultiLineComments()
	{
		String t = "{\n/* } */\n}";
		assertArrayEquals(new int[] {0, t.length() - 1}, find(t, 0));
	}

	@Test
	public void onlyMatchesTheSameKind()
	{
		assertArrayEquals(new int[] {0, 6}, find("{ ( ) }", 0));
		assertArrayEquals(new int[] {2, 4}, find("{ ( ) }", 2));
	}

	@Test
	public void unmatchedAndNone()
	{
		assertNull(find("f(a", 1));
		assertNull(find("abc", 1));
		assertNull(find("", 0));
	}
}
