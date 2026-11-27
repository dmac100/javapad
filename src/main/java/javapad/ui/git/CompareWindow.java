package javapad.ui.git;

import javapad.ui.MainWindow;
import javapad.ui.Theme;
import javapad.ui.dialog.ConfirmDialog;
import javapad.ui.editor.EditorTabs;
import javapad.ui.editor.ReadOnlyText;

import java.util.List;
import javapad.git.LineDiff;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

// Two versions of a file side by side, lined up and scrolling together. With several files, a list on the left chooses the file.
public class CompareWindow {
	private final MainWindow w;
	private final List<Entry> entries;
	private final Source source;
	private final Theme theme;
	private final Shell shell;
	private final StyledText left;
	private final StyledText right;
	private boolean syncing;
	private int generation;

	static class Entry {
		final String path;
		final String state;
		// where the file was before, for a rename; otherwise the same as path
		final String oldPath;

		Entry(String path, String state, String oldPath) {
			this.path = path;
			this.state = state;
			this.oldPath = oldPath == null ? path : oldPath;
		}
	}

	interface Source {
		// Reads both versions of the file: {left, right}. Called on a worker thread.
		String[] versions(Entry entry);
	}

	// parent: the window this one belongs to (a dialog, when it is opened from a dialog)
	static void open(MainWindow w, Shell parent, String title, String leftTitle, String rightTitle, List<Entry> entries, Source source) {
		if(entries.isEmpty()) {
			ConfirmDialog.message(parent, title, "There are no differences.", SWT.ICON_INFORMATION);
			return;
		}
		new CompareWindow(w, parent, title, leftTitle, rightTitle, entries, source);
	}

	CompareWindow(MainWindow w, Shell parent, String title, String leftTitle, String rightTitle, List<Entry> entries, Source source) {
		this.w = w;
		this.entries = entries;
		this.source = source;
		this.theme = w.theme;
		shell = new Shell(parent, SWT.SHELL_TRIM);
		shell.setText(title);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		shell.setLayout(layout);
		shell.setBackground(theme.chrome);
		SashForm outer = new SashForm(shell, SWT.HORIZONTAL);
		outer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		EditorTabs.styleSash(outer);
		Table list = null;
		if(entries.size() > 1) {
			list = new Table(outer, SWT.FULL_SELECTION | SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
			list.setHeaderVisible(true);
			String[] titles = {"File", "State"};
			int[] widths = {260, 80};
			for(int i = 0; i < titles.length; i++) {
				TableColumn c = new TableColumn(list, SWT.NONE);
				c.setText(titles[i]);
				c.setWidth(widths[i]);
			}
			list.setBackground(theme.background);
			list.setForeground(theme.foreground);
			list.setHeaderBackground(theme.chrome);
			list.setHeaderForeground(theme.foreground);
			for(Entry e : entries) {
				new TableItem(list, SWT.NONE).setText(new String[] {e.path, e.state});
			}
		}
		Composite sides = new Composite(outer, SWT.NONE);
		GridLayout sl = new GridLayout(2, true);
		sl.marginWidth = 0;
		sl.marginHeight = 0;
		sl.horizontalSpacing = 2;
		sl.verticalSpacing = 0;
		sides.setLayout(sl);
		sides.setBackground(theme.chrome);
		header(sides, leftTitle);
		header(sides, rightTitle);
		left = text(sides);
		right = text(sides);
		left.addListener(SWT.Paint, e -> follow(left, right));
		right.addListener(SWT.Paint, e -> follow(right, left));
		if(list != null) {
			outer.setWeights(new int[] {20, 80});
			Table chooser = list;
			chooser.addListener(SWT.Selection, e -> load(chooser.getSelectionIndex()));
		}
		Rectangle b = parent.getBounds();
		shell.setSize(Math.min(1300, Math.max(700, b.width - 40)), Math.min(760, Math.max(400, b.height - 60)));
		Point size = shell.getSize();
		shell.setLocation(b.x + (b.width - size.x) / 2, b.y + (b.height - size.y) / 2);
		shell.open();
		if(list != null) {
			list.setSelection(0);
		}
		load(0);
	}

	void header(Composite parent, String text) {
		Label l = new Label(parent, SWT.NONE);
		l.setText(text);
		l.setBackground(theme.chrome);
		l.setForeground(theme.foreground);
		l.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
	}

	StyledText text(Composite parent) {
		StyledText t = new StyledText(parent, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
		t.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		t.setFont(theme.font);
		t.setBackground(theme.background);
		t.setForeground(theme.foreground);
		ReadOnlyText.installKeys(t);
		return t;
	}

	// keeps the other side at the same scroll position
	void follow(StyledText from, StyledText to) {
		if(syncing) {
			return;
		}
		if(to.getTopPixel() != from.getTopPixel() || to.getHorizontalPixel() != from.getHorizontalPixel()) {
			syncing = true;
			to.setTopPixel(from.getTopPixel());
			to.setHorizontalPixel(from.getHorizontalPixel());
			syncing = false;
		}
	}

	void load(int index) {
		if(index < 0 || index >= entries.size()) {
			return;
		}
		Entry entry = entries.get(index);
		int mine = ++generation;
		left.setText("Loading...");
		right.setText("");
		w.git.background(() -> {
			String[] v = source.versions(entry);
			return () -> {
				if(mine == generation && !shell.isDisposed()) {
					show(v[0], v[1]);
				}
			};
		});
	}

	void show(String before, String after) {
		List<LineDiff.Row> rows = LineDiff.diff(before, after);
		StringBuilder l = new StringBuilder();
		StringBuilder r = new StringBuilder();
		for(int i = 0; i < rows.size(); i++) {
			l.append(rows.get(i).left == null ? "" : rows.get(i).left);
			r.append(rows.get(i).right == null ? "" : rows.get(i).right);
			if(i + 1 < rows.size()) {
				l.append('\n');
				r.append('\n');
			}
		}
		left.setText(l.toString());
		right.setText(r.toString());
		int first = -1;
		for(int i = 0; i < rows.size(); i++) {
			LineDiff.Row row = rows.get(i);
			if(row.kind == LineDiff.SAME) {
				continue;
			}
			first = first < 0 ? i : first;
			Color removed = theme.diffRemoved;
			Color added = theme.diffAdded;
			if(row.kind == LineDiff.CHANGED || row.kind == LineDiff.REMOVED) {
				left.setLineBackground(i, 1, removed);
			}
			if(row.kind == LineDiff.CHANGED || row.kind == LineDiff.ADDED) {
				right.setLineBackground(i, 1, added);
			}
		}
		int top = Math.max(0, first - 3);
		left.setTopIndex(top);
		right.setTopIndex(top);
	}
}
