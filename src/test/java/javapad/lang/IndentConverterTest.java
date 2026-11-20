package javapad.lang;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IndentConverterTest {
	@Test
	public void spacesBecomeTabs() {
		assertEquals("a\n\tb\n\t\tc  d\n\t  e", IndentConverter.toTabs("a\n    b\n        c  d\n      e", 0, 99));
	}

	@Test
	public void tabsBecomeSpacesAndMixedIndentsUseTabStops() {
		assertEquals("a\n    b\n        c\t d\n    e", IndentConverter.toSpaces("a\n\tb\n\t\tc\t d\n  \te", 0, 99));
	}

	@Test
	public void onlyTheChosenLinesChange() {
		assertEquals("    a\n\tb\n    c", IndentConverter.toTabs("    a\n    b\n    c", 1, 1));
	}

	@Test
	public void keepsLineEndingsAndTheTrailingNewline() {
		assertEquals("\ta\r\n\tb\n", IndentConverter.toTabs("    a\r\n    b\n", 0, 99));
	}
}
