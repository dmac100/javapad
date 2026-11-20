package javapad.ui.pane;

import javapad.ui.Theme;
import javapad.ui.editor.EditorTabs;
import javapad.ui.editor.ProblemMarks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javapad.git.GitStatus;
import javapad.model.EclipseProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

public class ProjectTree
{
	private final Tree tree;
	private final boolean sourcesOnly;
	private EclipseProject project;
	private Consumer<File> opener;
	// set when the project is in a git repository: tells which files are untracked, so their names are greyed
	private Predicate<File> untracked;
	// set when the project is in a git repository: tells which files, and folders holding files, have a merge conflict
	private Predicate<File> conflicts;
	// set when the project is in a git repository: tells which files are modified, so their names are green
	private Predicate<File> modified;
	private Color grey;
	private Theme theme;
	private GitStatus branch;
	// the files with errors and every folder above them, as real paths
	private Set<File> withErrors = new HashSet<File>();
	private final Map<File, File> realPaths = new HashMap<File, File>();

	public ProjectTree(Composite parent, boolean sourcesOnly)
	{
		this.sourcesOnly = sourcesOnly;
		tree = new Tree(parent, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
		tree.addListener(SWT.PaintItem, e ->
		{
			paintName(e);
			paintBranch(e);
			paintError(e);
			paintConflict(e);
		});
		tree.addListener(SWT.Expand, e -> fill((TreeItem)e.item));
		tree.addListener(SWT.Dispose, e -> disposeColor());
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

	// Drag and drop within the tree: dropping a file or folder on a folder (or beside a file, into its folder) calls the mover with the dragged file and the target folder.
	public void setMover(BiConsumer<File, File> mover)
	{
		Transfer[] types = {TextTransfer.getInstance()};
		DragSource source = new DragSource(tree, DND.DROP_MOVE);
		source.setTransfer(types);
		File[] dragged = new File[1];
		source.addDragListener(new DragSourceAdapter()
		{
			public void dragStart(DragSourceEvent e)
			{
				dragged[0] = selected();
				e.doit = dragged[0] != null && project != null && !dragged[0].equals(project.root);
			}

			public void dragSetData(DragSourceEvent e)
			{
				e.data = dragged[0] == null ? "" : dragged[0].getPath();
			}
		});
		DropTarget target = new DropTarget(tree, DND.DROP_MOVE);
		target.setTransfer(types);
		target.addDropListener(new DropTargetAdapter()
		{
			public void dragOver(DropTargetEvent e)
			{
				e.feedback = DND.FEEDBACK_SELECT | DND.FEEDBACK_SCROLL | DND.FEEDBACK_EXPAND;
				e.detail = dropDir(e) == null ? DND.DROP_NONE : DND.DROP_MOVE;
			}

			public void drop(DropTargetEvent e)
			{
				File dir = dropDir(e);
				File f = dragged[0];
				dragged[0] = null;
				if(dir != null && f != null)
				{
					mover.accept(f, dir);
				}
			}

			private File dropDir(DropTargetEvent e)
			{
				File f = dragged[0];
				TreeItem over = tree.getItem(tree.toControl(e.x, e.y));
				if(f == null || over == null || !(over.getData() instanceof File))
				{
					return null;
				}
				File dir = (File)over.getData();
				if(!dir.isDirectory())
				{
					dir = dir.getParentFile();
				}
				return dir == null || dir.equals(f.getParentFile()) || dir.toPath().startsWith(f.toPath()) ? null : dir;
			}
		});
	}

	// The branch and its commits ahead (up arrow) and behind (down arrow), shown after the name of the project folder; null for none.
	public void setBranch(GitStatus status)
	{
		branch = status == null || status.branch.isEmpty() ? null : status;
		for(TreeItem top : tree.getItems())
		{
			if(project != null && project.root.equals(top.getData()))
			{
				top.setText(label(top.getData(), top.getText()));
			}
		}
		tree.redraw();
	}

	// the branch and its arrows, in square brackets
	String info()
	{
		String s = branch.branch;
		if(branch.ahead > 0)
		{
			s += " \u2191" + branch.ahead;
		}
		if(branch.behind > 0)
		{
			s += " \u2193" + branch.behind;
		}
		return "[" + s + "]";
	}

	// the name of an item; the project folder's also carries the branch
	String label(Object data, String current)
	{
		String name = current;
		int gap = name.indexOf("  ");
		if(gap >= 0)
		{
			name = name.substring(0, gap);
		}
		if(branch == null || project == null || !project.root.equals(data))
		{
			return name;
		}
		return name + "  " + info();
	}

	// the branch and arrows are drawn over the plain text in one colour of their own
	// With a paint listener on the tree the toolkit ignores the colour of an item, so the name of a coloured item (untracked, modified) is drawn here.
	void paintName(Event e)
	{
		if(theme == null || !(e.item instanceof TreeItem) || (e.detail & SWT.SELECTED) != 0)
		{
			return;
		}
		TreeItem item = (TreeItem)e.item;
		Color color = item.getForeground();
		if(color.equals(tree.getForeground()) || item.getData() == null)
		{
			return;
		}
		String name = item.getText();
		if(item.getData().equals(project == null ? null : project.root) && name.indexOf("  ") > 0)
		{
			// the project's name, without the branch that is drawn after it
			name = name.substring(0, name.indexOf("  "));
		}
		Rectangle b = item.getBounds(0);
		Point extent = e.gc.stringExtent(name);
		e.gc.setBackground(theme.sidebar);
		e.gc.fillRectangle(b.x, b.y, extent.x + 2, b.height);
		e.gc.setForeground(color);
		e.gc.drawString(name, b.x, b.y + (b.height - extent.y) / 2, true);
	}

	void paintBranch(Event e)
	{
		if(branch == null || theme == null || project == null || !(e.item instanceof TreeItem))
		{
			return;
		}
		TreeItem item = (TreeItem)e.item;
		if(!project.root.equals(item.getData()))
		{
			return;
		}
		String text = item.getText();
		int gap = text.indexOf("  ");
		if(gap < 0)
		{
			return;
		}
		String name = text.substring(0, gap);
		String info = text.substring(gap + 2);
		GC gc = e.gc;
		Rectangle b = item.getBounds(0);
		int x = b.x + gc.stringExtent(name + "  ").x;
		boolean selected = (e.detail & SWT.SELECTED) != 0;
		gc.setBackground(selected ? tree.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION) : theme.sidebar);
		gc.fillRectangle(x, b.y, gc.stringExtent(info).x + 2, b.height);
		gc.setForeground(theme.branch);
		gc.drawString(info, x, b.y + 2, true);
	}

	// Marks the files with errors in them, and the folders that hold them.
	public void setErrorFiles(Set<File> files)
	{
		realPaths.clear();
		Set<File> marked = new HashSet<File>();
		for(File f : files)
		{
			for(File up = EditorTabs.canonical(f); up != null; up = up.getParentFile())
			{
				marked.add(up);
			}
		}
		if(!marked.equals(withErrors))
		{
			withErrors = marked;
			tree.redraw();
		}
	}

	File realPath(File f)
	{
		File real = realPaths.get(f);
		if(real == null)
		{
			real = EditorTabs.canonical(f);
			realPaths.put(f, real);
		}
		return real;
	}

	// an error sign after the name (and branch) of an item with errors in it
	void paintError(Event e)
	{
		if(withErrors.isEmpty() || theme == null || !(e.item instanceof TreeItem))
		{
			return;
		}
		TreeItem item = (TreeItem)e.item;
		if(!(item.getData() instanceof File) || !withErrors.contains(realPath((File)item.getData())))
		{
			return;
		}
		Rectangle b = item.getBounds(0);
		int size = Math.max(7, Math.min(9, b.height - 8));
		int x = b.x + e.gc.stringExtent(item.getText()).x + 6;
		ProblemMarks.drawError(e.gc, theme, x, b.y + (b.height - size) / 2, size);
	}

	// Marks the files and folders the test calls conflicted; null removes the marks.
	public void setConflictTest(Predicate<File> test)
	{
		conflicts = test;
		tree.redraw();
	}

	// a sign after the name (and the error sign) of an item with a merge conflict in it
	void paintConflict(Event e)
	{
		if(conflicts == null || theme == null || !(e.item instanceof TreeItem))
		{
			return;
		}
		TreeItem item = (TreeItem)e.item;
		if(!(item.getData() instanceof File) || !conflicts.test((File)item.getData()))
		{
			return;
		}
		Rectangle b = item.getBounds(0);
		int size = Math.max(7, Math.min(9, b.height - 8));
		int x = b.x + e.gc.stringExtent(item.getText()).x + 6;
		if(!withErrors.isEmpty() && withErrors.contains(realPath((File)item.getData())))
		{
			x += size + 4;
		}
		ProblemMarks.drawConflict(e.gc, theme, x, b.y + (b.height - size) / 2, size);
	}

	public void restyle(Theme theme)
	{
		this.theme = theme;
		tree.setBackground(theme.sidebar);
		tree.setForeground(theme.sidebarText);
		disposeColor();
		// the name of an untracked file: the text colour half way to the background
		RGB text = theme.sidebarText.getRGB();
		RGB back = theme.sidebar.getRGB();
		grey = new Color(tree.getDisplay(), (text.red + back.red) / 2, (text.green + back.green) / 2, (text.blue + back.blue) / 2);
		for(TreeItem top : tree.getItems())
		{
			decorate(top);
		}
	}

	// Greys the names of the files the test calls untracked; null removes the greying.
	public void setUntrackedTest(Predicate<File> test)
	{
		untracked = test;
		for(TreeItem top : tree.getItems())
		{
			decorate(top);
		}
	}

	// Colours the names of the files the test calls modified green; null removes the colour.
	public void setModifiedTest(Predicate<File> test)
	{
		modified = test;
		for(TreeItem top : tree.getItems())
		{
			decorate(top);
		}
		tree.redraw();
	}

	void decorate(TreeItem item)
	{
		item.setForeground(colorFor(item.getData()));
		for(TreeItem child : item.getItems())
		{
			if(child.getData() instanceof File && !((File)child.getData()).getPath().equals("."))
			{
				decorate(child);
			}
		}
	}

	Color colorFor(Object data)
	{
		if(untracked == null || grey == null || !(data instanceof File))
		{
			return null;
		}
		if(untracked.test((File)data))
		{
			return grey;
		}
		return modified != null && theme != null && modified.test((File)data) ? theme.modified : null;
	}

	void disposeColor()
	{
		if(grey != null)
		{
			grey.dispose();
			grey = null;
		}
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
		Path target = real(file);
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

	static Path real(File f)
	{
		try
		{
			return f.toPath().toRealPath();
		}
		catch(IOException e)
		{
			return f.toPath().toAbsolutePath().normalize();
		}
	}

	TreeItem descend(TreeItem item, Path target)
	{
		if(!(item.getData() instanceof File))
		{
			return null;
		}
		Path here = real((File)item.getData());
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

	// the folders that are open, so a refresh can open them again
	void collectExpanded(TreeItem item, Set<File> into)
	{
		if(item.getExpanded() && item.getData() instanceof File)
		{
			into.add((File)item.getData());
			for(TreeItem child : item.getItems())
			{
				collectExpanded(child, into);
			}
		}
	}

	void restoreExpanded(TreeItem item, Set<File> open)
	{
		for(TreeItem child : item.getItems())
		{
			if(child.getData() instanceof File && open.contains(child.getData()))
			{
				fill(child);
				child.setExpanded(true);
				restoreExpanded(child, open);
			}
		}
	}

	// Rebuilds the tree from the disk, keeping the folders that were open.
	public void refresh()
	{
		Set<File> open = new HashSet<File>();
		for(TreeItem top : tree.getItems())
		{
			collectExpanded(top, open);
		}
		File selection = selected();
		build();
		for(TreeItem top : tree.getItems())
		{
			restoreExpanded(top, open);
		}
		if(selection != null)
		{
			reveal(selection);
		}
	}

	void build()
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
				top.setText(label(src, src.equals(project.root) ? project.name : project.relative(src)));
				top.setData(src);
				top.setForeground(colorFor(src));
				fill(top);
				top.setExpanded(true);
			}
			return;
		}
		TreeItem root = new TreeItem(tree, SWT.NONE);
		root.setText(label(project.root, project.name));
		root.setData(project.root);
		root.setForeground(colorFor(project.root));
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
			child.setForeground(colorFor(f));
			if(f.isDirectory())
			{
				new TreeItem(child, SWT.NONE).setData(new File("."));
			}
		}
	}
}
