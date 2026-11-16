package javapad.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javapad.model.EclipseProject;
import javapad.model.Problem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

public class ProblemsPane
{
	private final Tree table;
	private final List<Problem> shown = new ArrayList<Problem>();
	private EclipseProject project;
	private Consumer<Location> opener;
	private Consumer<Location> previewer;

	public ProblemsPane(Composite parent)
	{
		table = new Tree(parent, SWT.FULL_SELECTION | SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		String[] titles = {"Description", "Resource", "Line"};
		int[] widths = {620, 300, 60};
		for(int i = 0; i < titles.length; i++)
		{
			TreeColumn c = new TreeColumn(table, SWT.NONE);
			c.setText(titles[i]);
			c.setWidth(widths[i]);
		}
		table.addListener(SWT.DefaultSelection, e -> activate());
		table.addListener(SWT.Selection, e -> preview());
		Menu menu = new Menu(table);
		MenuItem copy = addItem(menu, "Copy\tCtrl+C", () -> copy(selected()));
		MenuItem copyErrors = addItem(menu, "Copy Errors", () -> copy(errors()));
		MenuItem copyAll = addItem(menu, "Copy All", () -> copy(shown));
		table.setMenu(menu);
		menu.addListener(SWT.Show, e ->
		{
			copy.setEnabled(!selected().isEmpty());
			copyErrors.setEnabled(errorCount() > 0);
			copyAll.setEnabled(!shown.isEmpty());
		});
		table.addListener(SWT.KeyDown, e ->
		{
			if((e.stateMask & SWT.MOD1) != 0 && e.keyCode == 'c')
			{
				copy(selected());
				e.doit = false;
			}
		});
	}

	MenuItem addItem(Menu menu, String text, Runnable action)
	{
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(text);
		item.addListener(SWT.Selection, e -> action.run());
		return item;
	}

	List<Problem> selected()
	{
		List<Problem> result = new ArrayList<Problem>();
		for(TreeItem item : table.getSelection())
		{
			if(item.getData() instanceof Problem)
			{
				result.add((Problem)item.getData());
			}
		}
		return result;
	}

	List<Problem> errors()
	{
		List<Problem> result = new ArrayList<Problem>();
		for(Problem p : shown)
		{
			if(p.error)
			{
				result.add(p);
			}
		}
		return result;
	}

	public String format(List<Problem> problems)
	{
		StringBuilder sb = new StringBuilder();
		for(Problem p : problems)
		{
			if(!p.file.isEmpty())
			{
				sb.append(project.relative(new File(p.file)));
				if(p.line > 0)
				{
					sb.append(':').append(p.line);
				}
				sb.append(": ");
			}
			sb.append(p.error ? "error: " : "warning: ");
			sb.append(p.message).append('\n');
		}
		return sb.toString();
	}

	void copy(List<Problem> problems)
	{
		if(problems.isEmpty())
		{
			return;
		}
		Clipboard clipboard = new Clipboard(table.getDisplay());
		try
		{
			clipboard.setContents(new Object[] {format(problems)}, new Transfer[] {TextTransfer.getInstance()});
		}
		finally
		{
			clipboard.dispose();
		}
	}

	public void restyle(Theme theme)
	{
		table.setBackground(theme.background);
		table.setForeground(theme.foreground);
		table.setHeaderBackground(theme.chrome);
		table.setHeaderForeground(theme.foreground);
	}

	public Tree getControl()
	{
		return table;
	}

	public void setProject(EclipseProject project)
	{
		this.project = project;
	}

	public void setOpener(Consumer<Location> opener)
	{
		this.opener = opener;
	}

	// Called when a problem is selected with a click or the arrow keys: show its line without leaving the list.
	public void setPreviewer(Consumer<Location> previewer)
	{
		this.previewer = previewer;
	}

	void preview()
	{
		List<Problem> sel = selected();
		if(sel.isEmpty() || previewer == null)
		{
			return;
		}
		Problem p = sel.get(0);
		if(!p.file.isEmpty())
		{
			previewer.accept(new Location(new File(p.file), p.line, p.column));
		}
	}

	public void setProblems(List<Problem> problems)
	{
		shown.clear();
		for(Problem p : problems)
		{
			if(p.error)
			{
				shown.add(p);
			}
		}
		for(Problem p : problems)
		{
			if(!p.error)
			{
				shown.add(p);
			}
		}
		table.removeAll();
		addGroup("Errors", true);
		addGroup("Warnings", false);
	}

	// A top-level "Errors (n)" or "Warnings (n)" node with its problems below it.
	void addGroup(String title, boolean errors)
	{
		int n = errors ? errorCount() : shown.size() - errorCount();
		TreeItem group = new TreeItem(table, SWT.NONE);
		group.setText(title + " (" + n + ")");
		for(Problem p : shown)
		{
			if(p.error == errors)
			{
				TreeItem item = new TreeItem(group, SWT.NONE);
				item.setData(p);
				String resource = p.file.isEmpty() ? "" : project.relative(new File(p.file));
				item.setText(new String[] {p.message, resource, p.line > 0 ? String.valueOf(p.line) : ""});
			}
		}
		group.setExpanded(n > 0);
	}

	public int errorCount()
	{
		int n = 0;
		for(Problem p : shown)
		{
			if(p.error)
			{
				n++;
			}
		}
		return n;
	}

	public int count()
	{
		return shown.size();
	}

	void activate()
	{
		TreeItem[] sel = table.getSelection();
		if(sel.length == 0 || opener == null)
		{
			return;
		}
		if(!(sel[0].getData() instanceof Problem))
		{
			sel[0].setExpanded(!sel[0].getExpanded());
			return;
		}
		Problem p = (Problem)sel[0].getData();
		if(!p.file.isEmpty())
		{
			opener.accept(new Location(new File(p.file), p.line, p.column));
		}
	}
}
