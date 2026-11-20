package javapad.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;

public class LineDiffTest {
	static String kinds(List<LineDiff.Row> rows) {
		StringBuilder sb = new StringBuilder();
		for(LineDiff.Row r : rows) {
			sb.append("=~+-".charAt(r.kind));
		}
		return sb.toString();
	}

	@Test
	public void identicalTextsAreAllSame() {
		assertEquals("===", kinds(LineDiff.diff("a\nb\nc\n", "a\nb\nc\n")));
	}

	@Test
	public void aChangedLinePairsUp() {
		List<LineDiff.Row> rows = LineDiff.diff("a\nb\nc\n", "a\nB\nc\n");
		assertEquals("=~=", kinds(rows));
		assertEquals("b", rows.get(1).left);
		assertEquals("B", rows.get(1).right);
	}

	@Test
	public void insertionsAndDeletionsKeepTheRestAligned() {
		List<LineDiff.Row> rows = LineDiff.diff("a\nc\nd\n", "a\nb\nc\n");
		assertEquals("=+=-", kinds(rows));
		assertNull(rows.get(1).left);
		assertEquals("b", rows.get(1).right);
		assertEquals("d", rows.get(3).left);
		assertNull(rows.get(3).right);
	}

	@Test
	public void longerBlocksPairTheirFirstLines() {
		assertEquals("=~~+=", kinds(LineDiff.diff("x\n1\n2\ny\n", "x\nA\nB\nC\ny\n")));
	}

	@Test
	public void anEmptySideIsAllAddedOrRemoved() {
		assertEquals("++", kinds(LineDiff.diff("", "a\nb\n")));
		assertEquals("--", kinds(LineDiff.diff("a\nb", "")));
		assertEquals("", kinds(LineDiff.diff("", "")));
	}

	@Test
	public void aMissingFinalNewlineIsNotALine() {
		assertEquals("==", kinds(LineDiff.diff("a\nb", "a\nb\n")));
		assertEquals("=~", kinds(LineDiff.diff("a\r\nb\r\n", "a\nc\n")));
	}

	@Test
	public void changedLinesAreThoseAddedOrChangedInTheNewText() {
		assertEquals(new java.util.HashSet<Integer>(java.util.Arrays.asList(1, 3, 4)), LineDiff.changedLines("a\nb\nc\nd\n", "a\nB\nc\nx\ny\n"));
		assertEquals(new java.util.HashSet<Integer>(), LineDiff.changedLines("a\nb\n", "a\nb\n"));
		assertEquals(new java.util.HashSet<Integer>(), LineDiff.changedLines("a\nb\n", "a\n"));
	}
}
