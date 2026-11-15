package javapad.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class AnsiParserTest
{
	static final String ESC = "\u001b";

	@Test
	public void plainTextPassesThrough()
	{
		List<AnsiParser.Span> spans = new AnsiParser().parse("hello\nworld");
		assertEquals(1, spans.size());
		assertEquals("hello\nworld", spans.get(0).text);
		assertTrue(spans.get(0).style.plain());
	}

	@Test
	public void colorsAndReset()
	{
		List<AnsiParser.Span> spans = new AnsiParser().parse("a" + ESC + "[31mred" + ESC + "[0m plain" + ESC + "[1;92mbold green" + ESC + "[m");
		assertEquals(4, spans.size());
		assertEquals("a", spans.get(0).text);
		assertEquals("red", spans.get(1).text);
		assertEquals(1, spans.get(1).style.fg);
		assertEquals(" plain", spans.get(2).text);
		assertTrue(spans.get(2).style.plain());
		assertEquals("bold green", spans.get(3).text);
		assertEquals(10, spans.get(3).style.fg);
		assertTrue(spans.get(3).style.bold);
	}

	@Test
	public void backgroundsAndAttributes()
	{
		AnsiParser.Style st = new AnsiParser().parse(ESC + "[44;4;3;7mx").get(0).style;
		assertEquals(4, st.bg);
		assertTrue(st.underline);
		assertTrue(st.italic);
		assertTrue(st.reverse);
		AnsiParser p = new AnsiParser();
		p.parse(ESC + "[1;4;31m");
		AnsiParser.Style off = p.parse(ESC + "[22;24;39mx").get(0).style;
		assertTrue(off.plain());
	}

	@Test
	public void extendedColors()
	{
		AnsiParser.Style a = new AnsiParser().parse(ESC + "[38;5;196mx").get(0).style;
		assertEquals(196, a.fg);
		AnsiParser.Style b = new AnsiParser().parse(ESC + "[48;2;10;20;30mx").get(0).style;
		assertEquals(AnsiParser.RGB | (10 << 16) | (20 << 8) | 30, b.bg);
	}

	@Test
	public void colorCarriesAcrossChunks()
	{
		AnsiParser p = new AnsiParser();
		assertEquals(31 - 30, p.parse(ESC + "[31mone\n").get(0).style.fg);
		assertEquals(1, p.parse("two\n").get(0).style.fg);
		assertTrue(p.parse(ESC + "[0mthree").get(0).style.plain());
	}

	@Test
	public void splitEscapeSequences()
	{
		AnsiParser p = new AnsiParser();
		assertTrue(p.parse("ab" + ESC).size() == 1);
		List<AnsiParser.Span> rest = p.parse("[3");
		assertTrue(rest.isEmpty());
		List<AnsiParser.Span> done = p.parse("2mgreen");
		assertEquals("green", done.get(0).text);
		assertEquals(2, done.get(0).style.fg);
	}

	@Test
	public void otherSequencesAreStripped()
	{
		List<AnsiParser.Span> spans = new AnsiParser().parse("a" + ESC + "[2K" + ESC + "[1;1Hb" + ESC + "]0;title\u0007c" + ESC + "(Bd");
		StringBuilder all = new StringBuilder();
		for(AnsiParser.Span s : spans)
		{
			all.append(s.text);
			assertFalse(s.text.contains(ESC));
		}
		assertEquals("abcd", all.toString());
	}
}
