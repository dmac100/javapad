package javapad.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javapad.model.Problem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
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

	public EditorTabs(Composite parent, Theme theme)
	{
		this.theme = theme;
		this.shell = parent.getShell();
		folder = new CTabFolder(parent, SWT.BORDER);
		folder.addCTabFolder2Listener(new org.eclipse.swt.custom.CTabFolder2Adapter()
		{
			public void close(org.eclipse.swt.custom.CTabFolderEvent e)
			{
				e.doit = confirmClose((EditorTab)((CTabItem)e.item).getData());
			}
		});
	}

	public void setCompileHook(Runnable hook)
	{
		compileHook = hook;
	}

	public CTabFolder getControl()
	{
		return folder;
	}

	public EditorTab open(File file)
	{
		for(CTabItem item : folder.getItems())
		{
			EditorTab tab = (EditorTab)item.getData();
			if(tab.file.equals(file))
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
		for(Problem p : problems)
		{
			if(p.file.equals(file.getAbsolutePath()))
			{
				result.add(p);
			}
		}
		return result;
	}
}
