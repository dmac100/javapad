package javapad.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javapad.model.Problem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

public class EditorTabs
{
	private final CTabFolder folder;
	private final Theme theme;
	private final Shell shell;
	private List<Problem> problems = new ArrayList<Problem>();
	private Runnable compileHook;
	private CodeAssist assist;
	private NavigationActions navigation;
	private Consumer<EditorTab> tabHook;

	public EditorTabs(Composite parent, Theme theme)
	{
		this.theme = theme;
		this.shell = parent.getShell();
		folder = new CTabFolder(parent, SWT.BORDER);
		folder.setTabHeight(folder.getTabHeight() + 3);
		folder.addCTabFolder2Listener(new CTabFolder2Adapter()
		{
			public void close(CTabFolderEvent e)
			{
				e.doit = confirmClose((EditorTab)((CTabItem)e.item).getData());
			}
		});
	}

	// Called once for every tab that is opened, to add menus and the like.
	public void setTabHook(Consumer<EditorTab> hook)
	{
		tabHook = hook;
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

	public CTabFolder getControl()
	{
		return folder;
	}

	static File canonical(File file)
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
		for(CTabItem item : folder.getItems())
		{
			EditorTab tab = (EditorTab)item.getData();
			if(canonical(tab.file).equals(file))
			{
				folder.setSelection(item);
				return tab;
			}
		}
		try
		{
			EditorTab tab = new EditorTab(folder, file, theme);
			tab.editor.setProblems(problemsFor(file));
			tab.editor.setCompileHook(compileHook);
			boolean java = tab.file.getName().endsWith(".java");
			if(assist != null && java)
			{
				tab.editor.setAssist((t, o, d) -> assist.complete(tab.file, t, o, d));
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
			folder.setSelection(tab.item);
			return tab;
		}
		catch(IOException e)
		{
			ConfirmDialog.message(shell, "Error", "Cannot open " + file + ": " + e.getMessage(), SWT.ICON_ERROR);
			return null;
		}
	}

	public EditorTab active()
	{
		CTabItem item = folder.getSelection();
		return item == null ? null : (EditorTab)item.getData();
	}

	public void show(EditorTab tab)
	{
		folder.setSelection(tab.item);
		tab.editor.getControl().setFocus();
	}

	public List<EditorTab> tabs()
	{
		List<EditorTab> list = new ArrayList<EditorTab>();
		for(CTabItem item : folder.getItems())
		{
			list.add((EditorTab)item.getData());
		}
		return list;
	}

	public void closeActive()
	{
		CTabItem item = folder.getSelection();
		if(item != null && confirmClose((EditorTab)item.getData()))
		{
			item.dispose();
		}
	}

	public void closeAll()
	{
		for(CTabItem item : folder.getItems())
		{
			item.dispose();
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
					ConfirmDialog.message(shell, "Error", "Cannot save " + tab.file + ": " + e.getMessage(), SWT.ICON_ERROR);
					return false;
				}
			}
		}
		return true;
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
