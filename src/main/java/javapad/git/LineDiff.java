package javapad.git;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// A line diff of two texts as aligned rows for a side-by-side view: every row has a line on the left, on the right, or both.
public class LineDiff {
	public static final int SAME = 0;
	public static final int CHANGED = 1;
	public static final int ADDED = 2;
	public static final int REMOVED = 3;

	// beyond this many line pairs the middle of the files is shown as one removed and one added block
	static final long LIMIT = 6000000L;

	public static class Row {
		public final int kind;
		// null on the side that has no line in an ADDED or REMOVED row
		public final String left;
		public final String right;

		Row(int kind, String left, String right) {
			this.kind = kind;
			this.left = left;
			this.right = right;
		}
	}

	public static List<String> lines(String text) {
		if(text.isEmpty()) {
			return new ArrayList<String>();
		}
		List<String> list = new ArrayList<String>(Arrays.asList(text.replace("\r\n", "\n").split("\n", -1)));
		if(text.endsWith("\n")) {
			list.remove(list.size() - 1);
		}
		return list;
	}

	public static List<Row> diff(String before, String after) {
		List<String> a = lines(before);
		List<String> b = lines(after);
		int prefix = 0;
		while(prefix < a.size() && prefix < b.size() && a.get(prefix).equals(b.get(prefix))) {
			prefix++;
		}
		int suffix = 0;
		while(suffix < a.size() - prefix && suffix < b.size() - prefix && a.get(a.size() - 1 - suffix).equals(b.get(b.size() - 1 - suffix))) {
			suffix++;
		}
		List<Row> rows = new ArrayList<Row>();
		for(int i = 0; i < prefix; i++) {
			rows.add(new Row(SAME, a.get(i), b.get(i)));
		}
		List<String> ma = a.subList(prefix, a.size() - suffix);
		List<String> mb = b.subList(prefix, b.size() - suffix);
		middle(ma, mb, rows);
		for(int i = suffix; i > 0; i--) {
			rows.add(new Row(SAME, a.get(a.size() - i), b.get(b.size() - i)));
		}
		return rows;
	}

	// The 0-based lines of the new text that were added or changed.
	public static Set<Integer> changedLines(String before, String after) {
		Set<Integer> lines = new HashSet<Integer>();
		int line = 0;
		for(Row row : diff(before, after)) {
			if(row.kind == REMOVED) {
				continue;
			}
			if(row.kind != SAME) {
				lines.add(line);
			}
			line++;
		}
		return lines;
	}

	static void middle(List<String> a, List<String> b, List<Row> out) {
		int n = a.size();
		int m = b.size();
		if(n == 0 && m == 0) {
			return;
		}
		if((long)n * m > LIMIT) {
			block(a, b, out);
			return;
		}
		int[][] len = new int[n + 1][m + 1];
		for(int i = n - 1; i >= 0; i--) {
			for(int j = m - 1; j >= 0; j--) {
				len[i][j] = a.get(i).equals(b.get(j)) ? len[i + 1][j + 1] + 1 : Math.max(len[i + 1][j], len[i][j + 1]);
			}
		}
		List<String> removed = new ArrayList<String>();
		List<String> added = new ArrayList<String>();
		int i = 0;
		int j = 0;
		while(i < n || j < m) {
			if(i < n && j < m && a.get(i).equals(b.get(j))) {
				block(removed, added, out);
				removed.clear();
				added.clear();
				out.add(new Row(SAME, a.get(i), b.get(j)));
				i++;
				j++;
			} else if(j >= m || (i < n && len[i + 1][j] >= len[i][j + 1])) {
				removed.add(a.get(i));
				i++;
			} else {
				added.add(b.get(j));
				j++;
			}
		}
		block(removed, added, out);
	}

	// a run of removed and added lines: the first lines of both sides pair up as changed, the rest stand alone
	static void block(List<String> removed, List<String> added, List<Row> out) {
		int pairs = Math.min(removed.size(), added.size());
		for(int k = 0; k < pairs; k++) {
			out.add(new Row(CHANGED, removed.get(k), added.get(k)));
		}
		for(int k = pairs; k < removed.size(); k++) {
			out.add(new Row(REMOVED, removed.get(k), null));
		}
		for(int k = pairs; k < added.size(); k++) {
			out.add(new Row(ADDED, null, added.get(k)));
		}
	}
}
