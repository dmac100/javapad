package javapad.ui;

import java.io.File;
import java.util.Arrays;
import java.util.function.Consumer;
import javapad.model.EclipseProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

public class ProjectTree
{
	private final Tree tree;
	private final boolean sourcesOnly;
	private EclipseProject project;
	private Consumer<File> opener;

	public ProjectTree(Composite parent, boolean sourcesOnly)
	{
		this.sourcesOnly = sourcesOnly;
		tree = new Tree(parent, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
		tree.addListener(SWT.Expand, e -> fill((TreeItem)e.item));
		tree.addListener(SWT.DefaultSelection, e ->
		{
			File f = selected();
			if(f == null)
			{
				return;
			}
			if(f.isFile() && opener != null)
			{
				opener.accept(f);
			}
			else if(f.isDirectory())
			{
				toggle(tree.getSelection()[0]);
			}
		});
	}

	public void restyle(Theme theme)
	{
		tree.setBackground(theme.background);
		tree.setForeground(theme.foreground);
	}

	public Tree getControl()
	{
		return tree;
	}

	public void setOpener(Consumer<File> opener)
	{
		this.opener = opener;
	}

	public void setProject(EclipseProject project)
	{
		this.project = project;
		refresh();
	}

	public File selected()
	{
		TreeItem[] sel = tree.getSelection();
		if(sel.length == 0)
		{
			return null;
		}
		return (File)sel[0].getData();
	}

	public void selectRoot()
	{
		if(tree.getItemCount() > 0)
		{
			tree.setSelection(tree.getItem(0));
		}
	}

	// Double-click or Enter on a folder opens it if it is closed and closes it if it is open.
	void toggle(TreeItem item)
	{
		boolean expand = !item.getExpanded();
		if(expand && !loaded(item))
		{
			fill(item);
		}
		item.setExpanded(expand);
	}

	static boolean loaded(TreeItem item)
	{
		TreeItem[] kids = item.getItems();
		return !(kids.length == 1 && kids[0].getData() instanceof File && ((File)kids[0].getData()).getPath().equals("."));
	}

	// Expands the folders down to the file and selects it. Returns false when this tree does not show it.
	public boolean reveal(File file)
	{
		java.nio.file.Path target = real(file);
		for(TreeItem top : tree.getItems())
		{
			TreeItem found = descend(top, target);
			if(found != null)
			{
				tree.setSelection(found);
				tree.showItem(found);
				return true;
			}
		}
		return false;
	}

	static java.nio.file.Path real(File f)
	{
		try
		{
			return f.toPath().toRealPath();
		}
		catch(java.io.IOException e)
		{
			return f.toPath().toAbsolutePath().normalize();
		}
	}

	TreeItem descend(TreeItem item, java.nio.file.Path target)
	{
		if(!(item.getData() instanceof File))
		{
			return null;
		}
		java.nio.file.Path here = real((File)item.getData());
		if(here.equals(target))
		{
			return item;
		}
		if(!target.startsWith(here))
		{
			return null;
		}
		if(!loaded(item))
		{
			fill(item);
		}
		item.setExpanded(true);
		for(TreeItem child : item.getItems())
		{
			TreeItem found = descend(child, target);
			if(found != null)
			{
				return found;
			}
		}
		return null;
	}

	public void refresh()
	{
		tree.removeAll();
		if(project == null)
		{
			return;
		}
		if(sourcesOnly)
		{
			for(File src : project.sourceFolders)
			{
				TreeItem top = new TreeItem(tree, SWT.NONE);
				top.setText(src.equals(project.root) ? project.name : project.relative(src));
				top.setData(src);
				fill(top);
				top.setExpanded(true);
			}
			return;
		}
		TreeItem root = new TreeItem(tree, SWT.NONE);
		root.setText(project.name);
		root.setData(project.root);
		fill(root);
		root.setExpanded(true);
		for(TreeItem child : root.getItems())
		{
			File f = (File)child.getData();
			if(project.isSource(new File(f, "x")) || project.sourceFolders.contains(f))
			{
				child.setExpanded(true);
				fill(child);
			}
		}
	}

	boolean hidden(File f)
	{
		String n = f.getName();
		if(n.equals(".git") || n.equals(".javapad"))
		{
			return true;
		}
		return f.equals(project.outputFolder);
	}

	boolean containsJava(File dir)
	{
		File[] children = dir.listFiles();
		if(children == null)
		{
			return false;
		}
		for(File f : children)
		{
			if(hidden(f))
			{
				continue;
			}
			if(f.isDirectory() ? containsJava(f) : f.getName().endsWith(".java"))
			{
				return true;
			}
		}
		return false;
	}

	void fill(TreeItem item)
	{
		File dir = (File)item.getData();
		if(!dir.isDirectory())
		{
			return;
		}
		item.removeAll();
		File[] children = dir.listFiles();
		if(children == null)
		{
			return;
		}
		Arrays.sort(children, (a, b) ->
		{
			if(a.isDirectory() != b.isDirectory())
			{
				return a.isDirectory() ? -1 : 1;
			}
			return a.getName().compareToIgnoreCase(b.getName());
		});
		for(File f : children)
		{
			if(hidden(f))
			{
				continue;
			}
			if(sourcesOnly && !(f.isDirectory() ? containsJava(f) : f.getName().endsWith(".java")))
			{
				continue;
			}
			TreeItem child = new TreeItem(item, SWT.NONE);
			child.setText(f.getName());
			child.setData(f);
			if(f.isDirectory())
			{
				new TreeItem(child, SWT.NONE).setData(new File("."));
			}
		}
	}
}
