package javapad.ui;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javapad.model.EclipseProject;
import javapad.model.Problem;
import javapad.run.LaunchSettings;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;

public class MainWindow
{
	final Display display;
	final Shell shell;
	final Theme theme;
	final ProjectTree projectTree;
	final ProjectTree sourceTree;
	final CTabFolder left;
	final CTabItem projectTab;
	final CTabItem sourceTab;
	final EditorTabs editors;
	final ConsolePane console;
	final ProblemsPane problemsPane;
	final CTabFolder bottom;
	final CTabItem consoleTab;
	final CTabItem problemsTab;
	final CTabItem junitTab;
	final CTabItem callTab;
	private final CallHierarchyPane callPane;
	final JUnitPane junitPane;
	private final SashForm horizontal;
	private final SashForm vertical;
	private EclipseProject project;
	private LaunchSettings settings;
	RunController run;
	FileActions files;
	final CodeAssist assist;
	ImportActions importActions;
	NavigationActions navigation;
	private FindDialog findDialog;
	private final MenuBuilder menus = new MenuBuilder(this);
	private CommandList lastCommands = new CommandList();
	private CommandList lastFiles = new CommandList();

	public MainWindow(Display display)
	{
		this.display = display;
		theme = new Theme(display);
		assist = new CodeAssist(display);
		shell = new Shell(display);
		shell.setText("JavaPad");
		shell.setSize(1280, 800);
		shell.setLayout(new GridLayout(1, false));
		((GridLayout)shell.getLayout()).marginWidth = 0;
		((GridLayout)shell.getLayout()).marginHeight = 0;
		ToolBar toolbar = new ToolBar(shell, SWT.FLAT);
		toolbar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		horizontal = new SashForm(shell, SWT.HORIZONTAL);
		horizontal.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		left = new CTabFolder(horizontal, SWT.BORDER);
		projectTree = new ProjectTree(left, false);
		sourceTree = new ProjectTree(left, true);
		projectTab = new CTabItem(left, SWT.NONE);
		projectTab.setText("Project");
		projectTab.setControl(projectTree.getControl());
		sourceTab = new CTabItem(left, SWT.NONE);
		sourceTab.setText("Sources");
		sourceTab.setControl(sourceTree.getControl());
		left.setSelection(projectTab);
		vertical = new SashForm(horizontal, SWT.VERTICAL);
		editors = new EditorTabs(vertical, theme);
		editors.setAssist(assist);
		navigation = new NavigationActions(shell, assist, loc -> open(loc));
		editors.setNavigation(navigation);
		editors.setTabHook(tab -> new EditorMenu(this).attach(tab));
		bottom = new CTabFolder(vertical, SWT.BORDER);
		bottom.setTabPosition(SWT.TOP);
		console = new ConsolePane(bottom, theme);
		problemsPane = new ProblemsPane(bottom);
		problemsTab = new CTabItem(bottom, SWT.NONE);
		problemsTab.setText("Problems");
		problemsTab.setControl(problemsPane.getControl());
		consoleTab = new CTabItem(bottom, SWT.NONE);
		consoleTab.setText("Console");
		consoleTab.setControl(console.getControl());
		junitPane = new JUnitPane(bottom, theme);
		junitTab = new CTabItem(bottom, SWT.NONE);
		junitTab.setText("JUnit");
		junitTab.setControl(junitPane.getControl());
		callPane = new CallHierarchyPane(bottom, theme);
		callTab = new CTabItem(bottom, SWT.NONE);
		callTab.setText("Call Hierarchy");
		callTab.setControl(callPane.getControl());
		callPane.setModelSupplier(() -> assist.model());
		callPane.setOpener(l -> open(l));
		navigation.setHierarchy(t ->
		{
			callPane.show(t.key, t.description, t.declaration);
			bottom.setSelection(callTab);
		});
		bottom.setSelection(consoleTab);
		horizontal.setWeights(new int[] {20, 80});
		vertical.setWeights(new int[] {60, 40});
		run = new RunController(display, shell, editors, console, problemsPane, junitPane, assist, bottom, consoleTab, problemsTab, junitTab, () -> currentFile(), () -> refreshSources(), m -> error(m));
		importActions = new ImportActions(shell, assist);
		menus.addToolItems(toolbar);
		files = new FileActions(shell, () -> project, () -> activeTree(), () -> left.getSelection() == sourceTab, () -> refreshTrees(), l -> open(l), m -> error(m));
		menus.buildMenus();
		editors.setCompileHook(() -> run.runActive());
		projectTree.setOpener(f -> open(new Location(f, 1, 1)));
		sourceTree.setOpener(f -> open(new Location(f, 1, 1)));
		console.setOpener(l -> open(l));
		problemsPane.setOpener(l -> open(l));
		problemsPane.setPreviewer(l ->
		{
			EditorTab tab = editors.open(l.file);
			if(tab != null)
			{
				tab.editor.highlightLine(l.line);
			}
		});
		junitPane.setOpener(l -> open(l));
		junitPane.setRunner(specs -> run.runSpecs(specs));
		console.setInputSink(s ->
		{
			run.sendInput(s);
		});
		shell.addListener(SWT.Close, e -> e.doit = confirmExit());
		applyTheme();
	}

	public Shell getShell()
	{
		return shell;
	}

	void applyTheme()
	{
		CTabFolder[] folders = {editors.getControl(), bottom, left};
		for(CTabFolder f : folders)
		{
			f.setBackground(theme.chrome);
			f.setForeground(theme.foreground);
			f.setSelectionBackground(f == left ? theme.sidebar : theme.background);
			f.setSelectionForeground(f == left ? theme.sidebarText : theme.foreground);
		}
		horizontal.setBackground(theme.chrome);
		vertical.setBackground(theme.chrome);
		projectTree.restyle(theme);
		sourceTree.restyle(theme);
		problemsPane.restyle(theme);
		console.restyle();
		junitPane.restyle();
		callPane.restyle();
		for(EditorTab tab : editors.tabs())
		{
			tab.editor.restyle();
		}
	}

	void setDark(boolean dark)
	{
		theme.use(dark);
		theme.savePreference();
		applyTheme();
	}

	ProjectTree activeTree()
	{
		return left.getSelection() == sourceTab ? sourceTree : projectTree;
	}

	// One-off: select the file of the active editor in the sidebar, switching tab if this one does not list it.
	public void showInSidebar()
	{
		EditorTab tab = editors.active();
		if(tab == null || project == null)
		{
			return;
		}
		boolean sources = left.getSelection() == sourceTab;
		ProjectTree first = sources ? sourceTree : projectTree;
		ProjectTree second = sources ? projectTree : sourceTree;
		if(first.reveal(tab.file))
		{
			return;
		}
		if(second.reveal(tab.file))
		{
			left.setSelection(sources ? projectTab : sourceTab);
		}
	}

	void refreshTrees()
	{
		projectTree.refresh();
		sourceTree.refresh();
	}

	public void chooseProject()
	{
		DirectoryDialog dd = new DirectoryDialog(shell);
		dd.setMessage("Select an Eclipse Java project folder");
		if(project != null)
		{
			dd.setFilterPath(project.root.getParent());
		}
		String dir = dd.open();
		if(dir != null)
		{
			openProject(new File(dir));
		}
	}

	public boolean openProject(File dir)
	{
		if(!confirmExit())
		{
			return false;
		}
		try
		{
			project = EclipseProject.open(dir);
		}
		catch(Exception e)
		{
			error("Cannot open project " + dir + ": " + e);
			return false;
		}
		loadProject();
		return true;
	}

	void loadProject()
	{
		editors.closeAll();
		assist.setProject(project);
		settings = new LaunchSettings(project.root);
		run.setProject(project, settings);
		projectTree.setProject(project);
		sourceTree.setProject(project);
		console.setProject(project);
		problemsPane.setProject(project);
		junitPane.setProject(project);
		junitPane.clear();
		junitTab.setText("JUnit");
		problemsPane.setProblems(new ArrayList<Problem>());
		editors.setProblems(new ArrayList<Problem>());
		console.clear();
		shell.setText("JavaPad - " + project.name);
		for(String w : project.warnings)
		{
			console.append("warning: " + w + "\n", true);
		}
		for(String n : project.notes)
		{
			console.append(n + "\n", false);
		}
		console.append("Opened " + project.name + ": " + project.sourceFolders.size() + " source folder(s), "
			+ project.libraries.size() + " classpath entries, output " + project.relative(project.outputFolder) + "\n", false);
	}

	void regenerateProject()
	{
		if(project == null)
		{
			return;
		}
		String question = "Regenerate .project and .classpath for " + project.name + "?\nThe existing files will be overwritten.";
		if(!ConfirmDialog.confirm(shell, "Regenerate Project File", question) || !confirmExit())
		{
			return;
		}
		try
		{
			project = EclipseProject.regenerate(project.root);
		}
		catch(Exception e)
		{
			error("Cannot regenerate project files: " + e);
			return;
		}
		loadProject();
	}

	public void open(Location loc)
	{
		EditorTab tab = editors.open(loc.file);
		if(tab != null)
		{
			tab.editor.goTo(loc.line, loc.column);
		}
	}

	void error(String message)
	{
		ConfirmDialog.message(shell, "Error", message, SWT.ICON_ERROR);
	}

	boolean confirmExit()
	{
		if(!editors.anyDirty())
		{
			return true;
		}
		int answer = ConfirmDialog.ask(shell, "Save Modified Files", "Save modified files?");
		if(answer == SWT.CANCEL)
		{
			return false;
		}
		if(answer == SWT.YES)
		{
			return editors.saveAll();
		}
		return true;
	}

	void saveActive()
	{
		EditorTab tab = editors.active();
		if(tab == null)
		{
			return;
		}
		try
		{
			tab.save();
		}
		catch(Exception e)
		{
			error("Cannot save: " + e.getMessage());
			return;
		}
		if(project != null && project.isSource(tab.file))
		{
			run.build(null);
		}
	}

	void find()
	{
		if(editors.active() == null)
		{
			return;
		}
		if(findDialog == null)
		{
			findDialog = new FindDialog(shell, () ->
			{
				EditorTab tab = editors.active();
				return tab == null ? null : tab.editor.getControl();
			});
		}
		findDialog.open();
	}

	CommandDialog commandDialog()
	{
		CommandList all = new CommandList();
		for(EditorTab tab : editors.tabs())
		{
			String path = project == null ? tab.file.getName() : project.relative(tab.file).replace(File.separatorChar, '/');
			all.add("Tab: " + path + (tab.isDirty() ? " *" : ""), () -> editors.show(tab));
		}
		all.addAll(menus.commands());
		lastCommands = all;
		return new CommandDialog(shell, text -> all.find(text, 300));
	}

	// Ctrl+Shift+R: every file in the project, by path.
	CommandList fileList()
	{
		CommandList files = new CommandList();
		if(project != null)
		{
			List<File> found = new ArrayList<File>();
			listFiles(project.root, found);
			for(File f : found)
			{
				files.add(project.relative(f).replace(File.separatorChar, '/'), () -> open(new Location(f, 1, 1)));
			}
		}
		return files;
	}

	CommandDialog fileDialog()
	{
		CommandList files = fileList();
		lastFiles = files;
		return new CommandDialog(shell, text -> files.find(text, 300), "Open File");
	}

	void runFileDialog()
	{
		if(project == null)
		{
			error("Open a project first.");
			return;
		}
		String choice = fileDialog().open();
		if(choice != null)
		{
			lastFiles.run(choice);
		}
	}

	void runCommandDialog()
	{
		String choice = commandDialog().open();
		if(choice != null)
		{
			lastCommands.run(choice);
		}
	}

	static final Set<String> BINARY = new HashSet<String>(Arrays.asList(
		"class", "jar", "zip", "gz", "png", "jpg", "jpeg", "gif", "bmp", "ico", "so", "dll", "exe", "pdf", "wav", "mp3",
		"ogg", "iso", "grp", "wad", "dylib", "o", "a", "tar", "7z"));

	void listFiles(File dir, List<File> out)
	{
		File[] children = dir.listFiles();
		if(children == null || out.size() > 50000)
		{
			return;
		}
		Arrays.sort(children);
		for(File f : children)
		{
			if(f.isDirectory())
			{
				if(!f.getName().startsWith(".") && !f.equals(project.outputFolder))
				{
					listFiles(f, out);
				}
				continue;
			}
			String name = f.getName();
			int dot = name.lastIndexOf('.');
			String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
			if(!BINARY.contains(ext) && f.length() < 5000000)
			{
				out.add(f);
			}
		}
	}

	void gotoLine()
	{
		EditorTab tab = editors.active();
		if(tab == null)
		{
			return;
		}
		String s = Prompt.ask(shell, "Go to Line", "Line number:", "");
		if(s == null)
		{
			return;
		}
		try
		{
			tab.editor.goTo(Integer.parseInt(s.trim()), 1);
		}
		catch(NumberFormatException e)
		{
			// not a line number: stay where we are
		}
	}

	File currentFile()
	{
		EditorTab tab = editors.active();
		if(tab != null)
		{
			return tab.file;
		}
		return activeTree().selected();
	}

	void runSelected()
	{
		run.runFrom(activeTree().selected());
	}

	// Selection if there is one, else the whole file; only Java files have a formatter.
	void format(EditorTab tab)
	{
		if(tab.file.getName().endsWith(".java"))
		{
			tab.editor.format();
		}
	}

	void refreshSources()
	{
		try
		{
			if(project.refreshSources())
			{
				projectTree.setProject(project);
				sourceTree.setProject(project);
				assist.setProject(project);
				console.append("Found source folder(s): " + project.sourceFolders.size() + "\n", false);
			}
		}
		catch(Exception e)
		{
			error("Cannot update .classpath: " + e);
		}
	}

	void withEditor(Consumer<EditorTab> action)
	{
		EditorTab tab = editors.active();
		if(tab != null)
		{
			action.accept(tab);
		}
	}

	void contentAssist()
	{
		EditorTab tab = editors.active();
		if(tab != null)
		{
			tab.editor.getControl().setFocus();
			tab.editor.triggerCompletion(true);
		}
	}

	void goToMatchingBracket()
	{
		EditorTab tab = editors.active();
		if(tab != null)
		{
			tab.editor.goToMatchingBracket();
		}
	}

	void editArguments()
	{
		File f = currentFile();
		String cls = project == null || f == null ? null : project.classNameOf(f);
		if(cls == null)
		{
			return;
		}
		String args = Prompt.ask(shell, "Program Arguments", cls + " arguments:", settings.getArgs(cls));
		if(args == null)
		{
			return;
		}
		String vm = Prompt.ask(shell, "VM Arguments", cls + " VM arguments:", settings.getVmArgs(cls));
		if(vm == null)
		{
			return;
		}
		settings.set(cls, args, vm);
	}

	public void screenshot(String path)
	{
		shell.forceActive();
		display.update();
		Rectangle b = shell.getBounds();
		Image image = new Image(display, b.width, b.height);
		GC gc = new GC(display);
		gc.copyArea(image, b.x, b.y);
		gc.dispose();
		ImageLoader loader = new ImageLoader();
		loader.data = new ImageData[] {image.getImageData()};
		loader.save(path, SWT.IMAGE_PNG);
		image.dispose();
	}

	public void automate()
	{
		new Automation(this).run();
	}

	// Used by Automation: shows one of the bottom tabs by name (problems, junit, console, call).
	void showBottomTab(String name)
	{
		CTabItem tab = name.equals("problems") ? problemsTab : name.equals("junit") ? junitTab : name.equals("console") ? consoleTab : name.equals("call") ? callTab : null;
		if(tab != null)
		{
			bottom.setSelection(tab);
		}
	}

	void showSourcesTree()
	{
		left.setSelection(sourceTab);
	}

	void selectProjectRoot()
	{
		projectTree.selectRoot();
	}

	void runCommand(String choice)
	{
		lastCommands.run(choice);
	}

	EclipseProject project()
	{
		return project;
	}

	RunController runner()
	{
		return run;
	}

	JUnitPane junit()
	{
		return junitPane;
	}

	Display display()
	{
		return display;
	}
}
