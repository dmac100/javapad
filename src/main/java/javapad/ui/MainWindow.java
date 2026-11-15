package javapad.ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javapad.build.ProjectCompiler;
import javapad.model.EclipseProject;
import javapad.model.Problem;
import javapad.run.LaunchSettings;
import javapad.run.MainFinder;
import javapad.run.Launcher;
import javapad.run.LiveTail;
import javapad.run.OutputListener;
import javapad.run.JUnitReport;
import javapad.run.TestFinder;
import javapad.run.TestResult;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

public class MainWindow
{
	private final Display display;
	private final Shell shell;
	private final Theme theme;
	private final ProjectTree projectTree;
	private final ProjectTree sourceTree;
	private final CTabFolder left;
	private final CTabItem projectTab;
	private final CTabItem sourceTab;
	private final EditorTabs editors;
	private final ConsolePane console;
	private final ProblemsPane problemsPane;
	private final CTabFolder bottom;
	private final CTabItem consoleTab;
	private final CTabItem problemsTab;
	private final CTabItem junitTab;
	private final JUnitPane junitPane;
	private final java.util.Set<File> parsedReports = new java.util.HashSet<File>();
	private final List<TestResult> allResults = new ArrayList<TestResult>();
	private File testRunDir;
	private LiveTail liveTail;
	private final java.util.concurrent.ConcurrentLinkedQueue<String[]> liveEvents = new java.util.concurrent.ConcurrentLinkedQueue<String[]>();
	private final SashForm horizontal;
	private final SashForm vertical;
	private EclipseProject project;
	private LaunchSettings settings;
	private Launcher launcher;
	private boolean busy;
	private Runnable idleHook;
	private FindDialog findDialog;
	private final CommandList commands = new CommandList();
	private CommandList lastCommands = new CommandList();
	private String menuName;
	private final List<java.util.function.Function<Launcher, List<String>>> pending = new ArrayList<java.util.function.Function<Launcher, List<String>>>();

	public MainWindow(Display display)
	{
		this.display = display;
		theme = new Theme(display);
		shell = new Shell(display);
		shell.setText("JavaPad");
		shell.setSize(1280, 800);
		shell.setLayout(new GridLayout(1, false));
		((GridLayout)shell.getLayout()).marginWidth = 0;
		((GridLayout)shell.getLayout()).marginHeight = 0;
		Composite toolbar = new Composite(shell, SWT.NONE);
		RowLayout rows = new RowLayout(SWT.HORIZONTAL);
		rows.marginTop = 4;
		rows.marginBottom = 4;
		rows.marginLeft = 4;
		rows.spacing = 1;
		rows.center = true;
		toolbar.setLayout(rows);
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
		bottom.setSelection(consoleTab);
		horizontal.setWeights(new int[] {20, 80});
		vertical.setWeights(new int[] {60, 40});
		addToolItems(toolbar);
		buildMenus();
		editors.setCompileHook(() -> runActive());
		projectTree.setOpener(f -> open(new Location(f, 1, 1)));
		sourceTree.setOpener(f -> open(new Location(f, 1, 1)));
		console.setOpener(l -> open(l));
		problemsPane.setOpener(l -> open(l));
		junitPane.setOpener(l -> open(l));
		junitPane.setRunner(specs -> runSpecs(specs));
		console.setInputSink(s ->
		{
			if(launcher != null)
			{
				launcher.sendInput(s);
			}
		});
		shell.addListener(SWT.Close, e -> e.doit = confirmExit());
		applyTheme();
	}

	public Shell getShell()
	{
		return shell;
	}

	void addToolItems(Composite bar)
	{
		item(bar, "Open Project", () -> chooseProject());
		item(bar, "Save All", () -> editors.saveAll());
		Label separator = new Label(bar, SWT.SEPARATOR | SWT.VERTICAL);
		separator.setLayoutData(new RowData(2, 22));
		item(bar, "Build", () -> build(null));
		item(bar, "Run", () -> runActive());
		item(bar, "Run Tests", () -> runTests());
		item(bar, "Stop", () -> stop());
	}

	void item(Composite bar, String label, Runnable action)
	{
		Button b = new Button(bar, SWT.PUSH);
		b.setText(label);
		b.addListener(SWT.Selection, e -> action.run());
	}

	void menuItem(Menu menu, String label, int accel, Runnable action)
	{
		MenuItem mi = new MenuItem(menu, SWT.PUSH);
		mi.setText(accel == 0 ? label : label + "\t" + accelName(accel));
		if(accel != 0)
		{
			mi.setAccelerator(accel);
		}
		mi.addListener(SWT.Selection, e -> action.run());
		if(menuName != null)
		{
			commands.add(menuName + " > " + label, action);
		}
	}

	static String accelName(int accel)
	{
		String s = "Ctrl+";
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
			return "F" + (key - SWT.F1 + 1);
		}
		return s + (char)key;
	}

	void buildMenus()
	{
		Menu bar = new Menu(shell, SWT.BAR);
		shell.setMenuBar(bar);
		Menu file = submenu(bar, "File");
		menuItem(file, "Open Project...", SWT.MOD1 | 'O', () -> chooseProject());
		menuItem(file, "Run Command / Go to File...", SWT.MOD1 | '3', () -> runCommandDialog());
		menuItem(file, "Regenerate Project File...", 0, () -> regenerateProject());
		menuItem(file, "Save", SWT.MOD1 | 'S', () -> saveActive());
		menuItem(file, "Save All", SWT.MOD1 | SWT.SHIFT | 'S', () -> editors.saveAll());
		menuItem(file, "Close Tab", SWT.MOD1 | 'W', () -> editors.closeActive());
		menuItem(file, "Exit", 0, () -> shell.close());
		Menu edit = submenu(bar, "Edit");
		menuItem(edit, "Find...", SWT.MOD1 | 'F', () -> find());
		menuItem(edit, "Go to Line...", SWT.MOD1 | 'L', () -> gotoLine());
		menuItem(edit, "Go to Matching Bracket", SWT.MOD1 | SWT.SHIFT | 'P', () -> goToMatchingBracket());
		Menu view = submenu(bar, "View");
		MenuItem darkItem = new MenuItem(view, SWT.CHECK);
		darkItem.setText("Dark Mode");
		darkItem.setSelection(theme.dark);
		darkItem.addListener(SWT.Selection, e -> setDark(darkItem.getSelection()));
		commands.add("View > Dark Mode", () ->
		{
			darkItem.setSelection(!darkItem.getSelection());
			setDark(darkItem.getSelection());
		});
		commands.add("View > Show Project", () -> left.setSelection(projectTab));
		commands.add("View > Show Sources", () -> left.setSelection(sourceTab));
		commands.add("View > Show Problems", () -> bottom.setSelection(problemsTab));
		commands.add("View > Show Console", () -> bottom.setSelection(consoleTab));
		commands.add("View > Show JUnit", () -> bottom.setSelection(junitTab));
		Menu run = submenu(bar, "Run");
		menuItem(run, "Build", SWT.MOD1 | 'B', () -> build(null));
		menuItem(run, "Build and Run", SWT.F11, () -> runActive());
		menuItem(run, "Run", SWT.MOD1 | 'R', () -> runActive());
		menuItem(run, "Run Tests", SWT.MOD1 | SWT.SHIFT | 'R', () -> runTests());
		menuItem(run, "Run Test at Cursor", SWT.MOD1 | SWT.ALT | 'R', () -> runTestAtCursor());
		menuItem(run, "Re-run Failed Tests", 0, () -> runSpecs(junitPane.failedSpecs()));
		menuItem(run, "Program Arguments...", 0, () -> editArguments());
		menuItem(run, "Stop", 0, () -> stop());
		menuName = null;
		Menu treeMenu = new Menu(shell, SWT.POP_UP);
		projectTree.getControl().setMenu(treeMenu);
		sourceTree.getControl().setMenu(treeMenu);
		menuItem(treeMenu, "Run", 0, () -> runSelected());
		menuItem(treeMenu, "Run Tests", 0, () -> runTestsIn(activeTree().selected()));
		menuItem(treeMenu, "New File...", 0, () -> newFile());
		menuItem(treeMenu, "New Folder...", 0, () -> newFolder());
		menuItem(treeMenu, "Delete", 0, () -> deleteSelected());
		menuItem(treeMenu, "Refresh", 0, () -> refreshTrees());
	}

	void applyTheme()
	{
		CTabFolder[] folders = {editors.getControl(), bottom, left};
		for(CTabFolder f : folders)
		{
			f.setBackground(theme.chrome);
			f.setForeground(theme.foreground);
			f.setSelectionBackground(theme.background);
			f.setSelectionForeground(theme.foreground);
		}
		projectTree.restyle(theme);
		sourceTree.restyle(theme);
		problemsPane.restyle(theme);
		console.restyle();
		junitPane.restyle();
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

	void refreshTrees()
	{
		projectTree.refresh();
		sourceTree.refresh();
	}

	Menu submenu(Menu bar, String label)
	{
		menuName = label;
		MenuItem mi = new MenuItem(bar, SWT.CASCADE);
		mi.setText(label);
		Menu menu = new Menu(shell, SWT.DROP_DOWN);
		mi.setMenu(menu);
		return menu;
	}

	public void chooseProject()
	{
		org.eclipse.swt.widgets.DirectoryDialog dd = new org.eclipse.swt.widgets.DirectoryDialog(shell);
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
		settings = new LaunchSettings(project.root);
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
			build(null);
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
		all.addAll(commands);
		if(project != null)
		{
			List<File> files = new ArrayList<File>();
			listFiles(project.root, files);
			for(File f : files)
			{
				all.add("Open: " + project.relative(f).replace(File.separatorChar, '/'), () -> open(new Location(f, 1, 1)));
			}
		}
		lastCommands = all;
		return new CommandDialog(shell, text -> all.find(text, 300));
	}

	void runCommandDialog()
	{
		String choice = commandDialog().open();
		if(choice != null)
		{
			lastCommands.run(choice);
		}
	}

	static final java.util.Set<String> BINARY = new java.util.HashSet<String>(java.util.Arrays.asList(
		"class", "jar", "zip", "gz", "png", "jpg", "jpeg", "gif", "bmp", "ico", "so", "dll", "exe", "pdf", "wav", "mp3",
		"ogg", "iso", "grp", "wad", "dylib", "o", "a", "tar", "7z"));

	void listFiles(File dir, List<File> out)
	{
		File[] children = dir.listFiles();
		if(children == null || out.size() > 50000)
		{
			return;
		}
		java.util.Arrays.sort(children);
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
		}
	}

	public void build(Runnable then)
	{
		if(project == null)
		{
			return;
		}
		if(!editors.saveAll())
		{
			return;
		}
		if(busy)
		{
			return;
		}
		busy = true;
		console.append("Building " + project.name + "...\n", false);
		EclipseProject p = project;
		Thread t = new Thread(() ->
		{
			List<Problem> problems = ProjectCompiler.compile(p);
			display.asyncExec(() ->
			{
				busy = false;
				showProblems(problems);
				if(then != null)
				{
					then.run();
				}
				done();
			});
		}, "compile");
		t.setDaemon(true);
		t.start();
	}

	void showProblems(List<Problem> problems)
	{
		problemsPane.setProblems(problems);
		editors.setProblems(problems);
		int errors = problemsPane.errorCount();
		problemsTab.setText("Problems (" + problemsPane.count() + ")");
		console.append(errors == 0 ? "Build OK\n" : "Build failed: " + errors + " error(s)\n", errors > 0);
		if(errors > 0)
		{
			bottom.setSelection(problemsTab);
		}
	}

	void done()
	{
		if(idleHook != null && !busy && (launcher == null || !launcher.isRunning()))
		{
			Runnable r = idleHook;
			idleHook = null;
			r.run();
		}
	}

	boolean hasErrors()
	{
		return problemsPane.errorCount() > 0;
	}

	String contentOf(File f)
	{
		try
		{
			return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
		}
		catch(Exception e)
		{
			return "";
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

	public void runActive()
	{
		runFrom(currentFile());
	}

	void runSelected()
	{
		runFrom(activeTree().selected());
	}

	void runFrom(File f)
	{
		if(project == null)
		{
			error("Open a project first.");
			return;
		}
		if(f != null && f.isFile() && (TestFinder.isTestClass(contentOf(f)) || MainFinder.hasMain(f)))
		{
			launchMain(f);
			return;
		}
		List<File> mains = MainFinder.findAll(project.sourceFolders);
		if(mains.size() == 1)
		{
			launchMain(mains.get(0));
			return;
		}
		if(mains.isEmpty())
		{
			error("No class with a main method found in " + project.name);
			return;
		}
		StringBuilder sb = new StringBuilder("Several classes have a main method; open or select one and press Run:\n");
		for(File m : mains)
		{
			sb.append("\n").append(project.classNameOf(m));
		}
		error(sb.toString());
	}

	void launchMain(File file)
	{
		if(project == null || file == null || !file.getName().endsWith(".java"))
		{
			error("Select or open a Java file with a main method.");
			return;
		}
		String cls = project.classNameOf(file);
		if(cls == null)
		{
			error("File is not in a source folder.");
			return;
		}
		if(TestFinder.isTestClass(contentOf(file)))
		{
			runTestsIn(file);
			return;
		}
		build(() ->
		{
			if(hasErrors())
			{
				return;
			}
			startProcess(l -> l.mainCommand(cls, settings.getVmArgs(cls), settings.getArgs(cls)));
		});
	}

	void startProcess(java.util.function.Function<Launcher, List<String>> command)
	{
		stop();
		testRunDir = null;
		launch(command);
	}

	void launch(java.util.function.Function<Launcher, List<String>> command)
	{
		bottom.setSelection(consoleTab);
		Launcher[] self = new Launcher[1];
		self[0] = launcher = new Launcher(project, new OutputListener()
		{
			public void output(String text, boolean error)
			{
				display.asyncExec(() ->
				{
					if(!console.getControl().isDisposed())
					{
						console.append(text, error);
					}
				});
			}

			public void finished(int exitCode)
			{
				display.asyncExec(() ->
				{
					if(console.getControl().isDisposed())
					{
						return;
					}
					console.append("\n<exited with code " + exitCode + ">\n", false);
					if(launcher != self[0])
					{
						return;
					}
					collectReports();
					if(!pending.isEmpty())
					{
						launch(pending.remove(0));
						return;
					}
					finishTestRun();
					done();
				});
			}
		});
		launcher.start(command.apply(launcher));
	}

	File newRunDir()
	{
		stopLive();
		try
		{
			testRunDir = java.nio.file.Files.createTempDirectory("javapad-tests").toFile();
		}
		catch(Exception e)
		{
			testRunDir = new File(project.root, ".javapad/tests");
			testRunDir.mkdirs();
		}
		parsedReports.clear();
		allResults.clear();
		junitPane.beginLive();
		junitTab.setText("JUnit");
		liveEvents.clear();
		liveTail = new LiveTail(Launcher.liveLog(testRunDir), fields ->
		{
			liveEvents.add(fields);
			display.asyncExec(() -> drainLive());
		});
		liveTail.start();
		return testRunDir;
	}

	void drainLive()
	{
		String[] fields = liveEvents.poll();
		while(fields != null)
		{
			if(!junitPane.getControl().isDisposed())
			{
				if(bottom.getSelection() != junitTab && junitPane.total() == 0)
				{
					bottom.setSelection(junitTab);
				}
				junitPane.liveEvent(fields);
			}
			fields = liveEvents.poll();
		}
	}

	void stopLive()
	{
		if(liveTail != null)
		{
			liveTail.stop();
			liveTail = null;
		}
		drainLive();
	}

	void collectReports()
	{
		if(testRunDir == null)
		{
			return;
		}
		if(liveTail != null)
		{
			liveTail.flush();
		}
		drainLive();
		List<File> found = new ArrayList<File>();
		findXml(testRunDir, found);
		for(File xml : found)
		{
			if(parsedReports.add(xml))
			{
				try
				{
					allResults.addAll(JUnitReport.parse(xml));
				}
				catch(Exception e)
				{
					console.append("Cannot read test report " + xml.getName() + ": " + e + "\n", true);
				}
			}
		}
		if(!allResults.isEmpty() && pending.isEmpty())
		{
			junitPane.setResults(new ArrayList<TestResult>(allResults));
		}
	}

	void findXml(File dir, List<File> out)
	{
		File[] children = dir.listFiles();
		if(children == null)
		{
			return;
		}
		java.util.Arrays.sort(children);
		for(File f : children)
		{
			if(f.isDirectory())
			{
				findXml(f, out);
			}
			else if(f.getName().endsWith(".xml"))
			{
				out.add(f);
			}
		}
	}

	void finishTestRun()
	{
		if(testRunDir == null)
		{
			return;
		}
		stopLive();
		int failed = junitPane.failures();
		int total = junitPane.total();
		junitTab.setText(total == 0 ? "JUnit" : failed == 0 ? "JUnit (" + total + " passed)" : "JUnit (" + failed + " of " + total + " failed)");
		bottom.setSelection(junitTab);
		deleteRecursive(testRunDir);
		testRunDir = null;
	}

	public void runTests()
	{
		runTestsIn(currentFile());
	}

	void runTestsIn(File target)
	{
		if(project == null)
		{
			error("Open a project first.");
			return;
		}
		List<File> files = TestFinder.find(target, project.sourceFolders);
		if(files.isEmpty())
		{
			files = TestFinder.findAll(project.sourceFolders);
		}
		if(files.isEmpty())
		{
			console.append("No JUnit test classes found in " + project.name + "\n", true);
			error("No JUnit test classes found in " + project.name);
			return;
		}
		List<String> specs = new ArrayList<String>();
		for(File f : files)
		{
			String cls = project.classNameOf(f);
			if(cls != null)
			{
				specs.add(cls);
			}
		}
		runSpecs(specs);
	}

	public void runTestAtCursor()
	{
		EditorTab tab = editors.active();
		String cls = tab == null || project == null ? null : project.classNameOf(tab.file);
		if(cls == null || !TestFinder.isTestClass(tab.editor.getContent()))
		{
			error("Put the cursor in a JUnit test method.");
			return;
		}
		String method = TestFinder.methodAt(tab.editor.getContent(), tab.editor.getControl().getCaretOffset());
		runSpecs(java.util.Arrays.asList(method == null ? cls : cls + "#" + method));
	}

	void goToMatchingBracket()
	{
		EditorTab tab = editors.active();
		if(tab != null)
		{
			tab.editor.goToMatchingBracket();
		}
	}

	// specs: class names, or class#method for a single test
	public void runSpecs(List<String> specs)
	{
		if(project == null)
		{
			error("Open a project first.");
			return;
		}
		if(specs.isEmpty())
		{
			console.append("No tests to run\n", true);
			return;
		}
		build(() ->
		{
			if(hasErrors())
			{
				return;
			}
			List<String> four = new ArrayList<String>();
			List<String> five = new ArrayList<String>();
			for(String spec : specs)
			{
				int hash = spec.indexOf('#');
				File f = project.sourceFor(hash < 0 ? spec : spec.substring(0, hash));
				if(f != null && TestFinder.isJupiter(contentOf(f)))
				{
					five.add(spec);
				}
				else
				{
					four.add(spec);
				}
			}
			stop();
			pending.clear();
			File runDir = newRunDir();
			if(!five.isEmpty())
			{
				pending.add(l -> l.junitCommand(five, true, runDir));
			}
			if(!four.isEmpty())
			{
				launch(l -> l.junitCommand(four, false, runDir));
			}
			else if(!pending.isEmpty())
			{
				launch(pending.remove(0));
			}
		});
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

	public void stop()
	{
		pending.clear();
		if(launcher != null && launcher.isRunning())
		{
			launcher.stop();
		}
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
			File f = new File(dir, name);
			f.getParentFile().mkdirs();
			f.createNewFile();
			refreshTrees();
			open(new Location(f, 1, 1));
		}
		catch(Exception e)
		{
			error(e.getMessage());
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
		refreshTrees();
	}

	File targetDir()
	{
		File f = activeTree().selected();
		if(f == null)
		{
			return project == null ? null : project.root;
		}
		return f.isDirectory() ? f : f.getParentFile();
	}

	void deleteSelected()
	{
		File f = activeTree().selected();
		if(f == null || project == null || f.equals(project.root))
		{
			return;
		}
		if(ConfirmDialog.confirm(shell, "Delete", "Delete " + f.getName() + (f.isDirectory() ? " and everything in it?" : "?")))
		{
			deleteRecursive(f);
			refreshTrees();
		}
	}

	void deleteRecursive(File f)
	{
		File[] children = f.listFiles();
		if(children != null)
		{
			for(File c : children)
			{
				deleteRecursive(c);
			}
		}
		f.delete();
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
		org.eclipse.swt.graphics.ImageLoader loader = new org.eclipse.swt.graphics.ImageLoader();
		loader.data = new org.eclipse.swt.graphics.ImageData[] {image.getImageData()};
		loader.save(path, SWT.IMAGE_PNG);
		image.dispose();
	}

	void applyTabProperty()
	{
		String tab = System.getProperty("javapad.tab", "");
		if(tab.equals("problems"))
		{
			bottom.setSelection(problemsTab);
		}
		else if(tab.equals("junit"))
		{
			bottom.setSelection(junitTab);
		}
		else if(tab.equals("console"))
		{
			bottom.setSelection(consoleTab);
		}
	}

	public void automate()
	{
		String open = System.getProperty("javapad.open");
		if(open != null)
		{
			openProject(new File(open));
		}
		String file = System.getProperty("javapad.file");
		if(file != null)
		{
			File f = new File(file);
			if(!f.isAbsolute() && project != null)
			{
				f = new File(project.root, file);
			}
			int line = Integer.getInteger("javapad.line", 1);
			open(new Location(f, line, 1));
		}
		if(System.getProperty("javapad.select", "").equals("root"))
		{
			projectTree.selectRoot();
		}
		String action = System.getProperty("javapad.action", "");
		String shot = System.getProperty("javapad.shot");
		int wait = Integer.getInteger("javapad.wait", 1200);
		Runnable finish = () ->
		{
			applyTabProperty();
			display.timerExec(wait, () ->
			{
				if(shot != null)
				{
					screenshot(shot);
					shell.dispose();
				}
			});
		};
		if(System.getProperty("javapad.tree", "").equals("sources"))
		{
			left.setSelection(sourceTab);
		}
		applyTabProperty();
		String early = System.getProperty("javapad.early", "");
		if(shot != null && !early.isEmpty())
		{
			// extra screenshots at the given milliseconds (comma separated), taken while a run is still going
			for(String ms : early.split(","))
			{
				display.timerExec(Integer.parseInt(ms.trim()), () -> screenshot(shot + ".early" + ms.trim() + ".png"));
			}
		}
		if(action.equals("build"))
		{
			build(finish);
		}
		else if(action.equals("command"))
		{
			CommandDialog dialog = commandDialog();
			String text = System.getProperty("javapad.commandtext", "");
			boolean choose = Boolean.getBoolean("javapad.commandrun");
			String choice = dialog.open(() ->
			{
				dialog.setText(text);
				display.timerExec(300, () ->
				{
					if(choose)
					{
						dialog.accept();
					}
					else
					{
						if(shot != null)
						{
							screenshot(shot + ".dlg.png");
						}
						dialog.close();
					}
				});
			});
			if(choice != null)
			{
				lastCommands.run(choice);
			}
			finish.run();
		}
		else if(action.equals("f11"))
		{
			idleHook = finish;
			Event down = new Event();
			down.type = SWT.KeyDown;
			down.keyCode = SWT.F11;
			display.post(down);
			Event up = new Event();
			up.type = SWT.KeyUp;
			up.keyCode = SWT.F11;
			display.post(up);
		}
		else if(action.equals("run"))
		{
			idleHook = finish;
			runActive();
		}
		else if(action.equals("test"))
		{
			idleHook = finish;
			runTests();
		}
		else if(action.equals("testcursor"))
		{
			idleHook = finish;
			runTestAtCursor();
		}
		else if(action.equals("rerunfailed"))
		{
			// run everything, then re-run only what failed
			idleHook = () ->
			{
				idleHook = finish;
				runSpecs(junitPane.failedSpecs());
			};
			runTests();
		}
		else if(shot != null)
		{
			finish.run();
		}
	}
}
