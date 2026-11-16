package javapad.ui;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Control;

// The tabs of the bottom panel. They can be closed, and show() brings a closed one back at its original place.
class BottomTabs
{
	static class Tab
	{
		final String name;
		final Control control;
		String text;
		// null while the tab is closed
		CTabItem item;

		Tab(String name, String text, Control control)
		{
			this.name = name;
			this.text = text;
			this.control = control;
		}
	}

	private final CTabFolder folder;
	// in display order
	private final List<Tab> tabs = new ArrayList<Tab>();

	BottomTabs(CTabFolder folder)
	{
		this.folder = folder;
		folder.addCTabFolder2Listener(new CTabFolder2Adapter()
		{
			public void close(CTabFolderEvent e)
			{
				Tab tab = tabOf(e.item);
				if(tab != null)
				{
					// the folder disposes the item; the control stays in the folder for later
					tab.item = null;
					tab.control.setVisible(false);
				}
			}
		});
	}

	// Adds a tab after the existing ones, open or closed.
	Tab add(String name, String text, Control control, boolean open)
	{
		Tab tab = new Tab(name, text, control);
		tabs.add(tab);
		if(open)
		{
			open(tab);
		}
		else
		{
			control.setVisible(false);
		}
		return tab;
	}

	Tab tabOf(Object item)
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

	Tab named(String name)
	{
		for(Tab t : tabs)
		{
			if(t.name.equals(name))
			{
				return t;
			}
		}
		return null;
	}

	boolean isOpen(Tab tab)
	{
		return tab.item != null && !tab.item.isDisposed();
	}

	// the open tab that is showing, or null
	Tab selected()
	{
		return tabOf(folder.getSelection());
	}

	// opens the tab if it was closed, in its original place among the others
	void open(Tab tab)
	{
		if(isOpen(tab))
		{
			return;
		}
		int index = 0;
		for(Tab t : tabs)
		{
			if(t == tab)
			{
				break;
			}
			if(isOpen(t))
			{
				index++;
			}
		}
		tab.item = new CTabItem(folder, SWT.CLOSE, index);
		tab.item.setText(tab.text);
		tab.item.setControl(tab.control);
	}

	// opens the tab if needed and makes it the one showing
	void show(Tab tab)
	{
		open(tab);
		folder.setSelection(tab.item);
	}

	void setText(Tab tab, String text)
	{
		tab.text = text;
		if(isOpen(tab))
		{
			tab.item.setText(text);
		}
	}
}
