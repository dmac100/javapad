package javapad.ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javapad.model.EclipseProject;
import javapad.model.JavaTemplate;
import org.eclipse.swt.widgets.Shell;

// New file, new folder and delete in the sidebar trees.
class FileActions
{
	private final Shell shell;
	private final Supplier<EclipseProject> project;
	private final Supplier<ProjectTree> activeTree;
	private final BooleanSupplier inSources;
	private final Runnable refreshTrees;
	private final Consumer<Location> opener;
	private final Consumer<String> error;

	FileActions(Shell shell, Supplier<EclipseProject> project, Supplier<ProjectTree> activeTree, BooleanSupplier inSources, Runnable refreshTrees, Consumer<Location> opener,
		Consumer<String> error)
	{
		this.shell = shell;
		this.project = project;
		this.activeTree = activeTree;
		this.inSources = inSources;
		this.refreshTrees = refreshTrees;
		this.opener = opener;
		this.error = error;
	}

	void newFile()
	{
		File dir = targetDir();
		String name = dir == null ? null : Prompt.ask(shell, "New File", "File name:", "");
		if(name == null || name.isEmpty())
		{
			return;
		}
		try
		{
			// in the sources pane a name without an extension is a Java class
			if(inSources.getAsBoolean() && !name.substring(name.lastIndexOf('/') + 1).contains("."))
			{
				name += ".java";
			}
			File f = new File(dir, name);
			f.getParentFile().mkdirs();
			if(f.createNewFile() && name.endsWith(".java"))
			{
				Files.write(f.toPath(), JavaTemplate.of(f, project.get().sourceFolders).getBytes(StandardCharsets.UTF_8));
			}
			refreshTrees.run();
			opener.accept(new Location(f, 1, 1));
		}
		catch(Exception e)
		{
			error.accept(e.getMessage());
		}
	}

	void newFolder()
	{
		File dir = targetDir();
		String name = dir == null ? null : Prompt.ask(shell, "New Folder", "Folder name:", "");
		if(name == null || name.isEmpty())
		{
			return;
		}
		new File(dir, name).mkdirs();
		refreshTrees.run();
	}

	void deleteSelected()
	{
		File f = activeTree.get().selected();
		EclipseProject p = project.get();
		if(f == null || p == null || f.equals(p.root))
		{
			return;
		}
		if(ConfirmDialog.confirm(shell, "Delete", "Delete " + f.getName() + (f.isDirectory() ? " and everything in it?" : "?")))
		{
			FileUtil.deleteRecursive(f);
			refreshTrees.run();
		}
	}

	private File targetDir()
	{
		File f = activeTree.get().selected();
		EclipseProject p = project.get();
		if(f == null)
		{
			return p == null ? null : p.root;
		}
		return f.isDirectory() ? f : f.getParentFile();
	}
}
