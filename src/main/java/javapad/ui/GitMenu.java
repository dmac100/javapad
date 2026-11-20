package javapad.ui;

import java.util.ArrayList;
import java.util.List;
import javapad.git.Stash;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

// The "Git" entry of the sidebar's right-click menu.
class GitMenu
{
	private final MainWindow w;
	private final List<MenuItem> needRepo = new ArrayList<MenuItem>();
	private final List<MenuItem> needPath = new ArrayList<MenuItem>();
	private MenuItem init;
	private MenuItem assume;
	private final CommandList commands;

	// commands: every item is also added to this list (the Ctrl+3 dialog)
	GitMenu(MainWindow window, CommandList commands)
	{
		this.w = window;
		this.commands = commands;
	}

	// Adds a "Git" cascade to a pop-up menu.
	void attach(Menu parent)
	{
		MenuItem cascade = new MenuItem(parent, SWT.CASCADE);
		cascade.setText("Git");
		Menu menu = new Menu(parent);
		cascade.setMenu(menu);
		build(menu);
	}

	// Fills a menu with the git items.
	void build(Menu menu)
	{
		init = entry(menu, "Git", "Init", () -> w.git.init());
		new MenuItem(menu, SWT.SEPARATOR);
		Menu stashes = cascade(menu, "Stashes");
		commands.add("Git > Stashes > Stash Changes...", () -> w.git.stash());
		stashes.addListener(SWT.Show, e -> fillStashes(stashes));
		needRepo.add(stashes.getParentItem());
		needRepo.add(entry(menu, "Git", "Commit...", () -> w.git.commit()));
		new MenuItem(menu, SWT.SEPARATOR);
		needRepo.add(entry(menu, "Git", "Push to Origin", () -> w.git.pushOrigin()));
		needRepo.add(entry(menu, "Git", "Fetch from Origin", () -> w.git.fetchOrigin()));
		needRepo.add(entry(menu, "Git", "Push Branch...", () -> w.git.pushBranch()));
		needRepo.add(entry(menu, "Git", "Pull", () -> w.git.pull()));
		new MenuItem(menu, SWT.SEPARATOR);
		Menu switchTo = cascade(menu, "Switch to");
		commands.add("Git > Switch to > New Branch...", () -> w.git.switchToNew());
		switchTo.addListener(SWT.Show, e -> fillBranches(switchTo));
		needRepo.add(switchTo.getParentItem());
		needRepo.add(entry(menu, "Git", "Merge...", () -> w.git.merge()));
		needRepo.add(entry(menu, "Git", "Rebase...", () -> w.git.rebase()));
		needRepo.add(entry(menu, "Git", "Reset...", () -> w.git.reset()));
		new MenuItem(menu, SWT.SEPARATOR);
		needPath.add(entry(menu, "Git", "Add to Index", () -> w.git.addToIndex()));
		needPath.add(entry(menu, "Git", "Untrack", () -> w.git.untrack()));
		needPath.add(entry(menu, "Git", "Ignore", () -> w.git.ignore()));
		assume = new MenuItem(menu, SWT.CHECK);
		assume.setText("Assume Unchanged");
		assume.addListener(SWT.Selection, e -> w.git.toggleAssumeUnchanged());
		commands.add("Git > Assume Unchanged", () -> w.git.toggleAssumeUnchanged());
		needPath.add(assume);
		Menu replace = cascade(menu, "Replace with");
		entry(replace, "Git > Replace with", "Head Revision", () -> w.git.replaceWith(true));
		entry(replace, "Git > Replace with", "Previous Revision", () -> w.git.replaceWith(false));
		needPath.add(replace.getParentItem());
		new MenuItem(menu, SWT.SEPARATOR);
		needPath.add(entry(menu, "Git", "Compare with Previous Revision", () -> w.git.comparePreviousOfSelected()));
		needPath.add(entry(menu, "Git", "Show in History", () -> w.git.showInHistory()));
		menu.addListener(SWT.Show, e ->
		{
			boolean repo = w.git.repo() != null;
			boolean path = repo && w.git.selectedPath() != null;
			init.setEnabled(!repo && w.project() != null);
			for(MenuItem mi : needRepo)
			{
				mi.setEnabled(repo);
			}
			for(MenuItem mi : needPath)
			{
				mi.setEnabled(path);
			}
			assume.setSelection(path && w.git.selectedAssumedUnchanged());
		});
	}

	// a menu item that is also listed in the command dialog as "path > text"
	MenuItem entry(Menu menu, String path, String text, Runnable action)
	{
		commands.add(path + " > " + text, action);
		return item(menu, text, action);
	}

	static MenuItem item(Menu menu, String text, Runnable action)
	{
		MenuItem mi = new MenuItem(menu, SWT.PUSH);
		mi.setText(text);
		mi.addListener(SWT.Selection, e -> action.run());
		return mi;
	}

	// The commands that depend on the repository's current branches and stashes; call it each time the command dialog is built.
	void addDynamic(CommandList list)
	{
		if(w.git.repo() == null)
		{
			return;
		}
		for(String b : w.git.repo().branches())
		{
			list.add("Git > Switch to > " + b, () -> w.git.switchTo(b));
		}
		for(Stash s : w.git.stashes())
		{
			list.add("Git > Stashes > " + s + " > Apply", () -> w.git.stashApply(s.ref));
			list.add("Git > Stashes > " + s + " > Pop", () -> w.git.stashPop(s.ref));
			list.add("Git > Stashes > " + s + " > Drop...", () -> w.git.stashDrop(s.ref));
		}
	}

	static Menu cascade(Menu menu, String text)
	{
		MenuItem mi = new MenuItem(menu, SWT.CASCADE);
		mi.setText(text);
		Menu sub = new Menu(menu);
		mi.setMenu(sub);
		return sub;
	}

	static void clear(Menu menu)
	{
		for(MenuItem mi : menu.getItems())
		{
			mi.dispose();
		}
	}

	void fillStashes(Menu menu)
	{
		clear(menu);
		item(menu, "Stash Changes...", () -> w.git.stash());
		List<Stash> list = w.git.stashes();
		if(!list.isEmpty())
		{
			new MenuItem(menu, SWT.SEPARATOR);
		}
		for(Stash s : list)
		{
			Menu one = cascade(menu, s.toString().replace("&", "&&"));
			item(one, "Apply", () -> w.git.stashApply(s.ref));
			item(one, "Pop", () -> w.git.stashPop(s.ref));
			item(one, "Drop...", () -> w.git.stashDrop(s.ref));
		}
	}

	void fillBranches(Menu menu)
	{
		clear(menu);
		item(menu, "New Branch...", () -> w.git.switchToNew());
		if(w.git.repo() == null)
		{
			return;
		}
		String current = w.git.status() == null ? "" : w.git.status().branch;
		List<String> branches = w.git.repo().branches();
		if(!branches.isEmpty())
		{
			new MenuItem(menu, SWT.SEPARATOR);
		}
		for(String b : branches)
		{
			MenuItem mi = new MenuItem(menu, SWT.RADIO);
			mi.setText(b.replace("&", "&&"));
			mi.setSelection(b.equals(current));
			mi.addListener(SWT.Selection, e ->
			{
				if(mi.getSelection() && !b.equals(current))
				{
					w.git.switchTo(b);
				}
			});
		}
	}
}
