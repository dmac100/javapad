package javapad.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TerminalScreenTest
{
	static String row(TerminalScreen s, int r)
	{
		return s.text(s.scrollbackSize() + r, 0, s.cols());
	}

	@Test
	public void writesTextAndMovesTheCursor()
	{
		TerminalScreen s = new TerminalScreen(10, 3);
		s.feed("ab\r\ncd");
		assertEquals("ab", row(s, 0));
		assertEquals("cd", row(s, 1));
		assertEquals(2, s.cursorX());
		assertEquals(1, s.cursorY());
	}

	@Test
	public void wrapsAtTheEndOfTheLine()
	{
		TerminalScreen s = new TerminalScreen(4, 3);
		s.feed("abcdef");
		assertEquals("abcd", row(s, 0));
		assertEquals("ef", row(s, 1));
	}

	@Test
	public void scrollsIntoTheScrollback()
	{
		TerminalScreen s = new TerminalScreen(5, 2);
		s.feed("one\r\ntwo\r\nthree");
		assertEquals(1, s.scrollbackSize());
		assertEquals("one", s.text(0, 0, 5));
		assertEquals("two", row(s, 0));
		assertEquals("three", row(s, 1));
	}

	@Test
	public void positionsAndErases()
	{
		TerminalScreen s = new TerminalScreen(10, 3);
		s.feed("hello\u001b[2;3Hxy\u001b[1;3H\u001b[K");
		assertEquals("he", row(s, 0));
		assertEquals("  xy", row(s, 1));
		s.feed("\u001b[2J");
		assertEquals("", row(s, 1));
	}

	@Test
	public void setsColoursAndAttributes()
	{
		TerminalScreen s = new TerminalScreen(10, 2);
		s.feed("\u001b[1;31ma\u001b[0mb\u001b[38;5;200mc\u001b[48;2;1;2;3md");
		TerminalScreen.Line l = s.line(0);
		assertEquals(1, l.fg[0]);
		assertEquals(TerminalScreen.BOLD, l.attr[0]);
		assertEquals(-1, l.fg[1]);
		assertEquals(0, l.attr[1]);
		assertEquals(200, l.fg[2]);
		assertEquals(TerminalScreen.TRUE_COLOR | 0x010203, l.bg[3]);
	}

	@Test
	public void alternateScreenLeavesTheMainOneAlone()
	{
		TerminalScreen s = new TerminalScreen(10, 3);
		s.feed("main\u001b[?1049h\u001b[Halt");
		assertTrue(s.alternate());
		assertEquals("alt", row(s, 0));
		s.feed("\u001b[?1049l");
		assertFalse(s.alternate());
		assertEquals("main", row(s, 0));
	}

	@Test
	public void insertsAndDeletesCharacters()
	{
		TerminalScreen s = new TerminalScreen(10, 2);
		s.feed("abcdef\u001b[1;3H\u001b[2P");
		assertEquals("abef", row(s, 0));
		s.feed("\u001b[1;3H\u001b[1@X");
		assertEquals("abXef", row(s, 0));
	}

	@Test
	public void answersCursorPositionRequests()
	{
		TerminalScreen s = new TerminalScreen(10, 5);
		StringBuilder got = new StringBuilder();
		s.setReply(r -> got.append(r));
		s.feed("\u001b[3;4H\u001b[6n");
		assertEquals("\u001b[3;4R", got.toString());
	}

	@Test
	public void ignoresTitlesAndKeepsTheText()
	{
		TerminalScreen s = new TerminalScreen(20, 2);
		s.feed("\u001b]0;my title\u0007hi\u001b]2;other\u001b\\ there");
		assertEquals("hi there", row(s, 0));
		assertEquals("other", s.title());
	}

	@Test
	public void respectsTheScrollRegion()
	{
		TerminalScreen s = new TerminalScreen(5, 4);
		s.feed("1\r\n2\r\n3\r\n4\u001b[2;3r\u001b[3;1H\n");
		assertEquals("1", row(s, 0));
		assertEquals("3", row(s, 1));
		assertEquals("", row(s, 2));
		assertEquals("4", row(s, 3));
		assertEquals(0, s.scrollbackSize());
	}
}
