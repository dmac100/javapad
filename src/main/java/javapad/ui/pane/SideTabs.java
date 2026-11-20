package javapad.ui.pane;

import javapad.ui.editor.TabDrag;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

// The tabs of the side panel. A folder cannot move its items, so dragging a tab makes a new item in the new place; a Tab stays the same object.
public class SideTabs implements TabDrag.Host
{
	private final CTabFolder folder;
	private final List<Tab> tabs = new ArrayList<Tab>();

	public static class Tab
	{
		public final Control control;
		public String text;
		public CTabItem item;

		public Tab(String text, Control control)
		{
			this.text = text;
			this.control = control;
		}

		public void setText(String text)
		{
			this.text = text;
			item.setText(text);
		}

		public boolean selected()
		{
			return item.getParent().getSelection() == item;
		}

		public void select()
		{
			item.getParent().setSelection(item);
		}
	}

	public SideTabs(CTabFolder folder, Shell shell)
	{
		this.folder = folder;
		new TabDrag(shell, this).attach(folder);
	}

	public Tab add(String text, Control control)
	{
		Tab tab = new Tab(text, control);
		tab.item = new CTabItem(folder, SWT.NONE);
		tab.item.setText(text);
		tab.item.setControl(control);
		tabs.add(tab);
		return tab;
	}

	Tab tabOf(CTabItem item)
	{
		for(Tab t : tabs)
		{
			if(t.item == item)
			{
				return t;
			}
		}
		return null;
	}

	// Moves a tab so that it is at the index (as numbered once it has left its old place).
	void move(Tab tab, int index)
	{
		boolean selected = tab.selected();
		tab.item.dispose();
		tab.item = new CTabItem(folder, SWT.NONE, index);
		tab.item.setText(tab.text);
		tab.item.setControl(tab.control);
		if(selected)
		{
			folder.setSelection(tab.item);
		}
	}

	public List<CTabFolder> folders()
	{
		List<CTabFolder> list = new ArrayList<CTabFolder>();
		list.add(folder);
		return list;
	}

	public boolean splits()
	{
		return false;
	}

	public void drop(CTabItem item, CTabFolder target, int zone, int index)
	{
		Tab tab = tabOf(item);
		if(tab != null && zone == TabDrag.INSERT)
		{
			move(tab, index);
		}
	}
}
