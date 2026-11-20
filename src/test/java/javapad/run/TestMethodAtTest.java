package javapad.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TestMethodAtTest {
	static final String SRC = "class A {\n"
		+ "\t@Test\n"
		+ "\tpublic void first() {\n"
		+ "\t\tif(x) { y(); }\n"
		+ "\t}\n"
		+ "\n"
		+ "\tvoid helper() {\n"
		+ "\t}\n"
		+ "\n"
		+ "\t@Test(expected = IllegalStateException.class)\n"
		+ "\tpublic void second() throws Exception {\n"
		+ "\t}\n"
		+ "\n"
		+ "\t@org.junit.jupiter.api.Test @Disabled\n"
		+ "\tstatic <T> List<T> third() {\n"
		+ "\t}\n"
		+ "}\n";

	static int at(String marker) {
		return SRC.indexOf(marker);
	}

	@Test
	public void findsTheMethodUnderTheCaret() {
		assertEquals("first", TestFinder.methodAt(SRC, at("@Test\n")));
		assertEquals("first", TestFinder.methodAt(SRC, at("y();")));
		assertEquals("second", TestFinder.methodAt(SRC, at("public void second")));
		assertEquals("third", TestFinder.methodAt(SRC, at("static <T>")));
	}

	@Test
	public void helpersAndClassBodyAreNotTests() {
		assertNull(TestFinder.methodAt(SRC, at("void helper")));
		assertNull(TestFinder.methodAt(SRC, 3));
		assertNull(TestFinder.methodAt(SRC, SRC.length() - 1));
	}

	@Test
	public void selectableNames() {
		assertEquals("adds", TestFinder.selectableMethod("adds()"));
		assertEquals("adds", TestFinder.selectableMethod("adds"));
		assertNull(TestFinder.selectableMethod("[1] 3"));
		assertNull(TestFinder.selectableMethod("<class>"));
	}

	@Test
	public void findsTheLineOfADeclaration() {
		assertEquals(3, TestFinder.methodLine(SRC, "first"));
		assertEquals(11, TestFinder.methodLine(SRC, "second"));
		assertEquals(15, TestFinder.methodLine(SRC, "third"));
		assertEquals(-1, TestFinder.methodLine(SRC, "helper"));
	}
}
