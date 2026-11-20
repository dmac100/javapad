package javapad.ui.editor;

import javapad.ui.MainWindow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

// The editor's right-click menu.
public class EditorMenu {
	private final MainWindow w;

	public EditorMenu(MainWindow window) {
		this.w = window;
	}

	MenuItem contextItem(Menu menu, String text, Runnable action) {
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(text);
		item.addListener(SWT.Selection, e -> action.run());
		return item;
	}

	// the git actions that make sense for the file of this editor
	void addGit(Menu parent, EditorTab tab) {
		MenuItem cascade = new MenuItem(parent, SWT.CASCADE);
		cascade.setText("Git");
		Menu menu = new Menu(parent);
		cascade.setMenu(menu);
		List<MenuItem> items = new ArrayList<MenuItem>();
		items.add(contextItem(menu, "Compare with HEAD", () -> w.git.compareWithHead(tab.file)));
		items.add(contextItem(menu, "Compare with Previous Revision", () -> w.git.compareWithPrevious(tab.file)));
		items.add(contextItem(menu, "Show in History", () -> w.git.showInHistory(tab.file)));
		new MenuItem(menu, SWT.SEPARATOR);
		items.add(contextItem(menu, "Add to Index", () -> w.git.addToIndex(tab.file)));
		items.add(contextItem(menu, "Untrack", () -> w.git.untrack(tab.file)));
		items.add(contextItem(menu, "Ignore", () -> w.git.ignore(tab.file)));
		MenuItem assume = new MenuItem(menu, SWT.CHECK);
		assume.setText("Assume Unchanged");
		assume.addListener(SWT.Selection, e -> w.git.toggleAssumeUnchanged(tab.file));
		items.add(assume);
		new MenuItem(menu, SWT.SEPARATOR);
		items.add(contextItem(menu, "Replace with Head Revision", () -> w.git.replaceWith(tab.file, true, () -> reload(tab))));
		items.add(contextItem(menu, "Replace with Previous Revision", () -> w.git.replaceWith(tab.file, false, () -> reload(tab))));
		parent.addListener(SWT.Show, e -> {
			boolean on = w.git.pathOf(tab.file) != null;
			cascade.setEnabled(on);
			for(MenuItem item : items) {
				item.setEnabled(on);
			}
			assume.setSelection(on && w.git.assumedUnchanged(tab.file));
		});
	}

	// shows what is now on disk, dropping unsaved edits (the caller has asked for that)
	void reload(EditorTab tab) {
		try {
			tab.reload();
		} catch(IOException e) {
			e.printStackTrace();
			w.error("Cannot reload " + tab.file.getName() + ": " + e.getMessage());
		}
	}

	// The editor's right-click menu: editing, w.navigation and import features with their shortcuts.
	public void attach(EditorTab tab) {
		StyledText t = tab.editor.getControl();
		boolean java = tab.file.getName().endsWith(".java");
		Menu menu = new Menu(t);
		MenuItem cut = contextItem(menu, "Cut\tCtrl+X", () -> t.cut());
		MenuItem copy = contextItem(menu, "Copy\tCtrl+C", () -> t.copy());
		contextItem(menu, "Paste\tCtrl+V", () -> t.paste());
		contextItem(menu, "Select All\tCtrl+A", () -> t.selectAll());
		contextItem(menu, "Undo\tCtrl+Z", () -> tab.editor.undo());
		contextItem(menu, "Redo\tCtrl+Y", () -> tab.editor.redo());
		new MenuItem(menu, SWT.SEPARATOR);
		List<MenuItem> javaItems = new ArrayList<MenuItem>();
		contextItem(menu, "Show in Sidebar\tCtrl+Alt+W", () -> w.showInSidebar());
		javaItems.add(contextItem(menu, "Open Declaration\tF3", () -> w.navigation.openDeclaration(tab, t.getCaretOffset())));
		javaItems.add(contextItem(menu, "Open Implementation\tCtrl+T", () -> w.navigation.openImplementation(tab, t.getCaretOffset())));
		javaItems.add(contextItem(menu, "Call Hierarchy\tCtrl+Alt+H", () -> w.navigation.callHierarchy(tab, t.getCaretOffset())));
		new MenuItem(menu, SWT.SEPARATOR);
		javaItems.add(contextItem(menu, "Content Assist\tCtrl+Space", () -> tab.editor.triggerCompletion(true)));
		javaItems.add(contextItem(menu, "Quick Fix\tCtrl+1", () -> w.importActions.quickFix(tab)));
		javaItems.add(contextItem(menu, "Add Import\tCtrl+Shift+M", () -> w.importActions.addImport(tab)));
		javaItems.add(contextItem(menu, "Format\tCtrl+Shift+F", () -> w.format(tab)));
		javaItems.add(contextItem(menu, "Organize Imports\tCtrl+Shift+O", () -> w.importActions.organize(tab)));
		new MenuItem(menu, SWT.SEPARATOR);
		javaItems.add(contextItem(menu, "Run Test at Cursor\tCtrl+Alt+R", () -> w.run.runTestAtCursor()));
		javaItems.add(contextItem(menu, "Go to Matching Bracket\tCtrl+Shift+P", () -> tab.editor.goToMatchingBracket()));
		new MenuItem(menu, SWT.SEPARATOR);
		addGit(menu, tab);
		t.setMenu(menu);
		// a right click puts the caret where you clicked, unless that is inside the selection
		t.addListener(SWT.MenuDetect, e -> {
			try {
				int offset = t.getOffsetAtPoint(t.toControl(e.x, e.y));
				Point sel = t.getSelection();
				if(offset < sel.x || offset > sel.y) {
					t.setCaretOffset(offset);
				}
			} catch(IllegalArgumentException ex) {
				// the click was not over any text
			}
		});
		menu.addListener(SWT.Show, e -> {
			cut.setEnabled(t.getSelectionCount() > 0);
			copy.setEnabled(t.getSelectionCount() > 0);
			for(MenuItem item : javaItems) {
				item.setEnabled(java);
			}
		});
	}

}
