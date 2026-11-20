package javapad.ui.git;

import javapad.ui.MainWindow;
import javapad.ui.claude.Prompt;
import javapad.ui.dialog.ChoiceDialog;
import javapad.ui.dialog.ConfirmDialog;
import javapad.ui.editor.Location;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javapad.git.ChangedFile;
import javapad.git.Commit;
import javapad.git.GitRepo;
import org.eclipse.swt.SWT;

// What the History tab's context menus do with a commit or one of its files.
public class HistoryActions {
	private final MainWindow w;

	HistoryActions(MainWindow window) {
		this.w = window;
	}

	GitRepo repo() {
		return w.git.repo();
	}

	static List<String> branchNames(Commit c) {
		List<String> list = new ArrayList<String>();
		for(Commit.Ref r : c.branches()) {
			if(!r.name.equals("HEAD")) {
				list.add(r.name);
			}
		}
		return list;
	}

	// the local branches at the commit; picks one if there are several, complains if there are none
	String branchAt(Commit c, String title) {
		List<String> names = branchNames(c);
		if(names.isEmpty()) {
			w.error("No branch points at " + c.shortHash() + ".");
			return null;
		}
		return names.size() == 1 ? names.get(0) : ChoiceDialog.choose(w.shell, title, names);
	}

	// ---- commits

	void compareWithWorkingTree(Commit c) {
		GitRepo r = repo();
		w.git.background(() -> {
			List<CompareWindow.Entry> entries = new ArrayList<CompareWindow.Entry>();
			for(ChangedFile f : r.diffFiles(c.hash)) {
				entries.add(new CompareWindow.Entry(f.path, f.label(), f.oldPath));
			}
			return () -> CompareWindow.open(w, w.shell, "Compare " + c.shortHash() + " with working tree", c.shortHash(), "Working tree", entries,
				e -> new String[] {orEmpty(r.show(c.hash, e.oldPath)), r.working(e.path)});
		});
	}

	static String orEmpty(String s) {
		return s == null ? "" : s;
	}

	void checkout(Commit c) {
		List<String> names = branchNames(c);
		if(names.isEmpty()) {
			if(ConfirmDialog.confirm(w.shell, "Checkout", "Check out " + c.shortHash() + " as a detached HEAD?")) {
				w.git.run("Checkout", r -> r.checkout(c.hash), true);
			}
			return;
		}
		List<String> options = new ArrayList<String>(names);
		options.add("detached: " + c.shortHash());
		String pick = options.size() == 2 ? options.get(0) : ChoiceDialog.choose(w.shell, "Check out", options);
		if(pick != null) {
			String target = pick.startsWith("detached: ") ? c.hash : pick;
			w.git.run("Checkout", r -> r.checkout(target), true);
		}
	}

	void pushBranch(Commit c) {
		String branch = branchAt(c, "Push which branch?");
		if(branch != null) {
			w.git.run("Push", r -> r.push("origin", branch), false);
		}
	}

	void createBranch(Commit c) {
		String name = Prompt.ask(w.shell, "Create Branch", "Name of the new branch at " + c.shortHash() + ":", "");
		if(name != null && !name.trim().isEmpty()) {
			w.git.run("Create branch", r -> r.createBranch(name.trim(), c.hash), false);
		}
	}

	void deleteBranch(Commit c) {
		String branch = branchAt(c, "Delete which branch?");
		if(branch == null || !ConfirmDialog.confirm(w.shell, "Delete Branch", "Delete the branch " + branch + "?")) {
			return;
		}
		// git itself explains how to force the delete when the branch is not fully merged
		w.git.run("Delete branch", r -> r.deleteBranch(branch, false), false);
	}

	void renameBranch(Commit c) {
		String branch = branchAt(c, "Rename which branch?");
		if(branch == null) {
			return;
		}
		String name = Prompt.ask(w.shell, "Rename Branch", "New name for " + branch + ":", branch);
		if(name != null && !name.trim().isEmpty() && !name.trim().equals(branch)) {
			w.git.run("Rename branch", r -> r.renameBranch(branch, name.trim()), false);
		}
	}

	void createTag(Commit c) {
		String name = Prompt.ask(w.shell, "Create Tag", "Tag name for " + c.shortHash() + ":", "");
		if(name != null && !name.trim().isEmpty()) {
			w.git.run("Create tag", r -> r.createTag(name.trim(), c.hash), false);
		}
	}

	void cherryPick(Commit c) {
		w.git.run("Cherry-pick", r -> r.cherryPick(c.hash), true);
	}

	void merge(Commit c) {
		if(ConfirmDialog.confirm(w.shell, "Merge", "Merge " + c.shortHash() + " into the current branch?")) {
			w.git.run("Merge", r -> r.merge(c.hash), true);
		}
	}

	void reset(Commit c) {
		w.git.reset(c.hash);
	}

	void revert(Commit c) {
		if(ConfirmDialog.confirm(w.shell, "Revert", "Create a commit that reverts " + c.shortHash() + "?")) {
			w.git.run("Revert", r -> r.revert(c.hash), true);
		}
	}

	// ---- files of a commit

	void openWorkingTree(ChangedFile f) {
		File file = repo().file(f.path);
		if(!file.isFile()) {
			w.error(f.path + " does not exist in the working tree.");
			return;
		}
		w.open(new Location(file, 1, 1));
	}

	void openThisVersion(Commit c, ChangedFile f) {
		openRevision(c.hash, f.path, c.shortHash());
	}

	void openPreviousVersion(Commit c, ChangedFile f) {
		if(c.parents.isEmpty()) {
			w.error(c.shortHash() + " is the first commit; it has no previous version.");
			return;
		}
		String path = f.oldPath != null ? f.oldPath : f.path;
		openRevision(c.hash + "^", path, c.parents.get(0).substring(0, Math.min(8, c.parents.get(0).length())));
	}

	// writes the file as it was at the revision to a temporary file and opens that
	void openRevision(String rev, String path, String label) {
		GitRepo r = repo();
		w.git.background(() -> {
			String content = r.show(rev, path);
			return () -> {
				if(content == null) {
					w.error(path + " does not exist in " + label + ".");
					return;
				}
				try {
					String name = new File(path).getName();
					int dot = name.lastIndexOf('.');
					String versioned = dot < 0 ? name + "@" + label : name.substring(0, dot) + "@" + label + name.substring(dot);
					File dir = new File(System.getProperty("java.io.tmpdir"), "javapad-history/" + label);
					dir.mkdirs();
					File out = new File(dir, versioned);
					Files.write(out.toPath(), content.getBytes(StandardCharsets.UTF_8));
					w.open(new Location(out, 1, 1));
				} catch(IOException e) {
					e.printStackTrace();
					w.error("Cannot write the file: " + e.getMessage());
				}
			};
		});
	}

	void checkoutThisVersion(Commit c, ChangedFile f) {
		if(f.status == 'D') {
			w.error(f.path + " was deleted in " + c.shortHash() + ".");
			return;
		}
		if(ConfirmDialog.confirm(w.shell, "Checkout", "Replace " + f.path + " with its version from " + c.shortHash() + "? Local changes are lost.")) {
			w.git.run("Checkout", r -> r.restore(c.hash, Arrays.asList(f.path)), true);
		}
	}

	void compareWithPrevious(Commit c, ChangedFile f) {
		GitRepo r = repo();
		String before = c.parents.isEmpty() ? "(nothing)" : c.parents.get(0).substring(0, Math.min(8, c.parents.get(0).length()));
		CompareWindow.Entry entry = new CompareWindow.Entry(f.path, f.label(), f.oldPath);
		CompareWindow.open(w, w.shell, f.path + ": " + c.shortHash() + " and the version before", before, c.shortHash(), Arrays.asList(entry),
			e -> new String[] {c.parents.isEmpty() ? "" : orEmpty(r.show(c.hash + "^", e.oldPath)), orEmpty(r.show(c.hash, e.path))});
	}

	void compareWithWorkingTree(Commit c, ChangedFile f) {
		GitRepo r = repo();
		CompareWindow.Entry entry = new CompareWindow.Entry(f.path, f.label(), null);
		CompareWindow.open(w, w.shell, f.path + ": " + c.shortHash() + " and the working tree", c.shortHash(), "Working tree", Arrays.asList(entry),
			e -> new String[] {orEmpty(r.show(c.hash, e.path)), r.working(e.path)});
	}
}
