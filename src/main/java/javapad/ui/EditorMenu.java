package javapad.ui;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

// The editor's right-click menu.
class EditorMenu
{
	private final MainWindow w;

	EditorMenu(MainWindow window)
	{
		this.w = window;
	}

	MenuItem contextItem(Menu menu, String text, Runnable action)
	{
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(text);
		item.addListener(SWT.Selection, e -> action.run());
		return item;
	}

	// The editor's right-click menu: editing, w.navigation and import features with their shortcuts.
	void attach(EditorTab tab)
	{
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
		t.setMenu(menu);
		// a right click puts the caret where you clicked, unless that is inside the selection
		t.addListener(SWT.MenuDetect, e ->
		{
			try
			{
				int offset = t.getOffsetAtPoint(t.toControl(e.x, e.y));
				Point sel = t.getSelection();
				if(offset < sel.x || offset > sel.y)
				{
					t.setCaretOffset(offset);
				}
			}
			catch(IllegalArgumentException ex)
			{
				// the click was not over any text
			}
		});
		menu.addListener(SWT.Show, e ->
		{
			cut.setEnabled(t.getSelectionCount() > 0);
			copy.setEnabled(t.getSelectionCount() > 0);
			for(MenuItem item : javaItems)
			{
				item.setEnabled(java);
			}
		});
	}

}
