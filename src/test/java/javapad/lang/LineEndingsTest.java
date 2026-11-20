package javapad.lang;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LineEndingsTest {
	@Test
	public void toWindows() {
		assertEquals("a\r\nb\r\n\r\nc", LineEndings.convert("a\nb\r\n\nc", LineEndings.WINDOWS));
	}

	@Test
	public void toUnix() {
		assertEquals("a\nb\n\nc\n", LineEndings.convert("a\r\nb\r\n\r\nc\r", LineEndings.UNIX));
	}

	@Test
	public void unchanged() {
		assertEquals("abc", LineEndings.convert("abc", LineEndings.WINDOWS));
	}
}
