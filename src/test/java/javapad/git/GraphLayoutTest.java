package javapad.git;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class GraphLayoutTest {
	static Commit c(String hash, String... parents) {
		return new Commit(hash, Arrays.asList(parents), "", "", "", "", "", "", new ArrayList<Commit.Ref>(), hash, "");
	}

	@Test
	public void aStraightLineStaysInLaneZero() {
		List<GraphLayout.Row> rows = GraphLayout.layout(Arrays.asList(c("c", "b"), c("b", "a"), c("a")));
		assertEquals(1, GraphLayout.width(rows));
		assertEquals(Arrays.asList(0), rows.get(0).to);
		assertEquals(Arrays.asList(0), rows.get(1).from);
		assertEquals(0, rows.get(2).to.size());
	}

	@Test
	public void aBranchAndMergeUseTwoLanes() {
		// m merges f into main; both come from base
		List<GraphLayout.Row> rows = GraphLayout.layout(Arrays.asList(c("m", "a", "f"), c("f", "base"), c("a", "base"), c("base")));
		assertEquals(Arrays.asList(0, 1), rows.get(0).to);
		assertEquals(1, rows.get(1).lane);
		assertEquals(Arrays.asList(0), rows.get(1).through);
		assertEquals(0, rows.get(2).lane);
		assertEquals(Arrays.asList(1), rows.get(2).through);
		// the line of a moves over to base's lane
		assertEquals(Arrays.asList(1), rows.get(2).to);
		assertEquals(1, rows.get(3).lane);
		assertEquals(Arrays.asList(1), rows.get(3).from);
		assertEquals(2, GraphLayout.width(rows));
	}

	@Test
	public void twoTipsStartInSeparateLanes() {
		List<GraphLayout.Row> rows = GraphLayout.layout(Arrays.asList(c("x", "root"), c("y", "root"), c("root")));
		assertEquals(0, rows.get(0).lane);
		assertEquals(1, rows.get(1).lane);
		assertEquals(Arrays.asList(0), rows.get(1).through);
		// y joins the line that already leads to root
		assertEquals(Arrays.asList(0), rows.get(1).to);
		assertEquals(Arrays.asList(0), rows.get(2).from);
	}
}
