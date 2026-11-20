package javapad.ui.action;

import javapad.ui.FileUtil;
import javapad.ui.claude.Prompt;
import javapad.ui.dialog.ConfirmDialog;
import javapad.ui.editor.Location;
import javapad.ui.pane.ProjectTree;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javapad.git.GitRepo;
import javapad.git.GitResult;
import javapad.model.EclipseProject;
import javapad.ui.editor.EditorTab;
import javapad.ui.editor.EditorTabs;
import java.util.Map;
import javapad.model.JavaTemplate;
import org.eclipse.swt.widgets.Shell;

// New file, new folder and delete in the sidebar trees.
public class FileActions {
	private final Shell shell;
	private final Supplier<EclipseProject> project;
	private final Supplier<ProjectTree> activeTree;
	private final BooleanSupplier inSources;
	private final Runnable refreshTrees;
	private final Consumer<Location> opener;
	private final Consumer<String> error;
	private final EditorTabs editors;
	private final Supplier<GitRepo> repo;
	private final Runnable afterMove;

	public FileActions(Shell shell, Supplier<EclipseProject> project, Supplier<ProjectTree> activeTree, BooleanSupplier inSources, Runnable refreshTrees, Consumer<Location> opener,
		Consumer<String> error, EditorTabs editors, Supplier<GitRepo> repo, Runnable afterMove) {
		this.editors = editors;
		this.repo = repo;
		this.afterMove = afterMove;
		this.shell = shell;
		this.project = project;
		this.activeTree = activeTree;
		this.inSources = inSources;
		this.refreshTrees = refreshTrees;
		this.opener = opener;
		this.error = error;
	}

	void newFile() {
		File dir = targetDir();
		String name = dir == null ? null : Prompt.ask(shell, "New File", "File name:", "");
		if(name == null || name.isEmpty()) {
			return;
		}
		try {
			// in the sources pane a name without an extension is a Java class
			if(inSources.getAsBoolean() && !name.substring(name.lastIndexOf('/') + 1).contains(".")) {
				name += ".java";
			}
			File f = new File(dir, name);
			f.getParentFile().mkdirs();
			if(f.createNewFile() && name.endsWith(".java")) {
				Files.write(f.toPath(), JavaTemplate.of(f, project.get().sourceFolders).getBytes(StandardCharsets.UTF_8));
			}
			refreshTrees.run();
			opener.accept(new Location(f, 1, 1));
		} catch(Exception e) {
			e.printStackTrace();
			error.accept(e.getMessage());
		}
	}

	void newFolder() {
		File dir = targetDir();
		String name = dir == null ? null : Prompt.ask(shell, "New Folder", "Folder name:", "");
		if(name == null || name.isEmpty()) {
			return;
		}
		new File(dir, name).mkdirs();
		refreshTrees.run();
	}

	void deleteSelected() {
		File f = activeTree.get().selected();
		EclipseProject p = project.get();
		if(f == null || p == null || f.equals(p.root)) {
			return;
		}
		if(ConfirmDialog.confirm(shell, "Delete", "Delete " + f.getName() + (f.isDirectory() ? " and everything in it?" : "?"))) {
			FileUtil.deleteRecursive(f);
			refreshTrees.run();
		}
	}

	// Renames the selected file or folder; a name with slashes moves it, relative to the folder it is in.
	void renameSelected() {
		File f = selectedMovable();
		String name = f == null ? null : Prompt.ask(shell, "Rename / Move", "New name or path, relative to " + f.getParentFile().getName() + ":", f.getName());
		if(name == null || name.trim().isEmpty() || name.trim().equals(f.getName())) {
			return;
		}
		move(f, new File(f.getParentFile(), name.trim()).toPath().normalize().toFile());
	}

	// Drag and drop in the tree: moves a file or folder into a folder.
	public void moveInto(File f, File dir) {
		EclipseProject p = project.get();
		if(p == null || f.equals(p.root) || !dir.isDirectory() || dir.equals(f.getParentFile())) {
			return;
		}
		move(f, new File(dir, f.getName()));
	}

	private File selectedMovable() {
		File f = activeTree.get().selected();
		EclipseProject p = project.get();
		return f == null || p == null || f.equals(p.root) ? null : f;
	}

	// Moves the file or folder on disk, through git when it is tracked so that git sees a rename, and the open editors follow it.
	void move(File from, File to) {
		EclipseProject p = project.get();
		Path root = p.root.toPath().toAbsolutePath().normalize();
		Path target = to.toPath().toAbsolutePath().normalize();
		if(!target.startsWith(root)) {
			error.accept("A file cannot be moved out of the project.");
			return;
		}
		if(target.startsWith(from.toPath().toAbsolutePath().normalize())) {
			error.accept("A folder cannot be moved into itself.");
			return;
		}
		if(to.exists()) {
			error.accept(to.getName() + " already exists.");
			return;
		}
		Map<EditorTab, File> plan = editors.planMove(from, to);
		GitRepo r = repo.get();
		GitResult result;
		if(r != null) {
			result = r.move(from, to);
		} else {
			result = plainMove(from, to);
		}
		if(!result.ok()) {
			error.accept(result.message());
			return;
		}
		editors.applyMove(plan);
		afterMove.run();
	}

	private static GitResult plainMove(File from, File to) {
		try {
			to.getAbsoluteFile().getParentFile().mkdirs();
			Files.move(from.toPath(), to.toPath());
			return new GitResult(0, "", "");
		} catch(Exception e) {
			return new GitResult(1, "", String.valueOf(e.getMessage()));
		}
	}

	private File targetDir() {
		File f = activeTree.get().selected();
		EclipseProject p = project.get();
		if(f == null) {
			return p == null ? null : p.root;
		}
		return f.isDirectory() ? f : f.getParentFile();
	}
}
