package javapad.ui.pane;

import javapad.ui.editor.TabDrag;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

// The tabs of the side panel. They can be closed, and show() brings a closed one back at its original place.
// A folder cannot move its items, so dragging a tab makes a new item in the new place; a Tab stays the same object.
public class SideTabs implements TabDrag.Host {
	private final CTabFolder folder;
	// in display order, closed tabs included
	private final List<Tab> tabs = new ArrayList<Tab>();

	public static class Tab {
		public final Control control;
		public String text;
		// null while the tab is closed
		public CTabItem item;

		public Tab(String text, Control control) {
			this.text = text;
			this.control = control;
		}

		public boolean isOpen() {
			return item != null && !item.isDisposed();
		}

		public void setText(String text) {
			this.text = text;
			if(isOpen()) {
				item.setText(text);
			}
		}

		public boolean selected() {
			return isOpen() && item.getParent().getSelection() == item;
		}

		// selects the tab if it is open; a closed tab stays closed until show() is used
		public void select() {
			if(isOpen()) {
				item.getParent().setSelection(item);
			}
		}
	}

	public SideTabs(CTabFolder folder, Shell shell) {
		this.folder = folder;
		new TabDrag(shell, this).attach(folder);
		folder.addCTabFolder2Listener(new CTabFolder2Adapter() {
			public void close(CTabFolderEvent e) {
				Tab tab = tabOf(e.item);
				if(tab != null) {
					// the folder disposes the item; the control stays in the folder for later
					tab.item = null;
					tab.control.setVisible(false);
				}
			}
		});
	}

	// Adds a tab after the existing ones, open.
	public Tab add(String text, Control control) {
		Tab tab = new Tab(text, control);
		tabs.add(tab);
		open(tab);
		return tab;
	}

	Tab tabOf(Object item) {
		if(item == null) {
			return null;
		}
		for(Tab t : tabs) {
			if(t.item == item) {
				return t;
			}
		}
		return null;
	}

	// the open tab that is showing, or null
	public Tab selected() {
		return tabOf(folder.getSelection());
	}

	// opens the tab if it was closed, in its original place among the others
	void open(Tab tab) {
		if(tab.isOpen()) {
			return;
		}
		int index = 0;
		for(Tab t : tabs) {
			if(t == tab) {
				break;
			}
			if(t.isOpen()) {
				index++;
			}
		}
		tab.item = new CTabItem(folder, SWT.CLOSE, index);
		tab.item.setText(tab.text);
		tab.item.setControl(tab.control);
	}

	public void show(Tab tab) {
		open(tab);
		folder.setSelection(tab.item);
	}

	public List<CTabFolder> folders() {
		List<CTabFolder> list = new ArrayList<CTabFolder>();
		list.add(folder);
		return list;
	}

	public boolean splits() {
		return false;
	}

	// Moves an open tab, so that it is at the index among the open tabs (as numbered once it has left its old place).
	// A closed tab later comes back at this new place.
	public void drop(CTabItem item, CTabFolder target, int zone, int index) {
		Tab tab = tabOf(item);
		if(tab == null || zone != TabDrag.INSERT) {
			return;
		}
		List<Tab> others = new ArrayList<Tab>();
		for(Tab t : tabs) {
			if(t != tab && t.isOpen()) {
				others.add(t);
			}
		}
		int at = Math.max(0, Math.min(index, others.size()));
		tabs.remove(tab);
		int position = tabs.size();
		if(at < others.size()) {
			position = tabs.indexOf(others.get(at));
		} else if(!others.isEmpty()) {
			position = tabs.indexOf(others.get(others.size() - 1)) + 1;
		}
		tabs.add(position, tab);
		boolean selected = folder.getSelection() == item;
		item.dispose();
		tab.item = new CTabItem(folder, SWT.CLOSE, at);
		tab.item.setText(tab.text);
		tab.item.setControl(tab.control);
		if(selected) {
			folder.setSelection(tab.item);
		}
	}
}
