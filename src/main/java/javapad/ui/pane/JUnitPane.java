package javapad.ui.pane;

import javapad.ui.FileUtil;
import javapad.ui.Icons;
import javapad.ui.Theme;
import javapad.ui.editor.EditorTabs;
import javapad.ui.editor.Location;
import javapad.ui.editor.ReadOnlyText;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javapad.model.EclipseProject;
import javapad.run.TestFinder;
import javapad.run.TestResult;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

public class JUnitPane {
	static final Pattern FRAME = Pattern.compile("at ([\\w.$]+)\\.[\\w$<>]+\\((\\w+\\.java):(\\d+)\\)");

	private SashForm sash;
	private final Composite composite;
	private final ToolBar toolbar;
	private final ToolItem rerunAll;
	private final ToolItem rerunFailed;
	private final ToolItem stop;
	private Runnable rerunAllHandler;
	private Runnable rerunFailedHandler;
	private Runnable stopHandler;
	private BooleanSupplier running = () -> false;
	private BooleanSupplier hasRun = () -> false;
	private final Label summary;
	private final Canvas bar;
	private int progressDone;
	private int progressTotal;
	private int progressFailed;
	private final Tree tree;
	private final StyledText detail;
	private final Theme theme;
	private EclipseProject project;
	private Consumer<Location> opener;
	private List<TestResult> results;
	private Consumer<List<String>> runner;
	private final Map<String, TreeItem> liveClasses = new LinkedHashMap<String, TreeItem>();
	private final Map<String, TreeItem> liveTests = new LinkedHashMap<String, TreeItem>();
	private final ArrayList<TestResult> liveResults = new ArrayList<TestResult>();
	// per test class during a run: tests known, tests finished, tests that failed
	private final Map<String, int[]> liveCounts = new HashMap<String, int[]>();

	public JUnitPane(Composite parent, Theme theme) {
		this.theme = theme;
		composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 4;
		layout.marginHeight = 2;
		layout.verticalSpacing = 3;
		composite.setLayout(layout);
		toolbar = new ToolBar(composite, SWT.FLAT);
		toolbar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		rerunAll = tool("Re-run All Tests", Icons.RERUN_ALL, () -> run(rerunAllHandler));
		rerunFailed = tool("Re-run Failed Tests", Icons.RERUN_FAILED, () -> run(rerunFailedHandler));
		stop = tool("Stop Tests", Icons.STOP, () -> run(stopHandler));
		summary = new Label(composite, SWT.NONE);
		summary.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		bar = new Canvas(composite, SWT.NO_BACKGROUND);
		GridData barData = new GridData(SWT.FILL, SWT.TOP, true, false);
		barData.heightHint = 12;
		bar.setLayoutData(barData);
		bar.addListener(SWT.Paint, e -> paintBar(e.gc));
		sash = new SashForm(composite, SWT.VERTICAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tree = new Tree(sash, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
		detail = new StyledText(sash, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
		detail.setFont(theme.font);
		ReadOnlyText.installKeys(detail);
		sash.setWeights(new int[] {85, 15});
		tree.addListener(SWT.Selection, e -> showDetail());
		detail.addListener(SWT.MouseUp, e -> clicked(e.x, e.y));
		detail.addListener(SWT.MouseMove, e -> hover(e.x, e.y));
		tree.addListener(SWT.DefaultSelection, e -> {
			TreeItem[] sel = tree.getSelection();
			if(sel.length > 0 && sel[0].getItemCount() > 0) {
				sel[0].setExpanded(!sel[0].getExpanded());
			} else {
				openSelected();
			}
		});
		Menu menu = new Menu(tree);
		MenuItem runSelected = new MenuItem(menu, SWT.PUSH);
		runSelected.setText("Run Selected");
		runSelected.addListener(SWT.Selection, e -> run(selectedSpecs()));
		MenuItem runFailed = new MenuItem(menu, SWT.PUSH);
		runFailed.setText("Re-run Failed Tests");
		runFailed.addListener(SWT.Selection, e -> run(rerunFailedHandler));
		menu.addListener(SWT.Show, e -> {
			runSelected.setEnabled(!selectedSpecs().isEmpty());
			runFailed.setEnabled(!failedSpecs().isEmpty() && !running.getAsBoolean());
		});
		tree.setMenu(menu);
		clear();
	}

	ToolItem tool(String tip, int icon, Runnable action) {
		ToolItem item = new ToolItem(toolbar, SWT.PUSH);
		Image image = Icons.create(toolbar.getDisplay(), icon);
		item.setImage(image);
		item.setToolTipText(tip);
		item.addListener(SWT.Selection, e -> action.run());
		item.addListener(SWT.Dispose, e -> image.dispose());
		return item;
	}

	void run(Runnable handler) {
		if(handler != null) {
			handler.run();
		}
	}

	// The toolbar's actions, and what tells whether tests are running and whether there is a run to repeat.
	public void setToolbar(Runnable rerunAll, Runnable rerunFailed, Runnable stop, BooleanSupplier running, BooleanSupplier hasRun) {
		this.rerunAllHandler = rerunAll;
		this.rerunFailedHandler = rerunFailed;
		this.stopHandler = stop;
		this.running = running;
		this.hasRun = hasRun;
		updateToolbar();
	}

	// Enables the buttons that make sense now: stop while tests run, the reruns when they do not.
	public void updateToolbar() {
		if(toolbar.isDisposed()) {
			return;
		}
		boolean busy = running.getAsBoolean();
		rerunAll.setEnabled(!busy && hasRun.getAsBoolean());
		rerunFailed.setEnabled(!busy && !failedSpecs().isEmpty());
		stop.setEnabled(busy);
	}

	public void setRunner(Consumer<List<String>> runner) {
		this.runner = runner;
	}

	void run(List<String> specs) {
		if(runner != null && !specs.isEmpty()) {
			runner.accept(specs);
		}
	}

	static String spec(String className, String name) {
		String method = TestFinder.selectableMethod(name);
		return method == null ? className : className + "#" + method;
	}

	// class or class#method for each selected tree row (a class row means the whole class)
	public List<String> selectedSpecs() {
		LinkedHashSet<String> specs = new LinkedHashSet<String>();
		for(TreeItem item : tree.getSelection()) {
			if(item.getData() instanceof TestResult) {
				TestResult r = (TestResult)item.getData();
				specs.add(spec(r.className, r.name));
			} else if(item.getData("class") != null) {
				specs.add((String)item.getData("class"));
			}
		}
		return new ArrayList<String>(specs);
	}

	public List<String> failedSpecs() {
		LinkedHashSet<String> specs = new LinkedHashSet<String>();
		if(results != null) {
			for(TestResult r : results) {
				if(r.bad()) {
					specs.add(spec(r.className, r.name));
				}
			}
		}
		return new ArrayList<String>(specs);
	}

	// Called at the start of a run: results then arrive one at a time through liveEvent.
	public void beginLive() {
		clear();
		liveClasses.clear();
		liveTests.clear();
		liveResults.clear();
		liveCounts.clear();
		summary.setText("Running tests...");
	}

	void paintBar(GC gc) {
		Rectangle r = bar.getClientArea();
		gc.setBackground(theme.background);
		gc.fillRectangle(r);
		if(progressTotal > 0) {
			gc.setBackground(progressFailed > 0 ? theme.error : theme.pass);
			gc.fillRectangle(0, 0, (int)((long)r.width * Math.min(progressDone, progressTotal) / progressTotal), r.height);
		}
		gc.setForeground(theme.gutter);
		gc.drawRectangle(0, 0, r.width - 1, r.height - 1);
	}

	void setProgress(int done, int total, int failed) {
		progressDone = done;
		progressTotal = total;
		progressFailed = failed;
		bar.redraw();
	}

	// the row of a test class in the tree, made when it is first seen
	TreeItem classItem(String cls) {
		TreeItem classItem = liveClasses.get(cls);
		if(classItem == null) {
			classItem = new TreeItem(tree, SWT.NONE);
			classItem.setData("class", cls);
			liveClasses.put(cls, classItem);
			liveCounts.put(cls, new int[3]);
		}
		return classItem;
	}

	void updateClass(String cls) {
		TreeItem classItem = liveClasses.get(cls);
		int[] n = liveCounts.get(cls);
		boolean finished = n[1] == n[0];
		classItem.setText((n[2] > 0 ? "✘ " : finished ? "✔ " : "▶ ") + cls + "  (" + n[1] + "/" + n[0] + ")");
		classItem.setForeground(n[2] > 0 ? theme.errorText : finished ? theme.pass : theme.foreground);
	}

	// PLAN class name (a test that will run) | RUN class name | END class name status seconds message   (status P, F, E or K)
	public void liveEvent(String[] f) {
		if(f.length < 3) {
			return;
		}
		boolean end = f[0].equals("END");
		boolean plan = f[0].equals("PLAN");
		if(!end && !plan && !f[0].equals("RUN")) {
			return;
		}
		String cls = f[1].isEmpty() ? "(tests)" : f[1];
		TreeItem classItem = classItem(cls);
		int[] counts = liveCounts.get(cls);
		String key = cls + "#" + f[2];
		TreeItem item = liveTests.get(key);
		if(item == null) {
			item = new TreeItem(classItem, SWT.NONE);
			liveTests.put(key, item);
			classItem.setExpanded(true);
			counts[0]++;
			item.setText("○ " + f[2]);
			item.setForeground(theme.gutter);
			updateClass(cls);
		}
		if(plan) {
			setProgress(liveResults.size(), liveTests.size(), failures(liveResults));
			summary.setText("Running...  Tests: " + liveTests.size());
			return;
		}
		if(!end) {
			item.setText("▶ " + f[2]);
			item.setForeground(theme.foreground);
			if(tree.getSelectionCount() == 0) {
				tree.showItem(item);
			}
			return;
		}
		int status = f.length < 4 ? TestResult.PASSED : f[3].equals("F") ? TestResult.FAILED : f[3].equals("E") ? TestResult.ERROR
			: f[3].equals("K") ? TestResult.SKIPPED : TestResult.PASSED;
		double seconds = 0;
		try {
			seconds = f.length > 4 ? Double.parseDouble(f[4]) : 0;
		} catch(NumberFormatException e) {
			// a malformed time stays 0
		}
		TestResult r = new TestResult(cls, f[2], seconds, status, f.length > 5 ? f[5] : "", "");
		if(item.getData() instanceof TestResult) {
			// a second end for the same name (a parameterized test, say) is counted as one more test
			counts[0]++;
		}
		counts[1]++;
		counts[2] += r.bad() ? 1 : 0;
		liveResults.add(r);
		item.setData(r);
		item.setText(marker(r) + r.name + "  " + String.format("%.3f s", r.seconds));
		item.setForeground(r.bad() ? theme.errorText : r.status == TestResult.SKIPPED ? theme.gutter : theme.pass);
		updateClass(cls);
		int failed = failures(liveResults);
		results = new ArrayList<TestResult>(liveResults);
		int total = Math.max(liveTests.size(), liveResults.size());
		summary.setText("Running...  Run: " + liveResults.size() + "/" + total + "    Failures: " + failed);
		setProgress(liveResults.size(), total, failed);
		if(r.bad() && tree.getSelectionCount() == 0) {
			tree.setSelection(item);
			showDetail();
		}
	}

	static int failures(List<TestResult> list) {
		int n = 0;
		for(TestResult t : list) {
			n += t.bad() ? 1 : 0;
		}
		return n;
	}

	public Composite getControl() {
		return composite;
	}

	public void setProject(EclipseProject project) {
		this.project = project;
	}

	public void setOpener(Consumer<Location> opener) {
		this.opener = opener;
	}

	public void restyle() {
		detail.setFont(theme.font);
		composite.setBackground(theme.chrome);
		EditorTabs.styleSash(sash);
		toolbar.setBackground(theme.chrome);
		summary.setBackground(theme.chrome);
		summary.setForeground(theme.foreground);
		tree.setBackground(theme.background);
		tree.setForeground(theme.foreground);
		detail.setBackground(theme.background);
		detail.setForeground(theme.foreground);
		if(results != null) {
			setResults(results);
		} else {
			bar.redraw();
		}
	}

	public void clear() {
		results = null;
		tree.removeAll();
		detail.setText("");
		summary.setText("No test results yet. Run tests to see them here.");
		setProgress(0, 0, 0);
		updateToolbar();
	}

	public int failures() {
		int n = 0;
		if(results != null) {
			for(TestResult r : results) {
				if(r.bad()) {
					n++;
				}
			}
		}
		return n;
	}

	public int total() {
		return results == null ? 0 : results.size();
	}

	public void setResults(List<TestResult> list) {
		results = list;
		tree.removeAll();
		detail.setText("");
		Map<String, List<TestResult>> byClass = new LinkedHashMap<String, List<TestResult>>();
		int failed = 0;
		int skipped = 0;
		double seconds = 0;
		for(TestResult r : list) {
			byClass.computeIfAbsent(r.className, k -> new ArrayList<TestResult>()).add(r);
			failed += r.bad() ? 1 : 0;
			skipped += r.status == TestResult.SKIPPED ? 1 : 0;
			seconds += r.seconds;
		}
		boolean expandAll = list.size() <= 40;
		TreeItem firstBad = null;
		for(Map.Entry<String, List<TestResult>> entry : byClass.entrySet()) {
			int bad = 0;
			for(TestResult r : entry.getValue()) {
				bad += r.bad() ? 1 : 0;
			}
			TreeItem cls = new TreeItem(tree, SWT.NONE);
			cls.setText((bad > 0 ? "✘ " : "✔ ") + entry.getKey() + "  (" + entry.getValue().size() + ")");
			cls.setForeground(bad > 0 ? theme.errorText : theme.pass);
			cls.setData("class", entry.getKey());
			for(TestResult r : entry.getValue()) {
				TreeItem item = new TreeItem(cls, SWT.NONE);
				item.setData(r);
				item.setText(marker(r) + r.name + "  " + String.format("%.3f s", r.seconds));
				item.setForeground(r.bad() ? theme.errorText : r.status == TestResult.SKIPPED ? theme.gutter : theme.pass);
				if(r.bad() && firstBad == null) {
					firstBad = item;
				}
			}
			cls.setExpanded(expandAll || bad > 0);
		}
		summary.setText("Runs: " + (list.size() - skipped) + "/" + list.size() + "    Failures: " + failed + "    Skipped: " + skipped
			+ "    Time: " + String.format("%.2f s", seconds));
		setProgress(list.size(), list.size(), failed);
		if(firstBad != null) {
			tree.setSelection(firstBad);
			showDetail();
		}
		composite.layout(true, true);
	}

	static String marker(TestResult r) {
		if(r.status == TestResult.PASSED) {
			return "✔ ";
		}
		if(r.status == TestResult.SKIPPED) {
			return "– ";
		}
		return "✘ ";
	}

	TestResult selected() {
		TreeItem[] sel = tree.getSelection();
		if(sel.length == 0 || !(sel[0].getData() instanceof TestResult)) {
			return null;
		}
		return (TestResult)sel[0].getData();
	}

	void showDetail() {
		TestResult r = selected();
		if(r == null) {
			detail.setText("");
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(r.className).append('.').append(r.name).append('\n');
		if(!r.message.isEmpty()) {
			sb.append('\n').append(r.message).append('\n');
		}
		if(!r.trace.isEmpty()) {
			sb.append('\n').append(r.trace).append('\n');
		}
		detail.setText(sb.toString());
		linkify();
	}

	void linkify() {
		if(project == null) {
			return;
		}
		String text = detail.getText();
		Matcher m = FRAME.matcher(text);
		while(m.find()) {
			File f = project.findSource(m.group(1), m.group(2));
			if(f == null) {
				continue;
			}
			StyleRange link = new StyleRange();
			link.start = m.start() + 3;
			link.length = m.end() - m.start() - 3;
			link.foreground = theme.keyword;
			link.underline = true;
			link.underlineStyle = SWT.UNDERLINE_LINK;
			link.data = new Location(f, Integer.parseInt(m.group(3)), 1);
			detail.setStyleRange(link);
		}
	}

	Location linkAt(int x, int y) {
		try {
			int offset = detail.getOffsetAtPoint(new Point(x, y));
			StyleRange r = detail.getStyleRangeAtOffset(offset);
			if(r != null && r.data instanceof Location) {
				return (Location)r.data;
			}
		} catch(IllegalArgumentException e) {
			// the click was not over any row
		}
		return null;
	}

	void clicked(int x, int y) {
		Location l = linkAt(x, y);
		if(l != null && opener != null && detail.getSelectionCount() == 0) {
			opener.accept(l);
		}
	}

	void hover(int x, int y) {
		int cursor = linkAt(x, y) != null ? SWT.CURSOR_HAND : SWT.CURSOR_IBEAM;
		detail.setCursor(detail.getDisplay().getSystemCursor(cursor));
	}

	void openSelected() {
		TestResult r = selected();
		if(r == null || project == null || opener == null) {
			return;
		}
		String method = TestFinder.selectableMethod(r.name);
		File declaring = method == null ? null : project.sourceFor(r.className);
		if(declaring != null) {
			int line = TestFinder.methodLine(FileUtil.contentOf(declaring), method);
			if(line > 0) {
				opener.accept(new Location(declaring, line, 1));
				return;
			}
		}
		File first = null;
		int firstLine = 1;
		Matcher m = FRAME.matcher(r.trace);
		while(m.find()) {
			File f = project.findSource(m.group(1), m.group(2));
			if(f == null) {
				continue;
			}
			int line = Integer.parseInt(m.group(3));
			String cls = m.group(1);
			int inner = cls.indexOf('$');
			if((inner < 0 ? cls : cls.substring(0, inner)).equals(r.className)) {
				opener.accept(new Location(f, line, 1));
				return;
			}
			if(first == null) {
				first = f;
				firstLine = line;
			}
		}
		if(first != null) {
			opener.accept(new Location(first, firstLine, 1));
			return;
		}
		File src = project.sourceFor(r.className);
		if(src != null) {
			opener.accept(new Location(src, 1, 1));
		}
	}
}
