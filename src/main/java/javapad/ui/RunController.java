package javapad.ui;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javapad.build.ProjectCompiler;
import javapad.model.EclipseProject;
import javapad.model.Problem;
import javapad.run.HotSwap;
import javapad.run.JUnitReport;
import javapad.run.LaunchSettings;
import javapad.run.Launcher;
import javapad.run.LiveTail;
import javapad.run.MainFinder;
import javapad.run.OutputListener;
import javapad.run.TestFinder;
import javapad.run.TestResult;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolItem;

// Builds the project and runs its programs and tests, feeding output to the console, problems and JUnit panes.
class RunController
{
	private final Display display;
	private final Shell shell;
	private final EditorTabs editors;
	private final ConsolePane console;
	private final ProblemsPane problemsPane;
	private final JUnitPane junitPane;
	private final CodeAssist assist;
	private final BottomTabs bottom;
	private final BottomTabs.Tab consoleTab;
	private final BottomTabs.Tab problemsTab;
	private final CTabItem junitTab;
	// repeats the last main class or test run; null until something has been launched
	private Runnable lastLaunch;
	private final Supplier<File> currentFile;
	private final Runnable beforeBuild;
	private final Consumer<String> errorSink;
	private final Set<File> parsedReports = new HashSet<File>();
	private final List<TestResult> allResults = new ArrayList<TestResult>();
	private final ConcurrentLinkedQueue<String[]> liveEvents = new ConcurrentLinkedQueue<String[]>();
	private final List<Function<Launcher, List<String>>> pending = new ArrayList<Function<Launcher, List<String>>>();
	private EclipseProject project;
	private LaunchSettings settings;
	private File testRunDir;
	private LiveTail liveTail;
	private Launcher launcher;
	private ToolItem stopItem;
	private boolean busy;
	private Runnable idleHook;
	// connected to the running main program while hot swap is on; null otherwise
	private HotSwap hotSwap;
	private boolean hotSwapOn = HotSwap.enabled();

	RunController(Display display, Shell shell, EditorTabs editors, ConsolePane console, ProblemsPane problemsPane, JUnitPane junitPane, CodeAssist assist,
		BottomTabs bottom, BottomTabs.Tab consoleTab, BottomTabs.Tab problemsTab, CTabItem junitTab, Supplier<File> currentFile, Runnable beforeBuild, Consumer<String> errorSink)
	{
		this.display = display;
		this.shell = shell;
		this.editors = editors;
		this.console = console;
		this.problemsPane = problemsPane;
		this.junitPane = junitPane;
		this.assist = assist;
		this.bottom = bottom;
		this.consoleTab = consoleTab;
		this.problemsTab = problemsTab;
		this.junitTab = junitTab;
		this.currentFile = currentFile;
		this.beforeBuild = beforeBuild;
		this.errorSink = errorSink;
	}

	void setProject(EclipseProject project, LaunchSettings settings)
	{
		this.project = project;
		this.settings = settings;
		lastLaunch = null;
	}

	void setStopItem(ToolItem item)
	{
		stopItem = item;
		updateStop();
	}

	// the callback that runs once the build and any process have finished (used by the headless automation)
	void setIdleHook(Runnable hook)
	{
		idleHook = hook;
	}

	boolean hotSwapOn()
	{
		return hotSwapOn;
	}

	// Turns hot swapping on or off for the programs started from now on, and remembers the choice.
	void setHotSwap(boolean on)
	{
		hotSwapOn = on;
		HotSwap.setEnabled(on);
		console.append(on ? "Hot swap on: saved changes go into the running program (restart a program started before this)\n" : "Hot swap off\n", false);
	}

	void sendInput(String text)
	{
		if(launcher != null)
		{
			launcher.sendInput(text);
		}
	}

	void error(String message)
	{
		errorSink.accept(message);
	}

	// the stop button is only live while a process is running
	void updateStop()
	{
		if(stopItem != null && !stopItem.isDisposed())
		{
			stopItem.setEnabled(launcher != null && launcher.isRunning());
		}
	}

	void runActive()
	{
		runFrom(currentFile.get());
	}

	// Repeats the last main class or test run; the first time, it runs what the active file suggests.
	void runLast()
	{
		if(lastLaunch == null)
		{
			runActive();
			return;
		}
		beforeBuild.run();
		lastLaunch.run();
	}

	void build(Runnable then)
	{
		if(project == null)
		{
			return;
		}
		if(!editors.saveAll())
		{
			return;
		}
		beforeBuild.run();
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
				if(then == null)
				{
					hotSwapAfterBuild();
				}
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
		bottom.setText(problemsTab, "Problems (" + problemsPane.count() + ")");
		console.append(errors == 0 ? "Build OK\n" : "Build failed: " + errors + " error(s)\n", errors > 0);
		assist.refresh();
		if(errors > 0)
		{
			bottom.show(problemsTab);
		}
	}

	// after a save or Ctrl+B, puts the new code into the running program
	void hotSwapAfterBuild()
	{
		if(hotSwap == null || launcher == null || !launcher.isRunning())
		{
			return;
		}
		if(hasErrors())
		{
			console.append("Not hot-swapped: the build has errors\n", true);
			return;
		}
		hotSwap.swap();
	}

	// adds the debugging agent to a main program's command and starts attaching to it
	List<String> withHotSwap(List<String> command)
	{
		closeHotSwap();
		if(!hotSwapOn || HotSwap.hasAgent(command))
		{
			return command;
		}
		HotSwap swap = new HotSwap(project.outputFolder, (text, error) -> display.asyncExec(() ->
		{
			if(!console.getControl().isDisposed())
			{
				console.append(text, error);
			}
		}));
		List<String> result = new ArrayList<String>(command);
		result.add(1, swap.agentArgument());
		hotSwap = swap;
		swap.attach();
		return result;
	}

	void closeHotSwap()
	{
		if(hotSwap != null)
		{
			hotSwap.close();
			hotSwap = null;
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

	void runFrom(File f)
	{
		if(project == null)
		{
			error("Open a project first.");
			return;
		}
		beforeBuild.run();
		if(f != null && f.isFile() && TestFinder.isTestClass(FileUtil.contentOf(f)))
		{
			runTestsIn(f);
			return;
		}
		List<MainFinder.Main> mains = new ArrayList<MainFinder.Main>();
		if(f != null && f.isFile() && f.getName().endsWith(".java"))
		{
			EditorTab tab = tabFor(f);
			mains = tab != null ? MainFinder.inSource(f, tab.editor.getContent()) : MainFinder.inFile(f);
		}
		if(mains.isEmpty())
		{
			mains = allMains();
		}
		if(mains.isEmpty())
		{
			error("No class with a main method found in " + project.name);
			return;
		}
		runOneOf(mains);
	}

	EditorTab tabFor(File f)
	{
		for(EditorTab tab : editors.tabs())
		{
			if(tab.file.equals(f))
			{
				return tab;
			}
		}
		return null;
	}

	// Every main in the project, using the text of files that have unsaved changes.
	List<MainFinder.Main> allMains()
	{
		Map<File, String> unsaved = new HashMap<File, String>();
		for(EditorTab tab : editors.tabs())
		{
			if(tab.isDirty() && tab.file.getName().endsWith(".java"))
			{
				unsaved.put(tab.file, tab.editor.getContent());
			}
		}
		return MainFinder.inProject(project.sourceFolders, unsaved);
	}

	// Runs the only candidate, or asks which one; the last choice is offered first.
	void runOneOf(List<MainFinder.Main> mains)
	{
		MainFinder.Main pick = mains.get(0);
		if(mains.size() > 1)
		{
			List<MainFinder.Main> ordered = new ArrayList<MainFinder.Main>(mains);
			String last = settings.getLastMain();
			ordered.sort((a, b) -> Boolean.compare(!b.className.equals(last), !a.className.equals(last)));
			List<String> labels = new ArrayList<String>();
			for(MainFinder.Main m : ordered)
			{
				labels.add(m.label());
			}
			String choice = ChoiceDialog.choose(shell, "Run which main class?", labels);
			if(choice == null)
			{
				return;
			}
			pick = ordered.get(labels.indexOf(choice));
		}
		launchMain(pick);
	}

	void runMainClass()
	{
		if(project == null)
		{
			error("Open a project first.");
			return;
		}
		List<MainFinder.Main> mains = allMains();
		if(mains.isEmpty())
		{
			error("No class with a main method found in " + project.name);
			return;
		}
		runOneOf(mains);
	}

	void launchMain(MainFinder.Main main)
	{
		String cls = main.className;
		lastLaunch = () -> launchMain(main);
		settings.setLastMain(cls);
		build(() ->
		{
			if(hasErrors())
			{
				return;
			}
			startProcess(l -> withHotSwap(l.mainCommand(cls, settings.getVmArgs(cls), settings.getArgs(cls))));
		});
	}

	void startProcess(Function<Launcher, List<String>> command)
	{
		stop();
		testRunDir = null;
		launch(command);
	}

	void launch(Function<Launcher, List<String>> command)
	{
		bottom.show(consoleTab);
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
					updateStop();
					if(launcher != self[0])
					{
						return;
					}
					closeHotSwap();
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
		updateStop();
	}

	File newRunDir()
	{
		stopLive();
		try
		{
			testRunDir = Files.createTempDirectory("javapad-tests").toFile();
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
				if(junitTab.getParent().getSelection() != junitTab && junitPane.total() == 0)
				{
					junitTab.getParent().setSelection(junitTab);
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
					e.printStackTrace();
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
		Arrays.sort(children);
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
		junitTab.getParent().setSelection(junitTab);
		FileUtil.deleteRecursive(testRunDir);
		testRunDir = null;
	}

	void runTests()
	{
		runTestsIn(currentFile.get());
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

	void runTestAtCursor()
	{
		EditorTab tab = editors.active();
		String cls = tab == null || project == null ? null : project.classNameOf(tab.file);
		if(cls == null || !TestFinder.isTestClass(tab.editor.getContent()))
		{
			error("Put the cursor in a JUnit test method.");
			return;
		}
		String method = TestFinder.methodAt(tab.editor.getContent(), tab.editor.getControl().getCaretOffset());
		runSpecs(Arrays.asList(method == null ? cls : cls + "#" + method));
	}

	// specs: class names, or class#method for a single test
	void runSpecs(List<String> specs)
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
		lastLaunch = () -> runSpecs(specs);
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
				if(f != null && TestFinder.isJupiter(FileUtil.contentOf(f)))
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

	void stop()
	{
		pending.clear();
		closeHotSwap();
		if(launcher != null && launcher.isRunning())
		{
			launcher.stop();
		}
	}
}
