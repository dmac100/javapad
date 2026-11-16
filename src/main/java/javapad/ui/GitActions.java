package javapad.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import javapad.git.Commit;
import javapad.git.GitRepo;
import javapad.git.GitResult;
import javapad.git.GitStatus;
import javapad.model.EclipseProject;

// The project's git repository and the git commands of the sidebar menu. Git runs on a worker thread; the UI is updated on the UI thread.
class GitActions
{
	private final MainWindow w;
	private GitRepo repo;
	private GitStatus status;

	GitActions(MainWindow window)
	{
		this.w = window;
	}

	GitRepo repo()
	{
		return repo;
	}

	GitStatus status()
	{
		return status;
	}

	// ---- keeping track of the repository

	// Looks for the repository of the current project.
	void projectChanged()
	{
		EclipseProject project = w.project();
		repo = null;
		status = null;
		decorate();
		if(project == null)
		{
			return;
		}
		File root = project.root;
		background(() ->
		{
			GitRepo found = GitRepo.find(root);
			GitStatus s = found == null ? null : found.status(false);
			return () ->
			{
				repo = found;
				status = s;
				decorate();
				w.history.refresh();
			};
		});
	}

	// Re-reads the status and updates the question-mark boxes in the trees.
	void refresh()
	{
		if(repo == null)
		{
			return;
		}
		GitRepo r = repo;
		background(() ->
		{
			GitStatus s = r.status(false);
			return () ->
			{
				if(r == repo)
				{
					status = s;
					decorate();
				}
			};
		});
	}

	void decorate()
	{
		if(repo == null || status == null)
		{
			w.projectTree.setUntrackedTest(null);
			w.sourceTree.setUntrackedTest(null);
			return;
		}
		GitRepo r = repo;
		GitStatus s = status;
		java.util.function.Predicate<File> test = f ->
		{
			String rel = r.relative(f);
			return rel != null && !rel.isEmpty() && s.untracked(rel);
		};
		w.projectTree.setUntrackedTest(test);
		w.sourceTree.setUntrackedTest(test);
	}

	// After a command: the files on disk may have changed, so open editors and trees are brought up to date.
	void changed(boolean files)
	{
		if(files)
		{
			w.editors.reloadClean();
			w.refreshTrees();
		}
		refresh();
		w.history.refresh();
	}

	// ---- running commands

	interface Work
	{
		Runnable run();
	}

	// Runs work on a thread; the Runnable it returns runs on the UI thread.
	void background(Work work)
	{
		Thread t = new Thread(() ->
		{
			Runnable done = work.run();
			w.display.asyncExec(() ->
			{
				if(!w.shell.isDisposed())
				{
					done.run();
				}
			});
		}, "git");
		t.setDaemon(true);
		t.start();
	}

	boolean ready()
	{
		if(repo == null)
		{
			w.error("This project is not in a git repository. Use Git > Init in the sidebar menu.");
			return false;
		}
		return true;
	}

	// Runs a git command and reports a failure; files: whether it can change files on disk.
	void run(String title, Function<GitRepo, GitResult> command, boolean files)
	{
		run(title, command, files, null);
	}

	void run(String title, Function<GitRepo, GitResult> command, boolean files, Runnable onSuccess)
	{
		if(!ready())
		{
			return;
		}
		GitRepo r = repo;
		background(() ->
		{
			GitResult result = command.apply(r);
			return () ->
			{
				if(result.ok())
				{
					report(title, result);
					if(onSuccess != null)
					{
						onSuccess.run();
					}
				}
				else
				{
					w.error(title + " failed:\n\n" + result.message());
				}
				changed(files);
			};
		});
	}

	void report(String title, GitResult result)
	{
		String text = result.message();
		if(!text.isEmpty())
		{
			w.console.append("git " + title.toLowerCase() + ": " + text + "\n", false);
		}
	}

	// ---- the sidebar menu

	// the selected file or folder in the sidebar, relative to the repository; "." for the repository itself
	String selectedPath()
	{
		return pathOf(selectedFile());
	}

	File selectedFile()
	{
		return w.activeTree().selected();
	}

	void init()
	{
		EclipseProject project = w.project();
		if(project == null || repo != null)
		{
			return;
		}
		File root = project.root;
		background(() ->
		{
			GitResult r = GitRepo.init(root);
			return () ->
			{
				if(!r.ok())
				{
					w.error(r.message());
				}
				projectChanged();
			};
		});
	}

	void commit()
	{
		if(!ready())
		{
			return;
		}
		CommitDialog.Request req = new CommitDialog(w.shell, repo, repo.status(true), (parent, change) -> compareWithHead(parent, change)).open();
		if(req == null)
		{
			return;
		}
		run("Commit", r -> r.commit(req.message, req.author, req.committer, req.amend, req.paths), false, req.push ? () -> pushOrigin() : null);
	}

	// Shows a changed file next to its last committed version; parent is the dialog it was asked from.
	void compareWithHead(org.eclipse.swt.widgets.Shell parent, javapad.git.FileChange change)
	{
		GitRepo r = repo;
		CompareWindow.Entry entry = new CompareWindow.Entry(change.path, change.label(), null);
		CompareWindow.open(w, parent, change.path + ": last commit and working tree", "Last commit (HEAD)", "Working tree", Arrays.asList(entry),
			e -> new String[] {HistoryActions.orEmpty(r.show("HEAD", e.path)), r.working(e.path)});
	}

	void pushOrigin()
	{
		run("Push", r -> r.push("origin", "HEAD"), false);
	}

	void fetchOrigin()
	{
		run("Fetch", r -> r.fetch("origin"), false);
	}

	void pull()
	{
		run("Pull", r -> r.pull(), true);
	}

	void pushBranch()
	{
		String branch = chooseBranch("Push which branch?", false, true);
		if(branch != null)
		{
			run("Push", r -> r.push("origin", branch), false);
		}
	}

	// Asks for a branch; the current one first. remote: include remote branches; currentOnTop: list the current branch first.
	String chooseBranch(String title, boolean remote, boolean currentOnTop)
	{
		if(!ready())
		{
			return null;
		}
		List<String> list = repo.branches();
		String current = status == null ? "" : status.branch;
		if(currentOnTop && list.remove(current))
		{
			list.add(0, current);
		}
		if(remote)
		{
			list.addAll(repo.remoteBranches());
		}
		if(list.isEmpty())
		{
			w.error("There are no branches yet.");
			return null;
		}
		return ChoiceDialog.choose(w.shell, title, list);
	}

	void switchTo(String branch)
	{
		run("Switch", r -> r.checkout(branch), true);
	}

	void switchToNew()
	{
		if(!ready())
		{
			return;
		}
		String name = Prompt.ask(w.shell, "New Branch", "Name of the new branch (created from the current commit):", "");
		if(name != null && !name.trim().isEmpty())
		{
			run("Switch", r -> r.switchNew(name.trim(), null), true);
		}
	}

	// the branches other than the current one, then the remote branches
	String chooseOther(String title)
	{
		if(!ready())
		{
			return null;
		}
		List<String> list = repo.branches();
		list.remove(status == null ? "" : status.branch);
		list.addAll(repo.remoteBranches());
		if(list.isEmpty())
		{
			w.error("There is no other branch.");
			return null;
		}
		return ChoiceDialog.choose(w.shell, title, list);
	}

	void merge()
	{
		String branch = chooseOther("Merge which branch into the current one?");
		if(branch != null)
		{
			run("Merge", r -> r.merge(branch), true);
		}
	}

	void rebase()
	{
		String branch = chooseOther("Rebase the current branch onto:");
		if(branch != null)
		{
			run("Rebase", r -> r.rebase(branch), true);
		}
	}

	void reset()
	{
		if(!ready())
		{
			return;
		}
		String rev = Prompt.ask(w.shell, "Reset", "Reset the current branch to (a commit, branch or HEAD~1):", "HEAD");
		if(rev != null && !rev.trim().isEmpty())
		{
			reset(rev.trim());
		}
	}

	// asks for soft, mixed or hard and resets the current branch to the revision
	void reset(String rev)
	{
		String mode = ChoiceDialog.choose(w.shell, "Reset to " + rev, Arrays.asList("mixed (keep files, unstage changes)", "soft (keep files and the index)", "hard (discard all changes)"));
		if(mode == null)
		{
			return;
		}
		String kind = mode.substring(0, mode.indexOf(' '));
		if(kind.equals("hard") && !ConfirmDialog.confirm(w.shell, "Reset", "Discard all uncommitted changes and reset to " + rev + "? This cannot be undone."))
		{
			return;
		}
		run("Reset", r -> r.reset(kind, rev), true);
	}

	// the path relative to the repository, "." for the repository itself, null if there is no repository or the file is outside it
	String pathOf(File f)
	{
		if(f == null || repo == null)
		{
			return null;
		}
		String rel = repo.relative(f);
		return rel == null ? null : rel.isEmpty() ? "." : rel;
	}

	void addToIndex()
	{
		addToIndex(selectedFile());
	}

	void addToIndex(File f)
	{
		String path = pathOf(f);
		if(path != null)
		{
			run("Add", r -> r.add(Arrays.asList(path)), false);
		}
	}

	void untrack()
	{
		untrack(selectedFile());
	}

	void untrack(File f)
	{
		String path = pathOf(f);
		if(path != null && ConfirmDialog.confirm(w.shell, "Untrack", "Stop tracking " + path + "? The files stay on disk."))
		{
			run("Untrack", r -> r.untrack(Arrays.asList(path)), false);
		}
	}

	void ignore()
	{
		ignore(selectedFile());
	}

	void ignore(File f)
	{
		String path = pathOf(f);
		if(path != null && !path.equals("."))
		{
			run("Ignore", r -> r.ignore(path, f.isDirectory()), false);
		}
	}

	// head: the last commit; otherwise the version before the last change to the file
	void replaceWith(boolean head)
	{
		replaceWith(selectedFile(), head, null);
	}

	// after: runs when the file has been replaced (an editor showing it can reload then)
	void replaceWith(File f, boolean head, Runnable after)
	{
		String path = pathOf(f);
		if(path == null || path.equals("."))
		{
			return;
		}
		if(!ConfirmDialog.confirm(w.shell, "Replace", "Replace " + path + " with the " + (head ? "head" : "previous") + " revision? Local changes are lost."))
		{
			return;
		}
		run("Replace", r ->
		{
			if(head)
			{
				return r.restore("HEAD", Arrays.asList(path));
			}
			List<Commit> log = r.log(path, 2, false);
			if(log.size() < 2)
			{
				return new GitResult(1, "", "There is no previous revision of " + path + ".");
			}
			return r.restore(log.get(1).hash, Arrays.asList(path));
		}, true, after);
	}

	void showInHistory()
	{
		showInHistory(selectedFile());
	}

	void showInHistory(File f)
	{
		String path = pathOf(f);
		if(path != null)
		{
			w.history.showPath(path.equals(".") ? null : path);
		}
	}

	// The file next to its last committed version.
	void compareWithHead(File f)
	{
		String path = pathOf(f);
		if(path != null && !path.equals("."))
		{
			GitRepo r = repo;
			CompareWindow.open(w, w.shell, path + ": last commit and working tree", "Last commit (HEAD)", "Working tree", Arrays.asList(new CompareWindow.Entry(path, "", null)),
				e -> new String[] {HistoryActions.orEmpty(r.show("HEAD", e.path)), r.working(e.path)});
		}
	}

	void stash()
	{
		if(!ready())
		{
			return;
		}
		String message = Prompt.ask(w.shell, "Stash Changes", "Message (optional):", "");
		if(message != null)
		{
			run("Stash", r -> r.stash(message), true);
		}
	}

	List<javapad.git.Stash> stashes()
	{
		return repo == null ? new ArrayList<javapad.git.Stash>() : repo.stashes();
	}

	void stashApply(String ref)
	{
		run("Stash apply", r -> r.stashApply(ref), true);
	}

	void stashPop(String ref)
	{
		run("Stash pop", r -> r.stashPop(ref), true);
	}

	void stashDrop(String ref)
	{
		if(ConfirmDialog.confirm(w.shell, "Drop Stash", "Delete " + ref + "?"))
		{
			run("Stash drop", r -> r.stashDrop(ref), false);
		}
	}
}
