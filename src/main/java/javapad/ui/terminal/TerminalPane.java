package javapad.ui.terminal;

import javapad.ui.Theme;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javapad.model.EclipseProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

// The Terminal tab: the user's shell ($SHELL, as a login shell) on a pseudo-terminal, started in the project folder the first time the tab is shown.
public class TerminalPane {
	private final TerminalView view;
	private final Display display;
	private PtyProcess process;
	private OutputStream out;
	private File dir;
	private boolean started;
	private boolean disposed;

	public TerminalPane(Composite parent, Theme theme) {
		display = parent.getDisplay();
		view = new TerminalView(parent, theme);
		view.setInput(s -> input(s));
		view.setResizeListener((cols, rows) -> resized(cols, rows));
		view.screen().setReply(s -> write(s));
		view.control().addListener(SWT.Dispose, e -> {
			disposed = true;
			stop();
		});
	}

	public Control getControl() {
		return view.control();
	}

	public void restyle() {
		view.restyle();
	}

	// called with true when the terminal has the keyboard: its keys, such as Ctrl+R, should reach the shell and not the menus
	public void setFocusListener(Consumer<Boolean> listener) {
		view.setFocusListener(listener);
	}

	// a shell that is doing nothing moves to the new project's folder; one that is running something is left alone
	public void setProject(EclipseProject project) {
		File old = dir;
		dir = project == null ? null : project.root;
		if(started && dir != null && !dir.equals(old) && idle()) {
			stop();
			process = null;
			start();
		}
	}

	boolean idle() {
		if(process == null || !process.isAlive()) {
			return true;
		}
		return ProcessHandle.of(process.pid()).map(h -> h.children().count() == 0).orElse(true);
	}

	public void ensureStarted() {
		started = true;
		if(process == null) {
			start();
		}
	}

	void start() {
		TerminalScreen screen = view.screen();
		screen.feed("\u001bc");
		Map<String, String> env = new HashMap<String, String>(System.getenv());
		env.put("TERM", "xterm-256color");
		env.put("COLORTERM", "truecolor");
		String shell = System.getenv("SHELL");
		if(shell == null || shell.isEmpty()) {
			shell = "/bin/sh";
		}
		File where = dir == null || !dir.isDirectory() ? new File(System.getProperty("user.home")) : dir;
		try {
			PtyProcess p = new PtyProcessBuilder(new String[] {shell, "-l"}).setEnvironment(env).setDirectory(where.getPath()).setInitialColumns(screen.cols()).setInitialRows(screen.rows()).start();
			process = p;
			out = p.getOutputStream();
			Thread t = new Thread(() -> read(p), "terminal-reader");
			t.setDaemon(true);
			t.start();
		} catch(IOException | RuntimeException e) {
			e.printStackTrace();
			screen.feed("Cannot start " + shell + ": " + e.getMessage() + "\r\n");
			view.invalidate();
		}
	}

	void read(PtyProcess p) {
		try(Reader in = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)) {
			char[] buffer = new char[8192];
			int n = in.read(buffer);
			while(n >= 0) {
				view.screen().feed(new String(buffer, 0, n));
				view.invalidate();
				n = in.read(buffer);
			}
		} catch(IOException e) {
			// the shell went away
		}
		display.asyncExec(() -> ended(p));
	}

	void ended(PtyProcess p) {
		if(disposed || p != process) {
			return;
		}
		view.screen().feed("\r\n[the shell exited; press a key to start a new one]\r\n");
		view.invalidate();
		process = null;
	}

	// keys and pastes; once the shell has exited the next key starts a new one
	void input(String s) {
		if(process == null) {
			start();
			return;
		}
		write(s);
	}

	synchronized void write(String s) {
		try {
			if(out != null) {
				out.write(s.getBytes(StandardCharsets.UTF_8));
				out.flush();
			}
		} catch(IOException e) {
			// the shell is gone; the reader notices
		}
	}

	void resized(int cols, int rows) {
		if(process != null && process.isAlive()) {
			process.setWinSize(new WinSize(cols, rows));
		}
	}

	public void stop() {
		if(process != null) {
			ProcessHandle.of(process.pid()).ifPresent(h -> h.descendants().forEach(d -> d.destroyForcibly()));
			process.destroyForcibly();
		}
	}
}
