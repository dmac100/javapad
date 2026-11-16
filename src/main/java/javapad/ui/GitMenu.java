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

	GitMenu(MainWindow window)
	{
		this.w = window;
	}

	// Adds the cascade to the menu.
	void attach(Menu parent)
	{
		MenuItem cascade = new MenuItem(parent, SWT.CASCADE);
		cascade.setText("Git");
		Menu menu = new Menu(parent);
		cascade.setMenu(menu);
		init = item(menu, "Init", () -> w.git.init());
		new MenuItem(menu, SWT.SEPARATOR);
		Menu stashes = cascade(menu, "Stashes");
		stashes.addListener(SWT.Show, e -> fillStashes(stashes));
		needRepo.add(stashes.getParentItem());
		needRepo.add(item(menu, "Commit...", () -> w.git.commit()));
		new MenuItem(menu, SWT.SEPARATOR);
		needRepo.add(item(menu, "Push to Origin", () -> w.git.pushOrigin()));
		needRepo.add(item(menu, "Fetch from Origin", () -> w.git.fetchOrigin()));
		needRepo.add(item(menu, "Push Branch...", () -> w.git.pushBranch()));
		needRepo.add(item(menu, "Pull", () -> w.git.pull()));
		new MenuItem(menu, SWT.SEPARATOR);
		Menu switchTo = cascade(menu, "Switch to");
		switchTo.addListener(SWT.Show, e -> fillBranches(switchTo));
		needRepo.add(switchTo.getParentItem());
		needRepo.add(item(menu, "Merge...", () -> w.git.merge()));
		needRepo.add(item(menu, "Rebase...", () -> w.git.rebase()));
		needRepo.add(item(menu, "Reset...", () -> w.git.reset()));
		new MenuItem(menu, SWT.SEPARATOR);
		needPath.add(item(menu, "Add to Index", () -> w.git.addToIndex()));
		needPath.add(item(menu, "Untrack", () -> w.git.untrack()));
		needPath.add(item(menu, "Ignore", () -> w.git.ignore()));
		Menu replace = cascade(menu, "Replace with");
		item(replace, "Head Revision", () -> w.git.replaceWith(true));
		item(replace, "Previous Revision", () -> w.git.replaceWith(false));
		needPath.add(replace.getParentItem());
		new MenuItem(menu, SWT.SEPARATOR);
		needPath.add(item(menu, "Show in History", () -> w.git.showInHistory()));
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
		});
	}

	static MenuItem item(Menu menu, String text, Runnable action)
	{
		MenuItem mi = new MenuItem(menu, SWT.PUSH);
		mi.setText(text);
		mi.addListener(SWT.Selection, e -> action.run());
		return mi;
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
