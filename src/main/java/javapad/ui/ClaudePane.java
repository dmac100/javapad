package javapad.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javapad.model.EclipseProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

// A chat with Claude Code running in the multipass virtual machine "claude". One text holds the whole conversation: your prompts (typed on the last line, sent with Enter) and the answers, in different colours.
// Each prompt is sent, with the path of the project, to "claude -p" in the machine.
public class ClaudePane
{
	static final String MACHINE = "claude";
	static final String COMMAND = "multipass";
	static final String PROMPT = "> ";
	static final int DOT_MILLIS = 400;
	static final int KIND_PROMPT = 0;
	static final int KIND_ANSWER = 1;
	static final int KIND_ERROR = 2;
	static final int KIND_NOTE = 3;

	private final StyledText text;
	private final Theme theme;
	private final Display display;
	private EclipseProject project;
	private Consumer<Boolean> waitingHook;
	// the running request, and a number that tells its output from that of an earlier one
	private Process process;
	private int request;
	private boolean waiting;
	private int dots;
	// whether the next prompt continues the conversation in the machine (after a first answer)
	private boolean continuing;
	// the id of the Claude session that all the prompts of the conversation go to
	private String sessionId = UUID.randomUUID().toString();
	// everything before inputStart is history; what follows is the prompt being typed
	private int inputStart;
	// the start of the prompt being typed (its "> "), or -1 while waiting for an answer
	private int promptStart = -1;
	// the start of the "Waiting for Claude" line at the end of the text, or -1
	private int indicatorStart = -1;
	// set while this class changes the text itself
	private boolean internal;
	// the finished parts of the text: start, length, kind
	private final List<int[]> segments = new ArrayList<int[]>();

	public ClaudePane(Composite parent, Theme theme)
	{
		this.theme = theme;
		this.display = parent.getDisplay();
		text = new StyledText(parent, SWT.V_SCROLL | SWT.WRAP);
		text.setFont(theme.font);
		LineScroll.install(text);
		text.addVerifyListener(e -> verify(e));
		text.addModifyListener(e -> stylePrompt());
		text.addListener(SWT.KeyDown, e -> key(e));
		text.addListener(SWT.Dispose, e -> cancel());
		text.setMenu(menu());
		newPrompt();
	}

	Menu menu()
	{
		Menu menu = new Menu(text);
		MenuItem copy = item(menu, "Copy", () -> text.copy());
		MenuItem paste = item(menu, "Paste", () -> text.paste());
		new MenuItem(menu, SWT.SEPARATOR);
		MenuItem stop = item(menu, "Stop", () -> cancel());
		item(menu, "Clear", () -> clear());
		menu.addListener(SWT.Show, e ->
		{
			copy.setEnabled(text.getSelectionCount() > 0);
			paste.setEnabled(!waiting);
			stop.setEnabled(waiting);
		});
		return menu;
	}

	static MenuItem item(Menu menu, String label, Runnable action)
	{
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(label);
		item.addListener(SWT.Selection, e -> action.run());
		return item;
	}

	public Composite getControl()
	{
		return text;
	}

	public void setProject(EclipseProject project)
	{
		this.project = project;
		newSession();
	}

	// called with true when a request starts and with false when it is answered or stopped
	public void setWaitingHook(Consumer<Boolean> hook)
	{
		waitingHook = hook;
	}

	// Only the prompt at the end can be edited; text typed or pasted anywhere else goes to the end of it.
	void verify(org.eclipse.swt.events.VerifyEvent e)
	{
		if(internal || (!waiting && e.start >= inputStart))
		{
			return;
		}
		e.doit = false;
		if(!waiting && !e.text.isEmpty())
		{
			int end = text.getCharCount();
			internal = true;
			text.replaceTextRange(end, 0, e.text);
			internal = false;
			text.setCaretOffset(end + e.text.length());
		}
	}

	void key(Event e)
	{
		boolean enter = e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR;
		if(enter && (e.stateMask & SWT.SHIFT) == 0)
		{
			e.doit = false;
			send();
		}
	}

	public void focusInput()
	{
		text.setFocus();
		text.setCaretOffset(text.getCharCount());
		text.showSelection();
	}

	// the text sent to Claude: the path of the project, then what was typed
	static String message(String path, String prompt)
	{
		return "The project is at " + path + "\n\n" + prompt;
	}

	// a new conversation: the next prompt starts a session with a new id
	void newSession()
	{
		sessionId = UUID.randomUUID().toString();
		continuing = false;
	}

	// what runs in the machine: it goes to the project folder if the machine has it at the same path, then starts claude in auto mode with the arguments after the path; the prompt is read from stdin
	// The first prompt creates the session with the given id and the later ones resume it, so that they all go to the same session.
	static List<String> command(String path, String sessionId, boolean resume)
	{
		List<String> c = new ArrayList<String>();
		c.add(COMMAND);
		c.add("exec");
		c.add(MACHINE);
		c.add("--");
		c.add("bash");
		c.add("-lc");
		c.add("cd \"$1\" 2>/dev/null; shift; exec claude \"$@\"");
		c.add("claude-pane");
		c.add(path);
		c.add("-p");
		c.add("--permission-mode");
		c.add("auto");
		c.add(resume ? "--resume" : "--session-id");
		c.add(sessionId);
		return c;
	}

	// sends the prompt that was typed on the last line
	void send()
	{
		if(waiting)
		{
			return;
		}
		String prompt = text.getText(inputStart, Math.max(inputStart, text.getCharCount() - 1)).trim();
		if(prompt.isEmpty())
		{
			return;
		}
		if(project == null)
		{
			finishPrompt();
			append("Open a project first.\n", KIND_ERROR);
			newPrompt();
			return;
		}
		String path = project.root.getAbsolutePath();
		finishPrompt();
		int id = ++request;
		boolean resume = continuing;
		String session = sessionId;
		setWaiting(true);
		Thread t = new Thread(() -> talk(id, path, session, message(path, prompt), resume), "claude-pane");
		t.setDaemon(true);
		t.start();
	}

	// the prompt on the last line is done: it keeps its colour and the answer starts on the next line
	void finishPrompt()
	{
		internal = true;
		text.replaceTextRange(text.getCharCount(), 0, "\n");
		internal = false;
		segments.add(new int[] {promptStart, text.getCharCount() - promptStart, KIND_PROMPT});
		promptStart = -1;
		inputStart = text.getCharCount();
	}

	// a blank line and a new "> " to type after
	void newPrompt()
	{
		internal = true;
		int count = text.getCharCount();
		String gap = count == 0 || text.getText().endsWith("\n\n") ? "" : text.getText().endsWith("\n") ? "\n" : "\n\n";
		text.replaceTextRange(count, 0, gap);
		promptStart = text.getCharCount();
		text.replaceTextRange(promptStart, 0, PROMPT);
		internal = false;
		inputStart = text.getCharCount();
		stylePrompt();
		focusInputIfShowing();
	}

	void focusInputIfShowing()
	{
		text.setCaretOffset(text.getCharCount());
		text.showSelection();
	}

	// the prompt being typed has the prompt colour
	void stylePrompt()
	{
		if(promptStart < 0 || text.isDisposed() || promptStart > text.getCharCount())
		{
			return;
		}
		StyleRange style = new StyleRange();
		style.start = promptStart;
		style.length = text.getCharCount() - promptStart;
		style.foreground = color(KIND_PROMPT);
		style.fontStyle = SWT.BOLD;
		text.setStyleRange(style);
	}

	Color color(int kind)
	{
		switch(kind)
		{
			case KIND_PROMPT:
				return theme.keyword;
			case KIND_ERROR:
				return theme.errorText;
			case KIND_NOTE:
				return theme.gutter;
			default:
				return theme.foreground;
		}
	}

	// adds finished text at the end, in the colour of its kind
	void append(String chunk, int kind)
	{
		if(chunk.isEmpty())
		{
			return;
		}
		internal = true;
		int start = text.getCharCount();
		text.replaceTextRange(start, 0, chunk);
		internal = false;
		segments.add(new int[] {start, chunk.length(), kind});
		text.setStyleRange(style(start, chunk.length(), kind));
		inputStart = text.getCharCount();
		focusInputIfShowing();
	}

	StyleRange style(int start, int length, int kind)
	{
		StyleRange style = new StyleRange();
		style.start = start;
		style.length = length;
		style.foreground = color(kind);
		style.fontStyle = kind == KIND_PROMPT ? SWT.BOLD : SWT.NORMAL;
		return style;
	}

	// runs on a thread: starts the process, hands it the prompt and shows what it prints
	void talk(int id, String path, String session, String message, boolean resume)
	{
		StringBuilder errors = new StringBuilder();
		int exit = -1;
		try
		{
			ProcessBuilder pb = new ProcessBuilder(command(path, session, resume));
			Process p = pb.start();
			synchronized(this)
			{
				if(id != request)
				{
					p.destroy();
					return;
				}
				process = p;
			}
			Thread err = new Thread(() -> read(p.getErrorStream(), errors), "claude-pane-err");
			err.setDaemon(true);
			err.start();
			try(OutputStream out = p.getOutputStream())
			{
				out.write(message.getBytes(StandardCharsets.UTF_8));
			}
			catch(IOException e)
			{
				// the process ended before it read everything; its output says why
			}
			InputStream in = p.getInputStream();
			byte[] buffer = new byte[4096];
			StringBuilder pending = new StringBuilder();
			int n;
			while((n = in.read(buffer)) > 0)
			{
				pending.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
				String chunk = pending.toString();
				// keep a split multi-byte character for the next round: only whole text is shown
				if(!chunk.endsWith("�"))
				{
					pending.setLength(0);
					show(id, chunk, false);
				}
			}
			if(pending.length() > 0)
			{
				show(id, pending.toString(), false);
			}
			exit = p.waitFor();
			err.join(2000);
		}
		catch(IOException e)
		{
			errors.append(COMMAND + " could not be started: " + e.getMessage());
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		finish(id, exit, errors.toString().trim());
	}

	static void read(InputStream in, StringBuilder into)
	{
		try
		{
			byte[] buffer = new byte[2048];
			int n;
			while((n = in.read(buffer)) > 0)
			{
				synchronized(into)
				{
					into.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
				}
			}
		}
		catch(IOException e)
		{
			// the process ended
		}
	}

	void show(int id, String chunk, boolean error)
	{
		display.asyncExec(() ->
		{
			if(!text.isDisposed() && id == request)
			{
				hideIndicator();
				append(chunk, error ? KIND_ERROR : KIND_ANSWER);
				showIndicator();
			}
		});
	}

	void finish(int id, int exit, String errors)
	{
		display.asyncExec(() ->
		{
			if(text.isDisposed() || id != request)
			{
				return;
			}
			hideIndicator();
			if(exit == 0)
			{
				continuing = true;
			}
			else if(exit >= 0 || !errors.isEmpty())
			{
				if(!continuing)
				{
					// the session may or may not have been created: the next try uses a new id
					newSession();
				}
				String message = errors.isEmpty() ? COMMAND + " exited with code " + exit : errors;
				append("\n" + message + "\n", KIND_ERROR);
			}
			synchronized(this)
			{
				process = null;
			}
			setWaiting(false);
			newPrompt();
		});
	}

	// Stops the waiting for an answer; whatever arrived stays.
	void cancel()
	{
		Process p;
		synchronized(this)
		{
			p = process;
			process = null;
		}
		if(waiting)
		{
			request++;
			if(!text.isDisposed())
			{
				hideIndicator();
				append("\n(stopped)\n", KIND_NOTE);
			}
			setWaiting(false);
			if(!text.isDisposed())
			{
				newPrompt();
			}
		}
		if(p != null)
		{
			p.destroy();
		}
	}

	// Empties the tab and starts a new conversation with the next prompt.
	public void clear()
	{
		Process p;
		synchronized(this)
		{
			p = process;
			process = null;
		}
		request++;
		if(p != null)
		{
			p.destroy();
		}
		if(waiting)
		{
			setWaiting(false);
		}
		newSession();
		indicatorStart = -1;
		segments.clear();
		promptStart = -1;
		inputStart = 0;
		internal = true;
		text.setText("");
		internal = false;
		newPrompt();
		text.setFocus();
	}

	void setWaiting(boolean on)
	{
		waiting = on;
		dots = 0;
		if(waitingHook != null)
		{
			waitingHook.accept(on);
		}
		if(on)
		{
			showIndicator();
			tick(request);
		}
		else
		{
			hideIndicator();
		}
	}

	// the "Waiting for Claude..." line at the end of the text, with dots that come and go so that it is seen to be alive
	void showIndicator()
	{
		if(!waiting || text.isDisposed())
		{
			return;
		}
		hideIndicator();
		String label = "Waiting for Claude" + ".".repeat(dots);
		indicatorStart = text.getCharCount();
		internal = true;
		text.replaceTextRange(indicatorStart, 0, label);
		internal = false;
		text.setStyleRange(style(indicatorStart, label.length(), KIND_NOTE));
		inputStart = text.getCharCount();
		focusInputIfShowing();
	}

	void hideIndicator()
	{
		if(indicatorStart >= 0 && !text.isDisposed())
		{
			internal = true;
			text.replaceTextRange(indicatorStart, text.getCharCount() - indicatorStart, "");
			internal = false;
			inputStart = text.getCharCount();
		}
		indicatorStart = -1;
	}

	void tick(int id)
	{
		if(text.isDisposed() || !waiting || id != request)
		{
			return;
		}
		dots = (dots + 1) % 4;
		showIndicator();
		display.timerExec(DOT_MILLIS, () -> tick(id));
	}

	public void restyle()
	{
		text.setFont(theme.font);
		text.setBackground(theme.background);
		text.setForeground(theme.foreground);
		for(int[] seg : segments)
		{
			text.setStyleRange(style(seg[0], seg[1], seg[2]));
		}
		stylePrompt();
		if(indicatorStart >= 0)
		{
			text.setStyleRange(style(indicatorStart, text.getCharCount() - indicatorStart, KIND_NOTE));
		}
	}
}
