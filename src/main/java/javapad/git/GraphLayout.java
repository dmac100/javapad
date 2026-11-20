package javapad.git;

import java.util.ArrayList;
import java.util.List;

// Assigns each commit of a log (newest first, children before parents) a lane and works out the lines that connect it to its parents.
// Row i is drawn in a cell: lines run from the top edge to the dot, and from the dot to the bottom edge.
public class GraphLayout {
	public static class Row {
		public int lane;
		// lanes that pass straight through the cell
		public final List<Integer> through = new ArrayList<Integer>();
		// lanes (on the top edge) that end in this commit's dot
		public final List<Integer> from = new ArrayList<Integer>();
		// lanes (on the bottom edge) that start at this commit's dot, one per parent
		public final List<Integer> to = new ArrayList<Integer>();
	}

	public static List<Row> layout(List<Commit> commits) {
		List<Row> rows = new ArrayList<Row>();
		// lanes.get(x) is the hash the line in lane x is heading for, or null
		List<String> lanes = new ArrayList<String>();
		for(Commit c : commits) {
			Row row = new Row();
			int lane = -1;
			for(int x = 0; x < lanes.size(); x++) {
				if(c.hash.equals(lanes.get(x))) {
					row.from.add(x);
					if(lane < 0) {
						lane = x;
					}
				} else if(lanes.get(x) != null) {
					row.through.add(x);
				}
			}
			if(lane < 0) {
				lane = free(lanes);
			}
			row.lane = lane;
			for(int x : row.from) {
				lanes.set(x, null);
			}
			for(int p = 0; p < c.parents.size(); p++) {
				String parent = c.parents.get(p);
				int target = lanes.indexOf(parent);
				if(target < 0) {
					target = p == 0 ? lane : free(lanes);
					lanes.set(target, parent);
				}
				row.to.add(target);
			}
			// trailing empty lanes are dropped so the graph stays narrow
			while(!lanes.isEmpty() && lanes.get(lanes.size() - 1) == null) {
				lanes.remove(lanes.size() - 1);
			}
			rows.add(row);
		}
		return rows;
	}

	// the first empty lane, adding one if there is none; the lane holds a placeholder until the caller sets it
	static int free(List<String> lanes) {
		for(int x = 0; x < lanes.size(); x++) {
			if(lanes.get(x) == null) {
				return x;
			}
		}
		lanes.add(null);
		return lanes.size() - 1;
	}

	public static int width(List<Row> rows) {
		int max = 0;
		for(Row r : rows) {
			max = Math.max(max, r.lane);
			for(int x : r.through) {
				max = Math.max(max, x);
			}
			for(int x : r.from) {
				max = Math.max(max, x);
			}
			for(int x : r.to) {
				max = Math.max(max, x);
			}
		}
		return max + 1;
	}
}
