package javapad.ui.action;

import javapad.ui.MainWindow;
import javapad.ui.dialog.CommandDialog;
import javapad.ui.dialog.QuickOutlineDialog;
import javapad.ui.editor.EditorTab;
import javapad.ui.editor.Location;

import java.io.File;
import javapad.lang.Outline;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;

// Scripted start-up for headless runs and screenshots, driven by -Djavapad.* system properties.
public class Automation {
	static final String OPEN = "javapad.open";
	static final String FILE = "javapad.file";
	static final String LINE = "javapad.line";
	static final String SELECT = "javapad.select";
	static final String ACTION = "javapad.action";
	static final String SHOT = "javapad.shot";
	static final String WAIT = "javapad.wait";
	static final String TREE = "javapad.tree";
	static final String TAB = "javapad.tab";
	static final String EARLY = "javapad.early";
	static final String COMMAND_TEXT = "javapad.commandtext";
	static final String COMMAND_RUN = "javapad.commandrun";

	private final MainWindow window;
	private final Display display;

	public Automation(MainWindow window) {
		this.window = window;
		this.display = window.display();
	}

	public void run() {
		String open = System.getProperty(OPEN);
		if(open != null) {
			window.openProject(new File(open));
		}
		String file = System.getProperty(FILE);
		if(file != null) {
			File f = new File(file);
			if(!f.isAbsolute() && window.project() != null) {
				f = new File(window.project().root, file);
			}
			window.open(new Location(f, Integer.getInteger(LINE, 1), 1));
		}
		if(System.getProperty(SELECT, "").equals("root")) {
			window.selectProjectRoot();
		}
		String action = System.getProperty(ACTION, "");
		String shot = System.getProperty(SHOT);
		Runnable finish = finisher(shot, Integer.getInteger(WAIT, 1200));
		if(System.getProperty(TREE, "").equals("sources")) {
			window.showSourcesTree();
		}
		window.showBottomTab(System.getProperty(TAB, ""));
		earlyShots(shot);
		RunController run = window.runner();
		if(action.equals("build")) {
			run.build(finish);
		} else if(action.equals("command")) {
			command(shot);
			finish.run();
		} else if(action.equals("method")) {
			method(shot);
			finish.run();
		} else if(action.equals("f11")) {
			run.setIdleHook(finish);
			press(SWT.F11);
		} else if(action.equals("run")) {
			run.setIdleHook(finish);
			run.runActive();
		} else if(action.equals("test")) {
			run.setIdleHook(finish);
			run.runTests();
		} else if(action.equals("testcursor")) {
			run.setIdleHook(finish);
			run.runTestAtCursor();
		} else if(action.equals("rerunfailed")) {
			// run everything, then re-run only what failed
			run.setIdleHook(() -> {
				run.setIdleHook(finish);
				run.runSpecs(window.junit().failedSpecs());
			});
			run.runTests();
		} else if(shot != null) {
			finish.run();
		}
	}

	// shows the bottom tab, waits, then takes the screenshot and closes the window
	Runnable finisher(String shot, int wait) {
		return () -> {
			window.showBottomTab(System.getProperty(TAB, ""));
			display.timerExec(wait, () -> {
				if(shot != null) {
					window.screenshot(shot);
					window.getShell().dispose();
				}
			});
		};
	}

	// extra screenshots at the given milliseconds (comma separated), taken while a run is still going
	void earlyShots(String shot) {
		String early = System.getProperty(EARLY, "");
		if(shot == null || early.isEmpty()) {
			return;
		}
		for(String ms : early.split(",")) {
			display.timerExec(Integer.parseInt(ms.trim()), () -> window.screenshot(shot + ".early" + ms.trim() + ".png"));
		}
	}

	void command(String shot) {
		CommandDialog dialog = window.commandDialog();
		String text = System.getProperty(COMMAND_TEXT, "");
		boolean choose = Boolean.getBoolean(COMMAND_RUN);
		String choice = dialog.open(() -> {
			dialog.setText(text);
			display.timerExec(300, () -> {
				if(choose) {
					dialog.accept();
				} else {
					if(shot != null) {
						window.screenshot(shot + ".dlg.png");
					}
					dialog.close();
				}
			});
		});
		if(choice != null) {
			window.runCommand(choice);
		}
	}

	// Ctrl+O: shows the go-to-method popup, filtered by javapad.commandtext, and opens the choice with javapad.commandrun
	void method(String shot) {
		EditorTab tab = window.editors.active();
		if(tab == null) {
			return;
		}
		QuickOutlineDialog dialog = window.navigation.methodDialog(tab);
		String text = System.getProperty(COMMAND_TEXT, "");
		boolean choose = Boolean.getBoolean(COMMAND_RUN);
		Outline.Node chosen = dialog.open(() -> {
			dialog.setText(text);
			display.timerExec(300, () -> {
				if(choose) {
					dialog.accept();
				} else {
					if(shot != null) {
						window.screenshot(shot + ".dlg.png");
					}
					dialog.close();
				}
			});
		});
		if(chosen != null) {
			window.open(new Location(tab.file, chosen.line, chosen.column));
		}
	}

	void press(int keyCode) {
		Event down = new Event();
		down.type = SWT.KeyDown;
		down.keyCode = keyCode;
		display.post(down);
		Event up = new Event();
		up.type = SWT.KeyUp;
		up.keyCode = keyCode;
		display.post(up);
	}
}
