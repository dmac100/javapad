package javapad.ui;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

// The menu bar, the toolbar and the sidebar tree menu. Every menu item is also registered in the Ctrl+3 command list.
class MenuBuilder
{
	private final MainWindow w;
	private final CommandList commands = new CommandList();
	private String menuName;
	private ToolItem stopItem;
	private GitMenu gitMenu;
	private final List<MenuItem> accelerated = new ArrayList<MenuItem>();
	private final List<Integer> accelerators = new ArrayList<Integer>();

	MenuBuilder(MainWindow window)
	{
		this.w = window;
	}

	CommandList commands()
	{
		return commands;
	}

	// the git commands that depend on the repository's branches and stashes
	void addGitCommands(CommandList list)
	{
		gitMenu.addDynamic(list);
	}

	void addToolItems(ToolBar bar)
	{
		item(bar, "Open Project", Icons.FOLDER, () -> w.chooseProject());
		item(bar, "Save All", Icons.SAVE, () -> w.editors.saveAll());
		new ToolItem(bar, SWT.SEPARATOR);
		item(bar, "Build", Icons.BUILD, () -> w.run.build(null));
		item(bar, "Run", Icons.RUN, () -> w.run.runActive());
		item(bar, "Run Tests", Icons.TESTS, () -> w.run.runTests());
		stopItem = item(bar, "Stop", Icons.STOP, () -> w.run.stop());
		stopItem.setEnabled(false);
		w.run.setStopItem(stopItem);
	}

	ToolItem item(ToolBar bar, String label, int icon, Runnable action)
	{
		ToolItem b = new ToolItem(bar, SWT.PUSH);
		Image image = Icons.create(w.display, icon);
		b.setImage(image);
		b.setToolTipText(label);
		b.addListener(SWT.Selection, e -> action.run());
		b.addListener(SWT.Dispose, e -> image.dispose());
		return b;
	}

	void menuItem(Menu menu, String label, int accel, Runnable action)
	{
		MenuItem mi = new MenuItem(menu, SWT.PUSH);
		mi.setText(accel == 0 ? label : label + "\t" + accelName(accel));
		if(accel != 0)
		{
			mi.setAccelerator(accel);
			accelerated.add(mi);
			accelerators.add(accel);
		}
		mi.addListener(SWT.Selection, e -> action.run());
		if(menuName != null)
		{
			commands.add(menuName + " > " + label, action);
		}
	}

	// While the terminal has the keyboard the shortcuts are off, so Ctrl+R, Ctrl+W and the like reach the shell.
	void setAccelerators(boolean on)
	{
		for(int i = 0; i < accelerated.size(); i++)
		{
			if(!accelerated.get(i).isDisposed())
			{
				accelerated.get(i).setAccelerator(on ? accelerators.get(i) : 0);
			}
		}
	}

	static String accelName(int accel)
	{
		String s = "";
		if((accel & SWT.MOD1) != 0)
		{
			s += "Ctrl+";
		}
		if((accel & SWT.SHIFT) != 0)
		{
			s += "Shift+";
		}
		if((accel & SWT.ALT) != 0)
		{
			s += "Alt+";
		}
		int key = accel & ~(SWT.MOD1 | SWT.SHIFT | SWT.ALT);
		if(key >= SWT.F1 && key <= SWT.F12)
		{
			return s + "F" + (key - SWT.F1 + 1);
		}
		return s + (char)key;
	}

	void buildMenus()
	{
		Menu bar = new Menu(w.shell, SWT.BAR);
		w.shell.setMenuBar(bar);
		Menu file = submenu(bar, "File");
		menuItem(file, "Open Project...", SWT.MOD1 | 'O', () -> w.chooseProject());
		menuItem(file, "Run Command...", SWT.MOD1 | '3', () -> w.runCommandDialog());
		menuItem(file, "Open File...", SWT.MOD1 | SWT.SHIFT | 'R', () -> w.runFileDialog());
		menuItem(file, "Regenerate Project File...", 0, () -> w.regenerateProject());
		menuItem(file, "Save", SWT.MOD1 | 'S', () -> w.saveActive());
		menuItem(file, "Save All", SWT.MOD1 | SWT.SHIFT | 'S', () -> w.editors.saveAll());
		menuItem(file, "Close Tab", SWT.MOD1 | 'W', () -> w.editors.closeActive());
		menuItem(file, "Refresh", SWT.F5, () -> w.refreshAll());
		menuItem(file, "Exit", 0, () -> w.shell.close());
		Menu edit = submenu(bar, "Edit");
		menuItem(edit, "Find...", SWT.MOD1 | 'F', () -> w.find());
		menuItem(edit, "Find in Files...", SWT.MOD1 | SWT.SHIFT | 'H', () -> w.findInFiles());
		menuItem(edit, "Go to Line...", SWT.MOD1 | 'L', () -> w.gotoLine());
		menuItem(edit, "Content Assist", 0, () -> w.contentAssist());
		menuItem(edit, "Go to Matching Bracket", SWT.MOD1 | SWT.SHIFT | 'P', () -> w.goToMatchingBracket());
		// Alt+Shift+Up and Alt+Shift+Down are handled by the editor itself
		menuItem(edit, "Expand Selection", 0, () -> w.withEditor(t -> t.editor.expandSelection()));
		menuItem(edit, "Shrink Selection", 0, () -> w.withEditor(t -> t.editor.shrinkSelection()));
		Menu source = submenu(bar, "Source");
		menuItem(source, "Organize Imports", SWT.MOD1 | SWT.SHIFT | 'O', () -> w.withEditor(t -> w.importActions.organize(t)));
		menuItem(source, "Format", SWT.MOD1 | SWT.SHIFT | 'F', () -> w.withEditor(t -> w.format(t)));
		menuItem(source, "Convert Spaces to Tabs", 0, () -> w.withEditor(t -> t.editor.convertIndent(true)));
		menuItem(source, "Convert Tabs to Spaces", 0, () -> w.withEditor(t -> t.editor.convertIndent(false)));
		menuItem(source, "Add Import", SWT.MOD1 | SWT.SHIFT | 'M', () -> w.withEditor(t -> w.importActions.addImport(t)));
		menuItem(source, "Quick Fix", SWT.MOD1 | '1', () -> w.withEditor(t -> w.importActions.quickFix(t)));
		Menu navigate = submenu(bar, "Navigate");
		menuItem(navigate, "Show Current File in Sidebar", SWT.MOD1 | SWT.ALT | 'W', () -> w.showInSidebar());
		menuItem(navigate, "Open Declaration", SWT.F3, () -> w.withEditor(t -> w.navigation.openDeclaration(t, t.editor.getControl().getCaretOffset())));
		menuItem(navigate, "Open Implementation", SWT.MOD1 | 'T', () -> w.withEditor(t -> w.navigation.openImplementation(t, t.editor.getControl().getCaretOffset())));
		menuItem(navigate, "Call Hierarchy", SWT.MOD1 | SWT.ALT | 'H', () -> w.withEditor(t -> w.navigation.callHierarchy(t, t.editor.getControl().getCaretOffset())));
		gitMenu = new GitMenu(w, commands);
		gitMenu.build(submenu(bar, "Git"));
		Menu view = submenu(bar, "View");
		MenuItem darkItem = new MenuItem(view, SWT.CHECK);
		darkItem.setText("Dark Mode");
		darkItem.setSelection(w.theme.dark);
		darkItem.addListener(SWT.Selection, e -> w.setDark(darkItem.getSelection()));
		commands.add("View > Dark Mode", () ->
		{
			darkItem.setSelection(!darkItem.getSelection());
			w.setDark(darkItem.getSelection());
		});
		MenuItem toolbarItem = new MenuItem(view, SWT.CHECK);
		toolbarItem.setText("Show Toolbar");
		toolbarItem.setSelection(w.toolbarShown());
		toolbarItem.addListener(SWT.Selection, e -> w.showToolbar(toolbarItem.getSelection()));
		commands.add("View > Show Toolbar", () ->
		{
			toolbarItem.setSelection(!toolbarItem.getSelection());
			w.showToolbar(toolbarItem.getSelection());
		});
		menuItem(view, "Editor Font...", 0, () -> w.chooseFont());
		commands.add("View > Show Project", () -> w.left.setSelection(w.projectTab));
		commands.add("View > Show Sources", () -> w.left.setSelection(w.sourceTab));
		commands.add("View > Show Problems", () -> w.bottomTabs.show(w.problemsTab));
		commands.add("View > Show Console", () -> w.bottomTabs.show(w.consoleTab));
		commands.add("View > Show JUnit", () -> w.junitTab.getParent().setSelection(w.junitTab));
		commands.add("View > Show Call Hierarchy", () -> w.bottomTabs.show(w.callTab));
		commands.add("View > Show Search", () -> w.bottomTabs.show(w.searchTab));
		commands.add("View > Show Terminal", () -> w.showBottomTab("terminal"));
		commands.add("View > Show History", () -> w.showBottomTab("history"));
		Menu runMenu = submenu(bar, "Run");
		menuItem(runMenu, "Build", SWT.MOD1 | 'B', () -> w.run.build(null));
		menuItem(runMenu, "Run Last Launched", SWT.F11, () -> w.run.runLast());
		menuItem(runMenu, "Build and Run", 0, () -> w.run.runActive());
		menuItem(runMenu, "Run", SWT.MOD1 | 'R', () -> w.run.runActive());
		menuItem(runMenu, "Run Main Class...", 0, () -> w.run.runMainClass());
		menuItem(runMenu, "Run Tests", SWT.MOD1 | SWT.F11, () -> w.run.runTests());
		menuItem(runMenu, "Run Test at Cursor", SWT.MOD1 | SWT.ALT | 'R', () -> w.run.runTestAtCursor());
		menuItem(runMenu, "Re-run Failed Tests", 0, () -> w.run.runSpecs(w.junitPane.failedSpecs()));
		menuItem(runMenu, "Program Arguments...", 0, () -> w.editArguments());
		menuItem(runMenu, "Stop", 0, () -> w.run.stop());
		menuName = "Sidebar";
		Menu treeMenu = new Menu(w.shell, SWT.POP_UP);
		w.projectTree.getControl().setMenu(treeMenu);
		w.sourceTree.getControl().setMenu(treeMenu);
		menuItem(treeMenu, "Run", 0, () -> w.runSelected());
		menuItem(treeMenu, "Run Tests", 0, () -> w.run.runTestsIn(w.activeTree().selected()));
		menuItem(treeMenu, "New File...", 0, () -> w.files.newFile());
		menuItem(treeMenu, "New Folder...", 0, () -> w.files.newFolder());
		menuItem(treeMenu, "Delete", 0, () -> w.files.deleteSelected());
		menuItem(treeMenu, "Refresh", 0, () -> w.refreshAll());
		new MenuItem(treeMenu, SWT.SEPARATOR);
		menuName = null;
		new GitMenu(w, commands).attach(treeMenu);
	}

	Menu submenu(Menu bar, String label)
	{
		menuName = label;
		MenuItem mi = new MenuItem(bar, SWT.CASCADE);
		mi.setText(label);
		Menu menu = new Menu(w.shell, SWT.DROP_DOWN);
		mi.setMenu(menu);
		return menu;
	}

}
