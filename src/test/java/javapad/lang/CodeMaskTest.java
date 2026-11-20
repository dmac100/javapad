package javapad.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class CodeMaskTest {
	// the characters of text that are code, with everything else shown as a space
	static String code(String text) {
		boolean[] flags = CodeMask.flags(text);
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < text.length(); i++) {
			sb.append(flags[i] ? text.charAt(i) : '~');
		}
		return sb.toString();
	}

	@Test
	public void stringsAndEscapesAreNotCode() {
		assertEquals("a = ~~~~~~~~; b", code("a = \"x\\\"{ }\"; b"));
	}

	@Test
	public void charLiteralsAreNotCode() {
		assertEquals("c = ~~~~; d", code("c = '\\''; d"));
	}

	@Test
	public void lineCommentsRunToTheEndOfTheLine() {
		assertEquals("a; ~~~~~~\nb;", code("a; // x {\nb;"));
	}

	@Test
	public void blockCommentsSpanLines() {
		assertEquals("a ~~~~\n~~~~~ b", code("a /* {\n } */ b"));
	}

	@Test
	public void textBlocksSpanLines() {
		assertEquals("s = ~~~\n~~~\n~~~;", code("s = \"\"\"\n{ x\n\"\"\";"));
	}

	@Test
	public void startStatesAndLineCommentsAreRecordedPerLine() {
		CodeMask mask = CodeMask.of(Arrays.asList("a /* x", "y */ b // c", "\"\"\"", "t", "\"\"\""));
		assertEquals(CodeMask.NORMAL, mask.start[0]);
		assertEquals(CodeMask.BLOCK_COMMENT, mask.start[1]);
		assertEquals(CodeMask.NORMAL, mask.start[2]);
		assertEquals(CodeMask.TEXT_BLOCK, mask.start[3]);
		assertFalse(mask.lineComment[0]);
		assertTrue(mask.lineComment[1]);
	}
}
