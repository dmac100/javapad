package javapad.ui;

import javapad.ui.action.Automation;
import javapad.ui.action.FileActions;
import javapad.ui.action.ImportActions;
import javapad.ui.action.MenuBuilder;
import javapad.ui.action.NavigationActions;
import javapad.ui.action.RunController;
import javapad.ui.claude.ClaudePane;
import javapad.ui.claude.Prompt;
import javapad.ui.dialog.CommandDialog;
import javapad.ui.dialog.CommandList;
import javapad.ui.dialog.ConfirmDialog;
import javapad.ui.dialog.FindDialog;
import javapad.ui.dialog.FindInFilesDialog;
import javapad.ui.editor.CodeAssist;
import javapad.ui.editor.EditorMenu;
import javapad.ui.editor.EditorTab;
import javapad.ui.editor.EditorTabs;
import javapad.ui.editor.Location;
import javapad.ui.git.GitActions;
import javapad.ui.git.HistoryPane;
import javapad.ui.pane.BottomTabs;
import javapad.ui.pane.CallHierarchyPane;
import javapad.ui.pane.JUnitPane;
import javapad.ui.pane.ProblemsPane;
import javapad.ui.pane.ProjectTree;
import javapad.ui.pane.SearchPane;
import javapad.ui.pane.SideTabs;
import javapad.ui.terminal.ConsolePane;
import javapad.ui.terminal.TerminalPane;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javapad.lang.FileSearch;
import javapad.git.GitStatus;
import javapad.model.EclipseProject;
import javapad.model.GitIgnore;
import javapad.model.Problem;
import javapad.run.LaunchSettings;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.FontDialog;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;

public class MainWindow
{
	public final Display display;
	public final Shell shell;
	public final Theme theme;
	public final ProjectTree projectTree;
	public final ProjectTree sourceTree;
	final CTabFolder left;
	final SideTabs sideTabs;
	public final SideTabs.Tab projectTab;
	public final SideTabs.Tab sourceTab;
	public final EditorTabs editors;
	public final ConsolePane console;
	final ProblemsPane problemsPane;
	final CTabFolder bottom;
	public final BottomTabs bottomTabs;
	public final BottomTabs.Tab consoleTab;
	public final BottomTabs.Tab problemsTab;
	public final SideTabs.Tab junitTab;
	public final BottomTabs.Tab callTab;
	final BottomTabs.Tab historyTab;
	public final BottomTabs.Tab searchTab;
	final BottomTabs.Tab terminalTab;
	final BottomTabs.Tab claudeTab;
	public final ClaudePane claude;
	private final TerminalPane terminal;
	final SearchPane searchPane;
	public final HistoryPane history;
	public final GitActions git;
	private final CallHierarchyPane callPane;
	final JUnitPane junitPane;
	private final SashForm horizontal;
	private final SashForm vertical;
	private final ToolBar toolbar;
	private EclipseProject project;
	private LaunchSettings settings;
	public RunController run;
	public FileActions files;
	final CodeAssist assist;
	public ImportActions importActions;
	public NavigationActions navigation;
	private FindDialog findDialog;
	private final MenuBuilder menus = new MenuBuilder(this);
	private CommandList lastCommands = new CommandList();
	private CommandList lastFiles = new CommandList();
	private String lastSearch = "";
	private boolean searchCase;
	private boolean searchRegex;
	private int searchGeneration;
	static final Set<String> BINARY = new HashSet<String>(Arrays.asList(
		"class", "jar", "zip", "gz", "png", "jpg", "jpeg", "gif", "bmp", "ico", "so", "dll", "exe", "pdf", "wav", "mp3",
		"ogg", "iso", "grp", "wad", "dylib", "o", "a", "tar", "7z"));

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
		toolbar = new ToolBar(shell, SWT.FLAT);
		toolbar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		horizontal = new SashForm(shell, SWT.HORIZONTAL);
		horizontal.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		left = new CTabFolder(horizontal, SWT.BORDER);
		projectTree = new ProjectTree(left, false);
		sourceTree = new ProjectTree(left, true);
		sideTabs = new SideTabs(left, shell);
		projectTab = sideTabs.add("Project", projectTree.getControl());
		sourceTab = sideTabs.add("Sources", sourceTree.getControl());
		projectTab.select();
		vertical = new SashForm(horizontal, SWT.VERTICAL);
		editors = new EditorTabs(vertical, theme);
		editors.setAssist(assist);
		navigation = new NavigationActions(shell, assist, loc -> openLine(loc));
		editors.setNavigation(navigation);
		editors.setTabHook(tab -> tabOpened(tab));
		bottom = new CTabFolder(vertical, SWT.BORDER);
		bottom.setTabPosition(SWT.TOP);
		console = new ConsolePane(bottom, theme);
		problemsPane = new ProblemsPane(bottom);
		bottomTabs = new BottomTabs(bottom, shell);
		problemsTab = bottomTabs.add("problems", "Problems", problemsPane.getControl(), true);
		consoleTab = bottomTabs.add("console", "Console", console.getControl(), true);
		junitPane = new JUnitPane(left, theme);
		junitTab = sideTabs.add("JUnit", junitPane.getControl());
		callPane = new CallHierarchyPane(bottom, theme);
		callTab = bottomTabs.add("call", "Call Hierarchy", callPane.getControl(), false);
		searchPane = new SearchPane(bottom, theme);
		searchTab = bottomTabs.add("search", "Search", searchPane.getControl(), false);
		searchPane.setOpener((f, line, column, length) -> openMatch(f, line, column, length));
		git = new GitActions(this);
		history = new HistoryPane(bottom, theme, this);
		terminal = new TerminalPane(bottom, theme);
		terminal.setFocusListener(focus -> menus.setAccelerators(!focus));
		terminalTab = bottomTabs.add("terminal", "Terminal", terminal.getControl(), true);
		historyTab = bottomTabs.add("history", "History", history.getControl(), true);
		claude = new ClaudePane(bottom, theme);
		claudeTab = bottomTabs.add("claude", "Claude", claude.getControl(), true);
		claude.setWaitingHook(waiting -> bottomTabs.setText(claudeTab, waiting ? "Claude (waiting)" : "Claude"));
		bottom.addListener(SWT.Selection, e ->
		{
			if(bottomTabs.selected() == terminalTab)
			{
				terminal.ensureStarted();
			}
			if(bottomTabs.selected() == historyTab)
			{
				history.refresh();
			}
		});
		callPane.setModelSupplier(() -> assist.model());
		callPane.setOpener(l -> openLine(l));
		navigation.setHierarchy(t ->
		{
			callPane.show(t.key, t.description, t.declaration);
			bottomTabs.show(callTab);
		});
		bottomTabs.show(consoleTab);
		horizontal.setWeights(new int[] {20, 80});
		vertical.setWeights(new int[] {60, 40});
		run = new RunController(display, shell, editors, console, problemsPane, junitPane, assist, bottomTabs, consoleTab, problemsTab, junitTab, () -> currentFile(), () -> refreshSources(), m -> error(m));
		importActions = new ImportActions(shell, assist);
		menus.addToolItems(toolbar);
		showToolbar(Theme.loadToolbar());
		files = new FileActions(shell, () -> project, () -> activeTree(), () -> sourceTab.selected(), () -> refreshTrees(), l -> open(l), m -> error(m));
		menus.buildMenus();
		editors.setCompileHook(() -> run.runActive());
		projectTree.setOpener(f -> open(new Location(f, 1, 1)));
		sourceTree.setOpener(f -> open(new Location(f, 1, 1)));
		editors.setProblemsHook(list -> markErrorFiles(list));
		console.setOpener(l -> openLine(l));
		problemsPane.setOpener(l -> open(l));
		problemsPane.setPreviewer(l ->
		{
			EditorTab tab = editors.open(l.file);
			if(tab != null)
			{
				tab.editor.highlightLine(l.line);
			}
		});
		junitPane.setOpener(l -> openLine(l));
		junitPane.setRunner(specs -> run.runSpecs(specs));
		junitPane.setToolbar(() -> run.rerunAll(), () -> run.rerunFailed(), () -> run.stop(), () -> run.testsRunning(), () -> run.hasTestRun());
		console.setInputSink(s ->
		{
			run.sendInput(s);
		});
		shell.addListener(SWT.Close, e -> e.doit = confirmExit());
		shell.addListener(SWT.Dispose, e -> terminal.stop());
		shell.addListener(SWT.Activate, e -> git.refresh());
		applyTheme();
	}

	public Shell getShell()
	{
		return shell;
	}

	void applyTheme()
	{
		editors.restyle();
		CTabFolder[] folders = {bottom, left};
		for(CTabFolder f : folders)
		{
			f.setBackground(theme.chrome);
			f.setForeground(theme.foreground);
			f.setSelectionBackground(f == left ? theme.sidebar : theme.background);
			f.setSelectionForeground(f == left ? theme.sidebarText : theme.foreground);
		}
		EditorTabs.styleSash(horizontal, theme);
		EditorTabs.styleSash(vertical, theme);
		projectTree.restyle(theme);
		sourceTree.restyle(theme);
		problemsPane.restyle(theme);
		console.restyle();
		terminal.restyle();
		junitPane.restyle();
		callPane.restyle();
		history.restyle();
		claude.restyle();
		searchPane.restyle();
		for(EditorTab tab : editors.tabs())
		{
			tab.editor.restyle();
		}
	}

	public boolean toolbarShown()
	{
		return !((GridData)toolbar.getLayoutData()).exclude;
	}

	public void showToolbar(boolean shown)
	{
		toolbar.setVisible(shown);
		((GridData)toolbar.getLayoutData()).exclude = !shown;
		shell.layout(true, true);
		Theme.saveToolbar(shown);
	}

	// a new editor tab: its menu, and the committed text its changed lines are marked against
	void tabOpened(EditorTab tab)
	{
		new EditorMenu(this).attach(tab);
		git.loadBaseline(tab);
	}

	public void showWhitespace(boolean shown)
	{
		theme.showWhitespace = shown;
		Theme.saveWhitespace(shown);
		for(EditorTab tab : editors.tabs())
		{
			tab.editor.redrawWhitespace();
		}
	}

	public void chooseFont()
	{
		FontDialog dialog = new FontDialog(shell);
		dialog.setFontList(theme.font.getFontData());
		FontData chosen = dialog.open();
		if(chosen == null)
		{
			return;
		}
		Font old = theme.changeFont(chosen);
		applyTheme();
		old.dispose();
	}

	public void updateBranch(GitStatus status)
	{
		projectTree.setBranch(status);
		sourceTree.setBranch(status);
	}

	public void setDark(boolean dark)
	{
		theme.use(dark);
		theme.savePreference();
		applyTheme();
	}

	public ProjectTree activeTree()
	{
		return sourceTab.selected() ? sourceTree : projectTree;
	}

	// One-off: select the file of the active editor in the sidebar, switching tab if this one does not list it.
	public void showInSidebar()
	{
		EditorTab tab = editors.active();
		if(tab == null || project == null)
		{
			return;
		}
		boolean sources = sourceTab.selected();
		ProjectTree first = sources ? sourceTree : projectTree;
		ProjectTree second = sources ? projectTree : sourceTree;
		if(first.reveal(tab.file))
		{
			return;
		}
		if(second.reveal(tab.file))
		{
			(sources ? projectTab : sourceTab).select();
		}
	}

	// Picks up what changed outside the IDE: open files, the trees, the code analysis, and the git branch, status and history.
	public void refreshAll()
	{
		if(project == null)
		{
			return;
		}
		editors.reloadClean();
		refreshTrees();
		assist.setProject(project);
		git.projectChanged();
	}

	public void refreshTrees()
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
			e.printStackTrace();
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
		terminal.setProject(project);
		claude.setProject(project);
		problemsPane.setProject(project);
		junitPane.setProject(project);
		junitPane.clear();
		junitTab.setText("JUnit");
		problemsPane.setProblems(new ArrayList<Problem>());
		editors.setProblems(new ArrayList<Problem>());
		console.clear();
		shell.setText("JavaPad - " + project.name);
		git.projectChanged();
		searchPane.setProject(project);
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

	public void regenerateProject()
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
			e.printStackTrace();
			error("Cannot regenerate project files: " + e);
			return;
		}
		loadProject();
	}

	// Opens the file with the whole line selected.
	public void openLine(Location loc)
	{
		EditorTab tab = editors.open(loc.file);
		if(tab != null)
		{
			tab.editor.highlightLine(loc.line);
			tab.editor.getControl().setFocus();
		}
	}

	public void open(Location loc)
	{
		EditorTab tab = editors.open(loc.file);
		if(tab != null)
		{
			tab.editor.goTo(loc.line, loc.column);
		}
	}

	public void error(String message)
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

	public void saveActive()
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
			e.printStackTrace();
			error("Cannot save: " + e.getMessage());
			return;
		}
		git.refresh();
		if(project != null && project.isSource(tab.file))
		{
			run.build(null);
		}
	}

	// Edit > Find in Files: asks what to look for, searches every project file (using unsaved editor text) and lists the lines in the Search tab.
	public void findInFiles()
	{
		if(project == null)
		{
			error("Open a project first.");
			return;
		}
		String initial = lastSearch;
		EditorTab active = editors.active();
		if(active != null)
		{
			String selected = active.editor.getControl().getSelectionText();
			if(!selected.isEmpty() && selected.indexOf('\n') < 0 && selected.length() <= 200)
			{
				initial = selected;
			}
		}
		FindInFilesDialog.Request req = new FindInFilesDialog(shell).open(initial, searchCase, searchRegex);
		if(req == null)
		{
			return;
		}
		lastSearch = req.query;
		searchCase = req.caseSensitive;
		searchRegex = req.regex;
		Pattern pattern = FileSearch.pattern(req.query, req.caseSensitive, req.regex);
		Map<File, String> unsaved = new HashMap<File, String>();
		for(EditorTab tab : editors.tabs())
		{
			if(tab.isDirty())
			{
				unsaved.put(tab.file, tab.editor.getContent());
			}
		}
		int mine = ++searchGeneration;
		searchPane.searching(req.query);
		showBottomTab("search");
		Thread t = new Thread(() ->
		{
			List<File> found = new ArrayList<File>();
			listFiles(project.root, found, GitIgnore.of(project.root));
			FileSearch.Result result = FileSearch.search(found, unsaved, pattern, 5000);
			display.asyncExec(() ->
			{
				if(!shell.isDisposed() && mine == searchGeneration)
				{
					searchPane.show(req.query, result);
				}
			});
		}, "find-in-files");
		t.setDaemon(true);
		t.start();
	}

	// opens a search result with the match selected
	void openMatch(File file, int line, int column, int length)
	{
		EditorTab tab = editors.open(file);
		if(tab != null)
		{
			tab.editor.goTo(line, column);
			StyledText t = tab.editor.getControl();
			int caret = t.getCaretOffset();
			t.setSelection(caret, Math.min(t.getCharCount(), caret + length));
			t.setFocus();
		}
	}

	public void find()
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

	public CommandDialog commandDialog()
	{
		CommandList all = new CommandList();
		for(EditorTab tab : editors.tabs())
		{
			String path = project == null ? tab.file.getName() : project.relative(tab.file).replace(File.separatorChar, '/');
			all.add("Tab: " + path + (tab.isDirty() ? " *" : ""), () -> editors.show(tab));
		}
		all.addAll(menus.commands());
		menus.addGitCommands(all);
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
			listFiles(project.root, found, GitIgnore.of(project.root));
			for(File f : found)
			{
				files.add(fileLabel(project.relative(f).replace(File.separatorChar, '/')), () -> open(new Location(f, 1, 1)));
			}
		}
		return files;
	}

	// "Name.java - src/pkg": the base name first, then the folder
	static String fileLabel(String path)
	{
		int slash = path.lastIndexOf('/');
		return slash < 0 ? path : path.substring(slash + 1) + " - " + path.substring(0, slash);
	}

	CommandDialog fileDialog()
	{
		CommandList files = fileList();
		lastFiles = files;
		return new CommandDialog(shell, text -> files.find(text, 300), "Open File");
	}

	public void runFileDialog()
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

	public void runCommandDialog()
	{
		String choice = commandDialog().open();
		if(choice != null)
		{
			lastCommands.run(choice);
		}
	}

	void listFiles(File dir, List<File> out, GitIgnore ignore)
	{
		File[] children = dir.listFiles();
		if(children == null || out.size() > 50000)
		{
			return;
		}
		Arrays.sort(children);
		GitIgnore here = ignore.child(dir);
		File output = project.outputFolder == null ? null : project.outputFolder.getAbsoluteFile();
		for(File f : children)
		{
			if(f.isDirectory())
			{
				if(!f.getName().startsWith(".") && !f.getAbsoluteFile().equals(output) && !here.ignored(f, true))
				{
					listFiles(f, out, here);
				}
				continue;
			}
			if(here.ignored(f, false))
			{
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

	public void gotoLine()
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

	public void runSelected()
	{
		run.runFrom(activeTree().selected());
	}

	// Selection if there is one, else the whole file; only Java files have a formatter.
	public void format(EditorTab tab)
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
			e.printStackTrace();
			error("Cannot update .classpath: " + e);
		}
	}

	public void withEditor(Consumer<EditorTab> action)
	{
		EditorTab tab = editors.active();
		if(tab != null)
		{
			action.accept(tab);
		}
	}

	public void contentAssist()
	{
		EditorTab tab = editors.active();
		if(tab != null)
		{
			tab.editor.getControl().setFocus();
			tab.editor.triggerCompletion(true);
		}
	}

	// the files with errors get a mark in both sidebar trees
	void markErrorFiles(List<Problem> problems)
	{
		Set<File> files = new HashSet<File>();
		for(Problem p : problems)
		{
			if(p.error && !p.file.isEmpty())
			{
				files.add(new File(p.file));
			}
		}
		projectTree.setErrorFiles(files);
		sourceTree.setErrorFiles(files);
	}

	public void goToMatchingBracket()
	{
		EditorTab tab = editors.active();
		if(tab != null)
		{
			tab.editor.goToMatchingBracket();
		}
	}

	public void editArguments()
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

	// Used by Automation: shows one of the tabs by name (problems, console, call, history, search at the bottom; junit in the sidebar); a closed one is reopened.
	public void showBottomTab(String name)
	{
		if(name.equals("junit"))
		{
			junitTab.select();
			return;
		}
		BottomTabs.Tab tab = bottomTabs.named(name);
		if(tab != null)
		{
			bottomTabs.show(tab);
		}
		if(tab == terminalTab)
		{
			terminal.ensureStarted();
		}
	}

	public void showSourcesTree()
	{
		sourceTab.select();
	}

	public void selectProjectRoot()
	{
		projectTree.selectRoot();
	}

	public void runCommand(String choice)
	{
		lastCommands.run(choice);
	}

	public EclipseProject project()
	{
		return project;
	}

	public RunController runner()
	{
		return run;
	}

	public JUnitPane junit()
	{
		return junitPane;
	}

	public Display display()
	{
		return display;
	}
}
