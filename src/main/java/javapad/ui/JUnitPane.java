package javapad.ui;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javapad.model.EclipseProject;
import javapad.run.TestResult;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

public class JUnitPane
{
	static final Pattern FRAME = Pattern.compile("at ([\\w.$]+)\\.[\\w$<>]+\\((\\w+\\.java):(\\d+)\\)");

	private final Composite composite;
	private final Label summary;
	private final Composite bar;
	private final Tree tree;
	private final StyledText detail;
	private final Theme theme;
	private EclipseProject project;
	private Consumer<Location> opener;
	private List<TestResult> results;

	public JUnitPane(Composite parent, Theme theme)
	{
		this.theme = theme;
		composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 4;
		layout.marginHeight = 2;
		layout.verticalSpacing = 3;
		composite.setLayout(layout);
		summary = new Label(composite, SWT.NONE);
		summary.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		bar = new Composite(composite, SWT.NONE);
		GridData barData = new GridData(SWT.FILL, SWT.TOP, true, false);
		barData.heightHint = 6;
		bar.setLayoutData(barData);
		SashForm sash = new SashForm(composite, SWT.HORIZONTAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tree = new Tree(sash, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
		detail = new StyledText(sash, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
		detail.setFont(theme.font);
		sash.setWeights(new int[] {45, 55});
		tree.addListener(SWT.Selection, e -> showDetail());
		detail.addListener(SWT.MouseUp, e -> clicked(e.x, e.y));
		detail.addListener(SWT.MouseMove, e -> hover(e.x, e.y));
		tree.addListener(SWT.DefaultSelection, e -> openSelected());
		clear();
	}

	public Composite getControl()
	{
		return composite;
	}

	public void setProject(EclipseProject project)
	{
		this.project = project;
	}

	public void setOpener(Consumer<Location> opener)
	{
		this.opener = opener;
	}

	public void restyle()
	{
		composite.setBackground(theme.chrome);
		summary.setBackground(theme.chrome);
		summary.setForeground(theme.foreground);
		tree.setBackground(theme.background);
		tree.setForeground(theme.foreground);
		detail.setBackground(theme.background);
		detail.setForeground(theme.foreground);
		if(results != null)
		{
			setResults(results);
		}
		else
		{
			bar.setBackground(theme.chrome);
		}
	}

	public void clear()
	{
		results = null;
		tree.removeAll();
		detail.setText("");
		summary.setText("No test results yet. Run tests to see them here.");
		bar.setBackground(theme.chrome);
	}

	public int failures()
	{
		int n = 0;
		if(results != null)
		{
			for(TestResult r : results)
			{
				if(r.bad())
				{
					n++;
				}
			}
		}
		return n;
	}

	public int total()
	{
		return results == null ? 0 : results.size();
	}

	public void setResults(List<TestResult> list)
	{
		results = list;
		tree.removeAll();
		detail.setText("");
		Map<String, java.util.List<TestResult>> byClass = new LinkedHashMap<String, java.util.List<TestResult>>();
		int failed = 0;
		int skipped = 0;
		double seconds = 0;
		for(TestResult r : list)
		{
			byClass.computeIfAbsent(r.className, k -> new java.util.ArrayList<TestResult>()).add(r);
			failed += r.bad() ? 1 : 0;
			skipped += r.status == TestResult.SKIPPED ? 1 : 0;
			seconds += r.seconds;
		}
		boolean expandAll = list.size() <= 40;
		TreeItem firstBad = null;
		for(Map.Entry<String, java.util.List<TestResult>> entry : byClass.entrySet())
		{
			int bad = 0;
			for(TestResult r : entry.getValue())
			{
				bad += r.bad() ? 1 : 0;
			}
			TreeItem cls = new TreeItem(tree, SWT.NONE);
			cls.setText((bad > 0 ? "✘ " : "✔ ") + entry.getKey() + "  (" + entry.getValue().size() + ")");
			cls.setForeground(bad > 0 ? theme.errorText : theme.pass);
			for(TestResult r : entry.getValue())
			{
				TreeItem item = new TreeItem(cls, SWT.NONE);
				item.setData(r);
				item.setText(marker(r) + r.name + "  " + String.format("%.3f s", r.seconds));
				item.setForeground(r.bad() ? theme.errorText : r.status == TestResult.SKIPPED ? theme.gutter : theme.pass);
				if(r.bad() && firstBad == null)
				{
					firstBad = item;
				}
			}
			cls.setExpanded(expandAll || bad > 0);
		}
		summary.setText("Runs: " + (list.size() - skipped) + "/" + list.size() + "    Failures: " + failed + "    Skipped: " + skipped
			+ "    Time: " + String.format("%.2f s", seconds));
		bar.setBackground(failed > 0 ? theme.error : theme.pass);
		if(firstBad != null)
		{
			tree.setSelection(firstBad);
			showDetail();
		}
		composite.layout(true, true);
	}

	static String marker(TestResult r)
	{
		if(r.status == TestResult.PASSED)
		{
			return "✔ ";
		}
		if(r.status == TestResult.SKIPPED)
		{
			return "– ";
		}
		return "✘ ";
	}

	TestResult selected()
	{
		TreeItem[] sel = tree.getSelection();
		if(sel.length == 0 || !(sel[0].getData() instanceof TestResult))
		{
			return null;
		}
		return (TestResult)sel[0].getData();
	}

	void showDetail()
	{
		TestResult r = selected();
		if(r == null)
		{
			detail.setText("");
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(r.className).append('.').append(r.name).append('\n');
		if(!r.message.isEmpty())
		{
			sb.append('\n').append(r.message).append('\n');
		}
		if(!r.trace.isEmpty())
		{
			sb.append('\n').append(r.trace).append('\n');
		}
		detail.setText(sb.toString());
		linkify();
	}

	void linkify()
	{
		if(project == null)
		{
			return;
		}
		String text = detail.getText();
		Matcher m = FRAME.matcher(text);
		while(m.find())
		{
			File f = project.findSource(m.group(1), m.group(2));
			if(f == null)
			{
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

	Location linkAt(int x, int y)
	{
		try
		{
			int offset = detail.getOffsetAtPoint(new Point(x, y));
			StyleRange r = detail.getStyleRangeAtOffset(offset);
			if(r != null && r.data instanceof Location)
			{
				return (Location)r.data;
			}
		}
		catch(IllegalArgumentException e)
		{
		}
		return null;
	}

	void clicked(int x, int y)
	{
		Location l = linkAt(x, y);
		if(l != null && opener != null && detail.getSelectionCount() == 0)
		{
			opener.accept(l);
		}
	}

	void hover(int x, int y)
	{
		int cursor = linkAt(x, y) != null ? SWT.CURSOR_HAND : SWT.CURSOR_IBEAM;
		detail.setCursor(detail.getDisplay().getSystemCursor(cursor));
	}

	void openSelected()
	{
		TestResult r = selected();
		if(r == null || project == null || opener == null)
		{
			return;
		}
		File first = null;
		int firstLine = 1;
		Matcher m = FRAME.matcher(r.trace);
		while(m.find())
		{
			File f = project.findSource(m.group(1), m.group(2));
			if(f == null)
			{
				continue;
			}
			int line = Integer.parseInt(m.group(3));
			String cls = m.group(1);
			int inner = cls.indexOf('$');
			if((inner < 0 ? cls : cls.substring(0, inner)).equals(r.className))
			{
				opener.accept(new Location(f, line, 1));
				return;
			}
			if(first == null)
			{
				first = f;
				firstLine = line;
			}
		}
		if(first != null)
		{
			opener.accept(new Location(first, firstLine, 1));
			return;
		}
		File src = project.sourceFor(r.className);
		if(src != null)
		{
			opener.accept(new Location(src, 1, 1));
		}
	}
}
