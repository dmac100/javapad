package javapad.lang;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SelectionRangesTest
{
	static final String SRC = "package p;\n"
		+ "public class Main {\n"
		+ "\tint f(int a, int b) {\n"
		+ "\t\tint x = compute(a + 1, \"hi there\");\n"
		+ "\t\treturn x;\n"
		+ "\t}\n"
		+ "}\n";

	static String grow(String text, String from, int times)
	{
		int s = text.indexOf(from);
		int e = s;
		for(int i = 0; i < times; i++)
		{
			int[] r = SelectionRanges.expand(text, s, e, true);
			s = r[0];
			e = r[1];
		}
		return text.substring(s, e);
	}

	@Test
	public void growsFromAWordOutwards()
	{
		String from = "ompute";
		assertEquals("compute", grow(SRC, from, 1));
		assertEquals("compute(a + 1, \"hi there\")", grow(SRC, from, 2));
		assertEquals("int x = compute(a + 1, \"hi there\");", grow(SRC, from, 3));
		assertEquals("int x = compute(a + 1, \"hi there\");\n\t\treturn x;", grow(SRC, from, 4));
		assertEquals("{\n\t\tint x = compute(a + 1, \"hi there\");\n\t\treturn x;\n\t}", grow(SRC, from, 5));
		assertEquals(SRC.substring(SRC.indexOf("int f"), SRC.indexOf("\t}\n}") + 2), grow(SRC, from, 6));
	}

	@Test
	public void growsThroughArgumentsAndStrings()
	{
		assertEquals("hi", grow(SRC, "hi", 1));
		assertEquals("hi there", grow(SRC, "hi", 2));
		assertEquals("\"hi there\"", grow(SRC, "hi", 3));
		assertEquals("a + 1, \"hi there\"", grow(SRC, "hi", 4));
		assertEquals("a + 1", grow(SRC, "+ 1", 1));
	}

	@Test
	public void endsAtTheWholeText()
	{
		assertEquals(SRC, grow(SRC, "x;", 20));
	}

	@Test
	public void shrinksToTheLargestPartAroundTheCaret()
	{
		int s = SRC.indexOf("int x");
		int e = SRC.indexOf(";", s) + 1;
		int caret = SRC.indexOf("compute");
		int[] r = SelectionRanges.shrink(SRC, s, e, caret, true);
		assertEquals("compute(a + 1, \"hi there\")", SRC.substring(r[0], r[1]));
	}

	@Test
	public void plainTextUsesWordsLinesAndBrackets()
	{
		String text = "first line\n  call(one two)  \nlast";
		int at = text.indexOf("ne two");
		int[] r = SelectionRanges.expand(text, at, at, false);
		assertEquals("one", text.substring(r[0], r[1]));
		r = SelectionRanges.expand(text, r[0], r[1], false);
		assertEquals("one two", text.substring(r[0], r[1]));
		r = SelectionRanges.expand(text, r[0], r[1], false);
		assertEquals("(one two)", text.substring(r[0], r[1]));
		r = SelectionRanges.expand(text, r[0], r[1], false);
		assertEquals("call(one two)", text.substring(r[0], r[1]));
	}
}
