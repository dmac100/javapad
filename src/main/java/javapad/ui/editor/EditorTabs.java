package javapad.ui.editor;

import javapad.ui.Theme;
import javapad.ui.action.NavigationActions;
import javapad.ui.dialog.ConfirmDialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javapad.lang.Occurrences;
import javapad.model.Problem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Shell;

public class EditorTabs implements TabDrag.Host
{
	// a container holding either one folder or a tree of splits (a SashForm of two folders or splits)
	private final Composite area;
	private CTabFolder current;
	private final Theme theme;
	private final Shell shell;
	private final TabDrag drag;
	private List<Problem> problems = new ArrayList<Problem>();
	private Runnable compileHook;
	private CodeAssist assist;
	private NavigationActions navigation;
	private Consumer<EditorTab> tabHook;
	private Consumer<List<Problem>> problemsHook;

	public EditorTabs(Composite parent, Theme theme)
	{
		this.theme = theme;
		this.shell = parent.getShell();
		area = new Composite(parent, SWT.NONE);
		area.setLayout(new FillLayout());
		drag = new TabDrag(shell, this);
		current = newFolder(area);
	}

	CTabFolder newFolder(Composite parent)
	{
		CTabFolder folder = new CTabFolder(parent, SWT.BORDER);
		folder.setTabHeight(folder.getTabHeight() + 3);
		styleFolder(folder, theme);
		folder.addCTabFolder2Listener(new CTabFolder2Adapter()
		{
			public void close(CTabFolderEvent e)
			{
				e.doit = confirmClose((EditorTab)((CTabItem)e.item).getData());
				shell.getDisplay().asyncExec(() -> removeIfEmpty(folder));
			}
		});
		folder.addListener(SWT.Selection, e -> current = folder);
		drag.attach(folder);
		return folder;
	}

	static void styleFolder(CTabFolder folder, Theme theme)
	{
		folder.setBackground(theme.chrome);
		folder.setForeground(theme.foreground);
		folder.setSelectionBackground(theme.background);
		folder.setSelectionForeground(theme.foreground);
		if(folder.getParent() instanceof SashForm)
		{
			styleSash((SashForm)folder.getParent(), theme);
		}
	}

	public static void styleSash(SashForm sash, Theme theme)
	{
		sash.setBackground(theme.divider);
		sash.SASH_WIDTH = 5;
	}

	public void restyle()
	{
		for(CTabFolder folder : folders())
		{
			styleFolder(folder, theme);
		}
	}

	// every folder, left to right and top to bottom
	public List<CTabFolder> folders()
	{
		List<CTabFolder> list = new ArrayList<CTabFolder>();
		collect(area, list);
		return list;
	}

	static void collect(Composite parent, List<CTabFolder> list)
	{
		for(Control c : parent.getChildren())
		{
			if(c instanceof CTabFolder)
			{
				list.add((CTabFolder)c);
			}
			else if(c instanceof Composite && !(c instanceof Sash))
			{
				collect((Composite)c, list);
			}
		}
	}

	// the folder that has the keyboard focus, or else the one used last
	CTabFolder currentFolder()
	{
		List<CTabFolder> all = folders();
		for(Control c = shell.getDisplay().getFocusControl(); c != null; c = c.getParent())
		{
			if(c instanceof CTabFolder && all.contains(c))
			{
				current = (CTabFolder)c;
				break;
			}
		}
		if(current == null || current.isDisposed() || !all.contains(current))
		{
			current = all.get(0);
		}
		return current;
	}

	// Splits a folder in two and returns the new, empty folder on the given side (TabDrag.LEFT and so on).
	CTabFolder split(CTabFolder target, int side)
	{
		boolean horizontal = side == TabDrag.LEFT || side == TabDrag.RIGHT;
		Composite parent = target.getParent();
		SashForm sash = new SashForm(parent, horizontal ? SWT.HORIZONTAL : SWT.VERTICAL);
		sash.moveAbove(target);
		target.setParent(sash);
		CTabFolder created = newFolder(sash);
		if(side == TabDrag.LEFT || side == TabDrag.TOP)
		{
			created.moveAbove(target);
		}
		parent.layout(true, true);
		return created;
	}

	// An empty folder goes away (the last one stays), and a split left with a single part is replaced by that part.
	void removeIfEmpty(CTabFolder folder)
	{
		if(folder.isDisposed() || folder.getItemCount() > 0 || folders().size() < 2)
		{
			return;
		}
		Composite parent = folder.getParent();
		folder.dispose();
		if(parent == area || parent.isDisposed())
		{
			return;
		}
		Control only = null;
		int count = 0;
		for(Control c : parent.getChildren())
		{
			if(!(c instanceof Sash))
			{
				only = c;
				count++;
			}
		}
		if(count == 1)
		{
			Composite grand = parent.getParent();
			only.setParent(grand);
			only.moveAbove(parent);
			parent.dispose();
			grand.layout(true, true);
		}
		else
		{
			parent.layout(true, true);
		}
	}

	public boolean splits()
	{
		return true;
	}

	public void drop(CTabItem item, CTabFolder target, int zone, int index)
	{
		EditorTab tab = (EditorTab)item.getData();
		CTabFolder from = item.getParent();
		if(zone == TabDrag.INSERT)
		{
			tab.moveTo(target, index);
		}
		else
		{
			tab.moveTo(split(target, zone), 0);
		}
		current = tab.item.getParent();
		removeIfEmpty(from);
		tab.editor.getControl().setFocus();
	}

	// Called once for every tab that is opened, to add menus and the like.
	public void setTabHook(Consumer<EditorTab> hook)
	{
		tabHook = hook;
	}

	// Called with every new list of problems, so other views can mark them too.
	public void setProblemsHook(Consumer<List<Problem>> hook)
	{
		problemsHook = hook;
	}

	public void setNavigation(NavigationActions navigation)
	{
		this.navigation = navigation;
	}

	public void setAssist(CodeAssist assist)
	{
		this.assist = assist;
	}

	public void setCompileHook(Runnable hook)
	{
		compileHook = hook;
	}

	public Composite getControl()
	{
		return area;
	}

	public static File canonical(File file)
	{
		try
		{
			return file.toPath().toRealPath().toFile();
		}
		catch(IOException e)
		{
			return file.toPath().toAbsolutePath().normalize().toFile();
		}
	}

	public EditorTab open(File requested)
	{
		File file = canonical(requested);
		for(EditorTab tab : tabs())
		{
			if(canonical(tab.file).equals(file))
			{
				tab.item.getParent().setSelection(tab.item);
				current = tab.item.getParent();
				return tab;
			}
		}
		try
		{
			EditorTab tab = new EditorTab(currentFolder(), file, theme);
			tab.editor.setProblems(problemsFor(file));
			tab.editor.setCompileHook(compileHook);
			boolean java = tab.file.getName().endsWith(".java");
			if(assist != null && java)
			{
				tab.editor.setAssist((t, o, d) -> assist.complete(tab.file, t, o, d));
			}
			if(assist != null && java)
			{
				tab.editor.setOccurrenceFinder((t, offset, length, done) -> assist.submit(() -> Occurrences.find(assist.analyzer(), tab.file, t, offset, length), done));
			}
			if(navigation != null && java)
			{
				tab.editor.setNavigation(new CodeEditor.Navigation()
				{
					public void declaration(int offset)
					{
						navigation.openDeclaration(tab, offset);
					}

					public void implementation(int offset)
					{
						navigation.openImplementation(tab, offset);
					}
				});
			}
			if(tabHook != null)
			{
				tabHook.accept(tab);
			}
			tab.item.getParent().setSelection(tab.item);
			return tab;
		}
		catch(IOException e)
		{
			e.printStackTrace();
			ConfirmDialog.message(shell, "Error", "Cannot open " + file + ": " + e.getMessage(), SWT.ICON_ERROR);
			return null;
		}
	}

	public EditorTab active()
	{
		CTabItem item = currentFolder().getSelection();
		return item == null ? null : (EditorTab)item.getData();
	}

	public void show(EditorTab tab)
	{
		tab.item.getParent().setSelection(tab.item);
		current = tab.item.getParent();
		tab.editor.getControl().setFocus();
	}

	public List<EditorTab> tabs()
	{
		List<EditorTab> list = new ArrayList<EditorTab>();
		for(CTabFolder folder : folders())
		{
			for(CTabItem item : folder.getItems())
			{
				list.add((EditorTab)item.getData());
			}
		}
		return list;
	}

	public void closeActive()
	{
		CTabFolder folder = currentFolder();
		CTabItem item = folder.getSelection();
		if(item != null && confirmClose((EditorTab)item.getData()))
		{
			item.dispose();
			removeIfEmpty(folder);
		}
	}

	public void closeAll()
	{
		for(EditorTab tab : tabs())
		{
			tab.item.dispose();
		}
		List<CTabFolder> all = folders();
		for(int i = all.size() - 1; i > 0; i--)
		{
			removeIfEmpty(all.get(i));
		}
	}

	public boolean saveAll()
	{
		for(EditorTab tab : tabs())
		{
			if(tab.isDirty())
			{
				try
				{
					tab.save();
				}
				catch(IOException e)
				{
					e.printStackTrace();
					ConfirmDialog.message(shell, "Error", "Cannot save " + tab.file + ": " + e.getMessage(), SWT.ICON_ERROR);
					return false;
				}
			}
		}
		return true;
	}

	// Reloads the tabs without unsaved changes whose files changed on disk; tabs of deleted files are left alone.
	public void reloadClean()
	{
		for(EditorTab tab : tabs())
		{
			if(!tab.isDirty() && tab.file.isFile())
			{
				try
				{
					tab.reload();
				}
				catch(IOException e)
				{
					// the file stays as it was in the editor
				}
			}
		}
	}

	public boolean anyDirty()
	{
		for(EditorTab tab : tabs())
		{
			if(tab.isDirty())
			{
				return true;
			}
		}
		return false;
	}

	boolean confirmClose(EditorTab tab)
	{
		if(!tab.isDirty())
		{
			return true;
		}
		int answer = ConfirmDialog.ask(shell, "Save Changes", "Save changes to " + tab.file.getName() + "?");
		if(answer == SWT.CANCEL)
		{
			return false;
		}
		if(answer == SWT.YES)
		{
			try
			{
				tab.save();
			}
			catch(IOException e)
			{
				return false;
			}
		}
		return true;
	}

	public void setProblems(List<Problem> list)
	{
		problems = list;
		for(EditorTab tab : tabs())
		{
			tab.editor.setProblems(problemsFor(tab.file));
		}
		if(problemsHook != null)
		{
			problemsHook.accept(list);
		}
	}

	List<Problem> problemsFor(File file)
	{
		List<Problem> result = new ArrayList<Problem>();
		File wanted = canonical(file);
		for(Problem p : problems)
		{
			if(!p.file.isEmpty() && canonical(new File(p.file)).equals(wanted))
			{
				result.add(p);
			}
		}
		return result;
	}
}
