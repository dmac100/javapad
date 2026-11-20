package javapad.ui.pane;

import javapad.ui.Theme;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javapad.lang.FileSearch;
import javapad.model.EclipseProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

// The Search tab: the lines found by "Find in Files", grouped by file.
public class SearchPane
{
	private final Composite composite;
	private final Label summary;
	private final Tree tree;
	private final Theme theme;
	private EclipseProject project;
	private Opener opener;

	public interface Opener
	{
		void open(File file, int line, int column, int length);
	}

	public SearchPane(Composite parent, Theme theme)
	{
		this.theme = theme;
		composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 4;
		layout.marginHeight = 2;
		composite.setLayout(layout);
		summary = new Label(composite, SWT.NONE);
		summary.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		tree = new Tree(composite, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
		tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tree.addListener(SWT.DefaultSelection, e -> activate());
		summary.setText("Use Edit > Find in Files to search the project.");
		restyle();
	}

	public Composite getControl()
	{
		return composite;
	}

	public void setProject(EclipseProject project)
	{
		this.project = project;
		tree.removeAll();
		summary.setText("Use Edit > Find in Files to search the project.");
	}

	public void setOpener(Opener opener)
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
	}

	public void searching(String query)
	{
		tree.removeAll();
		summary.setText("Searching for \"" + query + "\"...");
	}

	public void show(String query, FileSearch.Result result)
	{
		tree.removeAll();
		Map<File, List<FileSearch.Match>> byFile = new LinkedHashMap<File, List<FileSearch.Match>>();
		for(FileSearch.Match m : result.matches)
		{
			byFile.computeIfAbsent(m.file, k -> new ArrayList<FileSearch.Match>()).add(m);
		}
		for(Map.Entry<File, List<FileSearch.Match>> entry : byFile.entrySet())
		{
			String name = project == null ? entry.getKey().getName() : project.relative(entry.getKey()).replace(File.separatorChar, '/');
			TreeItem file = new TreeItem(tree, SWT.NONE);
			file.setText(name + "  (" + entry.getValue().size() + ")");
			for(FileSearch.Match m : entry.getValue())
			{
				TreeItem line = new TreeItem(file, SWT.NONE);
				line.setText(m.line + ":  " + m.text.trim());
				line.setData(m);
			}
			file.setExpanded(byFile.size() <= 20 || entry.getValue().size() <= 5);
		}
		String lines = result.matches.size() == 1 ? "1 line" : result.matches.size() + " lines";
		String files = byFile.size() == 1 ? "1 file" : byFile.size() + " files";
		summary.setText("\"" + query + "\": " + lines + " in " + files + " (" + result.filesSearched + " searched)" + (result.truncated ? ", stopped at the limit" : ""));
		if(tree.getItemCount() > 0)
		{
			tree.showItem(tree.getItem(0));
		}
	}

	void activate()
	{
		TreeItem[] sel = tree.getSelection();
		if(sel.length == 0)
		{
			return;
		}
		if(!(sel[0].getData() instanceof FileSearch.Match))
		{
			sel[0].setExpanded(!sel[0].getExpanded());
			return;
		}
		FileSearch.Match m = (FileSearch.Match)sel[0].getData();
		if(opener != null)
		{
			opener.open(m.file, m.line, m.column, m.length);
		}
	}
}
