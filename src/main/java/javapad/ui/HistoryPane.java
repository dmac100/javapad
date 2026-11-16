package javapad.ui;

import java.util.ArrayList;
import java.util.List;
import javapad.git.ChangedFile;
import javapad.git.Commit;
import javapad.git.GitRepo;
import javapad.git.GraphLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

// The History tab: the commit graph above, and below it the details of the selected commit with its changed files.
public class HistoryPane
{
	static final int LANE = 12;
	static final int LIMIT = 400;
	static final int[][] LANE_COLORS = {{86, 156, 214}, {106, 190, 100}, {229, 192, 123}, {198, 120, 221}, {244, 112, 112}, {78, 201, 176}, {255, 160, 80}, {160, 160, 220}};

	private final MainWindow w;
	private final Theme theme;
	private final HistoryActions actions;
	private final Composite composite;
	private final Label title;
	private final Button showAll;
	private final Button currentRadio;
	private final Button allRadio;
	private final Table commitTable;
	private final StyledText details;
	private final Table fileTable;
	private final SashForm vertical;
	private final SashForm lower;
	private final Font bold;
	private final Color[] laneColors = new Color[LANE_COLORS.length];
	private final Color[] refColors = new Color[3];
	private final Color refText;
	private List<Commit> commits = new ArrayList<Commit>();
	private List<GraphLayout.Row> rows = new ArrayList<GraphLayout.Row>();
	private List<ChangedFile> files = new ArrayList<ChangedFile>();
	private String path;
	private int logGeneration;
	private int filesGeneration;
	private int diffGeneration;
	private int detailLines;

	public HistoryPane(Composite parent, Theme theme, MainWindow window)
	{
		this.w = window;
		this.theme = theme;
		this.actions = new HistoryActions(window);
		composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(4, false);
		layout.marginWidth = 4;
		layout.marginHeight = 2;
		composite.setLayout(layout);
		title = new Label(composite, SWT.NONE);
		title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		currentRadio = new Button(composite, SWT.RADIO);
		currentRadio.setText("Current branch");
		allRadio = new Button(composite, SWT.RADIO);
		allRadio.setText("All branches");
		allRadio.setSelection(true);
		currentRadio.addListener(SWT.Selection, e -> refresh());
		allRadio.addListener(SWT.Selection, e -> refresh());
		showAll = new Button(composite, SWT.PUSH);
		showAll.setText("Show Whole Repository");
		showAll.addListener(SWT.Selection, e -> showPath(null));
		vertical = new SashForm(composite, SWT.VERTICAL);
		vertical.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1));
		commitTable = new Table(vertical, SWT.FULL_SELECTION | SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		commitTable.setHeaderVisible(true);
		String[] titles = {"Graph", "Message", "Commit", "Author", "Authored", "Committer", "Committed"};
		int[] widths = {80, 520, 80, 140, 130, 140, 130};
		for(int i = 0; i < titles.length; i++)
		{
			TableColumn c = new TableColumn(commitTable, SWT.NONE);
			c.setText(titles[i]);
			c.setWidth(widths[i]);
		}
		lower = new SashForm(vertical, SWT.HORIZONTAL);
		details = new StyledText(lower, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
		details.setFont(theme.font);
		ReadOnlyText.installKeys(details);
		fileTable = new Table(lower, SWT.FULL_SELECTION | SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
		fileTable.setHeaderVisible(true);
		String[] fileTitles = {"Changed Files", "State"};
		int[] fileWidths = {300, 90};
		for(int i = 0; i < fileTitles.length; i++)
		{
			TableColumn c = new TableColumn(fileTable, SWT.NONE);
			c.setText(fileTitles[i]);
			c.setWidth(fileWidths[i]);
		}
		vertical.setWeights(new int[] {55, 45});
		lower.setWeights(new int[] {70, 30});
		FontData[] fd = theme.font.getFontData();
		for(FontData d : fd)
		{
			d.setStyle(SWT.BOLD);
		}
		bold = new Font(parent.getDisplay(), fd);
		for(int i = 0; i < LANE_COLORS.length; i++)
		{
			laneColors[i] = new Color(parent.getDisplay(), LANE_COLORS[i][0], LANE_COLORS[i][1], LANE_COLORS[i][2]);
		}
		refColors[Commit.LOCAL] = new Color(parent.getDisplay(), 150, 205, 150);
		refColors[Commit.REMOTE] = new Color(parent.getDisplay(), 150, 185, 225);
		refColors[Commit.TAG] = new Color(parent.getDisplay(), 235, 210, 130);
		refText = new Color(parent.getDisplay(), 25, 25, 25);
		commitTable.addListener(SWT.Dispose, e ->
		{
			bold.dispose();
			refText.dispose();
			for(Color c : laneColors)
			{
				c.dispose();
			}
			for(Color c : refColors)
			{
				c.dispose();
			}
		});
		commitTable.addListener(SWT.MeasureItem, e -> e.height = Math.max(e.height, 22));
		commitTable.addListener(SWT.PaintItem, e -> paintCell(e));
		commitTable.addListener(SWT.Selection, e -> commitSelected());
		fileTable.addListener(SWT.Selection, e -> fileSelected());
		fileTable.addListener(SWT.DefaultSelection, e ->
		{
			Commit c = selectedCommit();
			ChangedFile f = selectedFile();
			if(c != null && f != null)
			{
				actions.compareWithPrevious(c, f);
			}
		});
		commitTable.setMenu(commitMenu());
		fileTable.setMenu(fileMenu());
		showAll.setVisible(false);
		title.setText("");
		restyle();
	}

	public Composite getControl()
	{
		return composite;
	}

	public void restyle()
	{
		composite.setBackground(theme.chrome);
		title.setBackground(theme.chrome);
		title.setForeground(theme.foreground);
		for(Button radio : new Button[] {currentRadio, allRadio})
		{
			radio.setBackground(theme.chrome);
			radio.setForeground(theme.foreground);
		}
		vertical.setBackground(theme.chrome);
		lower.setBackground(theme.chrome);
		for(Table t : new Table[] {commitTable, fileTable})
		{
			t.setBackground(theme.background);
			t.setForeground(theme.foreground);
			t.setHeaderBackground(theme.chrome);
			t.setHeaderForeground(theme.foreground);
		}
		details.setBackground(theme.background);
		details.setForeground(theme.foreground);
		commitTable.redraw();
	}

	// ---- loading

	// Shows the history of one file or folder; null for the whole repository.
	public void showPath(String path)
	{
		this.path = path;
		w.showBottomTab("history");
		refresh();
	}

	public void refresh()
	{
		GitRepo repo = w.git.repo();
		if(repo == null)
		{
			setCommits(new ArrayList<Commit>());
			title.setText(w.project() == null ? "" : "Not a git repository");
			showAll.setVisible(false);
			currentRadio.setEnabled(false);
			allRadio.setEnabled(false);
			composite.layout();
			return;
		}
		String shownPath = path;
		title.setText(shownPath == null ? "" : "History of " + shownPath);
		showAll.setVisible(shownPath != null);
		// the history of one file follows the current branch
		currentRadio.setEnabled(shownPath == null);
		allRadio.setEnabled(shownPath == null);
		boolean everything = allRadio.getSelection();
		composite.layout();
		Commit before = selectedCommit();
		String keep = before == null ? null : before.hash;
		int mine = ++logGeneration;
		w.git.background(() ->
		{
			List<Commit> list = repo.log(shownPath, LIMIT, shownPath == null && everything);
			return () ->
			{
				if(mine == logGeneration && repo == w.git.repo())
				{
					setCommits(shownPath == null ? list : linear(list));
					select(keep);
				}
			};
		});
	}

	// a log of one file skips commits, so each commit's parent is simply the next one listed
	static List<Commit> linear(List<Commit> list)
	{
		List<Commit> result = new ArrayList<Commit>();
		for(int i = 0; i < list.size(); i++)
		{
			List<String> parents = new ArrayList<String>();
			if(i + 1 < list.size())
			{
				parents.add(list.get(i + 1).hash);
			}
			result.add(list.get(i).withParents(parents));
		}
		return result;
	}

	void setCommits(List<Commit> list)
	{
		commits = list;
		rows = GraphLayout.layout(list);
		commitTable.setRedraw(false);
		commitTable.removeAll();
		for(Commit c : list)
		{
			TableItem item = new TableItem(commitTable, SWT.NONE);
			// the graph and message cells are drawn by paintCell
			item.setText(new String[] {"", "", c.shortHash(), c.authorName, Commit.shortDate(c.authorDate), c.committerName, Commit.shortDate(c.commitDate)});
		}
		commitTable.getColumn(0).setWidth(Math.min(220, Math.max(60, GraphLayout.width(rows) * LANE + 20)));
		commitTable.setRedraw(true);
		files = new ArrayList<ChangedFile>();
		fileTable.removeAll();
		details.setText("");
	}

	void select(String hash)
	{
		if(hash == null)
		{
			return;
		}
		for(int i = 0; i < commits.size(); i++)
		{
			if(commits.get(i).hash.equals(hash))
			{
				commitTable.setSelection(i);
				commitSelected();
				return;
			}
		}
	}

	Commit selectedCommit()
	{
		int i = commitTable.getSelectionIndex();
		return i < 0 || i >= commits.size() ? null : commits.get(i);
	}

	ChangedFile selectedFile()
	{
		int i = fileTable.getSelectionIndex();
		return i < 0 || i >= files.size() ? null : files.get(i);
	}

	void commitSelected()
	{
		Commit c = selectedCommit();
		fileTable.removeAll();
		files = new ArrayList<ChangedFile>();
		if(c == null)
		{
			details.setText("");
			return;
		}
		showDetails(c, "");
		GitRepo repo = w.git.repo();
		int mine = ++filesGeneration;
		w.git.background(() ->
		{
			List<ChangedFile> list = repo.changedFiles(c.hash);
			return () ->
			{
				if(mine != filesGeneration || c != selectedCommit())
				{
					return;
				}
				files = list;
				for(ChangedFile f : list)
				{
					TableItem item = new TableItem(fileTable, SWT.NONE);
					item.setText(new String[] {f.path, f.label()});
				}
			};
		});
	}

	void fileSelected()
	{
		Commit c = selectedCommit();
		ChangedFile f = selectedFile();
		if(c == null || f == null)
		{
			return;
		}
		GitRepo repo = w.git.repo();
		int mine = ++diffGeneration;
		w.git.background(() ->
		{
			String diff = repo.commitDiff(c.hash, f.path);
			return () ->
			{
				if(mine == diffGeneration && c == selectedCommit() && f == selectedFile())
				{
					showDetails(c, diff);
				}
			};
		});
	}

	// the commit's details, then the diff if there is one
	void showDetails(Commit c, String diff)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Commit:    ").append(c.hash).append('\n');
		sb.append("Author:    ").append(c.authorName).append(" <").append(c.authorEmail).append(">  ").append(Commit.shortDate(c.authorDate)).append('\n');
		sb.append("Committer: ").append(c.committerName).append(" <").append(c.committerEmail).append(">  ").append(Commit.shortDate(c.commitDate)).append('\n');
		List<String> names = new ArrayList<String>();
		for(Commit.Ref r : c.refs)
		{
			names.add(r.name);
		}
		if(!names.isEmpty())
		{
			sb.append("Refs:      ").append(String.join(", ", names)).append('\n');
		}
		if(!c.parents.isEmpty())
		{
			List<String> shortParents = new ArrayList<String>();
			for(String p : c.parents)
			{
				shortParents.add(p.substring(0, Math.min(8, p.length())));
			}
			sb.append("Parents:   ").append(String.join(", ", shortParents)).append('\n');
		}
		sb.append('\n').append(c.subject).append('\n');
		if(!c.body.isEmpty())
		{
			sb.append('\n').append(c.body).append('\n');
		}
		sb.append('\n');
		detailLines = sb.toString().split("\n", -1).length - 1;
		sb.append(diff);
		DiffView.show(details, sb.toString(), theme, detailLines);
		details.setTopIndex(0);
	}

	// ---- drawing

	void paintCell(Event e)
	{
		TableItem item = (TableItem)e.item;
		int index = commitTable.indexOf(item);
		if(index < 0 || index >= rows.size())
		{
			return;
		}
		boolean selected = (e.detail & SWT.SELECTED) != 0;
		GC gc = e.gc;
		if(e.index == 0)
		{
			paintGraph(gc, rows.get(index), e.x, e.y, e.height);
		}
		else if(e.index == 1)
		{
			paintMessage(gc, commits.get(index), e.x, e.y, e.height, selected);
		}
	}

	static int laneX(int x0, int lane)
	{
		return x0 + 8 + lane * LANE;
	}

	void paintGraph(GC gc, GraphLayout.Row row, int x0, int y, int h)
	{
		gc.setAntialias(SWT.ON);
		gc.setLineWidth(2);
		int mid = y + h / 2;
		int nodeX = laneX(x0, row.lane);
		for(int lane : row.through)
		{
			gc.setForeground(laneColors[lane % laneColors.length]);
			gc.drawLine(laneX(x0, lane), y, laneX(x0, lane), y + h);
		}
		for(int lane : row.from)
		{
			gc.setForeground(laneColors[lane % laneColors.length]);
			gc.drawLine(laneX(x0, lane), y, nodeX, mid);
		}
		for(int lane : row.to)
		{
			gc.setForeground(laneColors[lane % laneColors.length]);
			gc.drawLine(nodeX, mid, laneX(x0, lane), y + h);
		}
		gc.setBackground(laneColors[row.lane % laneColors.length]);
		gc.fillOval(nodeX - 4, mid - 4, 9, 9);
		gc.setForeground(theme.background);
		gc.setLineWidth(1);
		gc.drawOval(nodeX - 4, mid - 4, 9, 9);
	}

	void paintMessage(GC gc, Commit c, int x0, int y, int h, boolean selected)
	{
		gc.setAntialias(SWT.ON);
		int x = x0 + 4;
		Font normal = gc.getFont();
		for(Commit.Ref r : c.refs)
		{
			gc.setFont(r.current ? bold : normal);
			Point size = gc.textExtent(r.name);
			int boxH = size.y + 2;
			int boxY = y + (h - boxH) / 2;
			gc.setBackground(refColors[r.kind]);
			gc.fillRoundRectangle(x, boxY, size.x + 10, boxH, 10, 10);
			gc.setForeground(refText);
			gc.drawText(r.name, x + 5, boxY + 1, true);
			x += size.x + 16;
		}
		gc.setFont(normal);
		gc.setForeground(selected ? commitTable.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT) : theme.foreground);
		Point size = gc.textExtent(c.subject);
		gc.drawText(c.subject, x, y + (h - size.y) / 2, true);
	}

	// ---- menus

	Menu commitMenu()
	{
		Menu menu = new Menu(commitTable);
		List<MenuItem> items = new ArrayList<MenuItem>();
		items.add(item(menu, "Compare with Working Tree", c -> actions.compareWithWorkingTree(c)));
		new MenuItem(menu, SWT.SEPARATOR);
		items.add(item(menu, "Checkout", c -> actions.checkout(c)));
		items.add(item(menu, "Push Branch", c -> actions.pushBranch(c)));
		new MenuItem(menu, SWT.SEPARATOR);
		items.add(item(menu, "Create Branch...", c -> actions.createBranch(c)));
		items.add(item(menu, "Delete Branch...", c -> actions.deleteBranch(c)));
		items.add(item(menu, "Rename Branch...", c -> actions.renameBranch(c)));
		items.add(item(menu, "Create Tag...", c -> actions.createTag(c)));
		new MenuItem(menu, SWT.SEPARATOR);
		items.add(item(menu, "Cherry-pick", c -> actions.cherryPick(c)));
		items.add(item(menu, "Merge", c -> actions.merge(c)));
		items.add(item(menu, "Reset...", c -> actions.reset(c)));
		items.add(item(menu, "Revert", c -> actions.revert(c)));
		menu.addListener(SWT.Show, e ->
		{
			boolean on = selectedCommit() != null;
			for(MenuItem mi : items)
			{
				mi.setEnabled(on);
			}
		});
		return menu;
	}

	Menu fileMenu()
	{
		Menu menu = new Menu(fileTable);
		List<MenuItem> items = new ArrayList<MenuItem>();
		items.add(fileItem(menu, "Open Working Tree Version", (c, f) -> actions.openWorkingTree(f)));
		items.add(fileItem(menu, "Open This Version", (c, f) -> actions.openThisVersion(c, f)));
		items.add(fileItem(menu, "Open Previous Version", (c, f) -> actions.openPreviousVersion(c, f)));
		new MenuItem(menu, SWT.SEPARATOR);
		items.add(fileItem(menu, "Checkout This Version", (c, f) -> actions.checkoutThisVersion(c, f)));
		new MenuItem(menu, SWT.SEPARATOR);
		items.add(fileItem(menu, "Compare with Previous Revision", (c, f) -> actions.compareWithPrevious(c, f)));
		items.add(fileItem(menu, "Compare with Working Tree", (c, f) -> actions.compareWithWorkingTree(c, f)));
		menu.addListener(SWT.Show, e ->
		{
			boolean on = selectedCommit() != null && selectedFile() != null;
			for(MenuItem mi : items)
			{
				mi.setEnabled(on);
			}
		});
		return menu;
	}

	interface CommitAction
	{
		void run(Commit c);
	}

	interface FileAction
	{
		void run(Commit c, ChangedFile f);
	}

	MenuItem item(Menu menu, String text, CommitAction action)
	{
		MenuItem mi = new MenuItem(menu, SWT.PUSH);
		mi.setText(text);
		mi.addListener(SWT.Selection, e ->
		{
			Commit c = selectedCommit();
			if(c != null && w.git.ready())
			{
				action.run(c);
			}
		});
		return mi;
	}

	MenuItem fileItem(Menu menu, String text, FileAction action)
	{
		MenuItem mi = new MenuItem(menu, SWT.PUSH);
		mi.setText(text);
		mi.addListener(SWT.Selection, e ->
		{
			Commit c = selectedCommit();
			ChangedFile f = selectedFile();
			if(c != null && f != null && w.git.ready())
			{
				action.run(c, f);
			}
		});
		return mi;
	}
}
