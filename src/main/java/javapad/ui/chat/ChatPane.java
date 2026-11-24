package javapad.ui.chat;

import javapad.ui.Theme;
import javapad.ui.editor.LineScroll;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javapad.model.EclipseProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

// A chat with Claude Code or OpenCode running in the multipass virtual machine "claude"; a dropdown chooses which one answers.
// One text holds the whole conversation: your prompts (typed on the last line, sent with Enter) and the answers, in different colours.
// Each prompt is sent, with the path of the project, to the chosen AI in the machine.
public class ChatPane {
	static final String MACHINE = "claude";
	static final String COMMAND = "multipass";
	static final String PROMPT = "> ";
	static final int DOT_MILLIS = 400;
	static final int KIND_PROMPT = 0;
	static final int KIND_ANSWER = 1;
	static final int KIND_ERROR = 2;
	static final int KIND_NOTE = 3;
	static final int KIND_BOLD = 4;
	static final int KIND_THOUGHT = 5;
	static final String OPENCODE = "~/.opencode/bin/opencode";
	static final String CLAUDE_SHELL = "cd \"$1\" 2>/dev/null; shift; exec claude \"$@\"";
	static final String OPENCODE_SHELL = "cd \"$1\" 2>/dev/null; shift; "
		+ "if [ -x \"$HOME/.opencode/bin/opencode\" ]; then exec \"$HOME/.opencode/bin/opencode\" run \"$@\"; fi; "
		+ "echo 'opencode is not installed at " + OPENCODE + " in the " + MACHINE + " VM' >&2; exit 127";

	private final Composite panel;
	private final Composite header;
	private final Label aiLabel;
	private final Combo aiChoice;
	private final Button stop;
	private final StyledText text;
	private final Theme theme;
	private final Display display;
	private EclipseProject project;
	private Consumer<Boolean> waitingHook;
	private Assistant assistant = Assistant.CLAUDE;
	// each AI keeps its own session, so switching the dropdown does not mix their contexts
	private final Map<Assistant, Chat> chats = new EnumMap<Assistant, Chat>(Assistant.class);
	// the running request, and a number that tells its output from that of an earlier one
	private Process process;
	private int request;
	private int indicatorPrefix;
	private boolean waiting;
	private int dots;
	// everything before inputStart is history; what follows is the prompt being typed
	private int inputStart;
	// the start of the prompt being typed (its "> "), or -1 while waiting for an answer
	private int promptStart = -1;
	// the start of the "Waiting for ..." line at the end of the text, or -1
	private int indicatorStart = -1;
	// set while this class changes the text itself
	private boolean internal;
	// the finished parts of the text: start, length, kind
	private final List<int[]> segments = new ArrayList<int[]>();
	// while an answer arrives: where it starts in the text, and what was received of it so far (Markdown, as the AI wrote it)
	private int answerStart = -1;
	private final StringBuilder answerRaw = new StringBuilder();
	// what the answer looked like when it was last shown, so that an unchanged rendering is not shown again
	private ChatMarkdown.Result answerShown;
	// what the answer being received has shown so far: the kind of the last thing (a chat event kind, or -1 for nothing yet), whether any answer text came, and whether an error was shown
	private int lastKind = -1;
	private boolean answered;
	private boolean errorShown;

	// one conversation in one AI, remembered while the dropdown switches to another
	private static class Chat {
		String session = UUID.randomUUID().toString();
		boolean continuing;
	}

	public ChatPane(Composite parent, Theme theme) {
		this.theme = theme;
		this.display = parent.getDisplay();
		for(Assistant a : Assistant.values()) {
			chats.put(a, new Chat());
		}
		panel = new Composite(parent, SWT.NONE);
		panel.setLayout(new GridLayout(1, false));
		header = new Composite(panel, SWT.NONE);
		header.setLayout(new GridLayout(3, false));
		header.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		aiLabel = new Label(header, SWT.NONE);
		aiLabel.setText("AI:");
		aiLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		aiChoice = new Combo(header, SWT.DROP_DOWN | SWT.READ_ONLY);
		aiChoice.setItems(new String[] {Assistant.CLAUDE.label, Assistant.OPENCODE.label});
		aiChoice.select(0);
		aiChoice.setToolTipText("AI to send prompts to");
		aiChoice.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		aiChoice.addListener(SWT.Selection, e -> choose(aiChoice.getText()));
		stop = new Button(header, SWT.PUSH);
		stop.setText("Stop");
		stop.setToolTipText("Stop waiting for the answer");
		stop.setEnabled(false);
		stop.addListener(SWT.Selection, e -> stopPressed());
		stop.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		text = new StyledText(panel, SWT.V_SCROLL | SWT.WRAP);
		text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		text.setFont(theme.font);
		LineScroll.install(text);
		text.addVerifyListener(e -> verify(e));
		text.addModifyListener(e -> stylePrompt());
		text.addVerifyKeyListener(e -> key(e));
		text.addListener(SWT.Dispose, e -> cancel());
		text.setMenu(menu());
		newPrompt();
	}

	// switching AI starts a new session for it, but keeps what is already in the chat and any prompt being typed
	void choose(String label) {
		Assistant selected = Assistant.withLabel(label);
		if(selected == assistant || waiting) {
			return;
		}
		assistant = selected;
		note("Chatting with " + selected.label + " (" + selected.cli + " in " + MACHINE + ")");
	}

	// a line about the chat itself, after whatever was typed, which becomes history
	void note(String content) {
		internal = true;
		int start = text.getCharCount();
		String line = "\n\n" + content + "\n";
		text.replaceTextRange(start, 0, line);
		internal = false;
		segments.add(new int[] {start, line.length(), KIND_NOTE});
		text.setStyleRange(style(start, line.length(), KIND_NOTE));
		inputStart = text.getCharCount();
		newPrompt();
	}

	Menu menu() {
		Menu menu = new Menu(text);
		MenuItem copy = item(menu, "Copy", () -> text.copy());
		MenuItem paste = item(menu, "Paste", () -> text.paste());
		new MenuItem(menu, SWT.SEPARATOR);
		MenuItem stop = item(menu, "Stop", () -> cancel());
		item(menu, "Clear", () -> clear());
		menu.addListener(SWT.Show, e -> {
			copy.setEnabled(text.getSelectionCount() > 0);
			paste.setEnabled(!waiting);
			stop.setEnabled(waiting);
		});
		return menu;
	}

	public static MenuItem item(Menu menu, String label, Runnable action) {
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(label);
		item.addListener(SWT.Selection, e -> action.run());
		return item;
	}

	public Composite getControl() {
		return panel;
	}

	public void setProject(EclipseProject project) {
		this.project = project;
		for(Assistant a : Assistant.values()) {
			newSession(a);
		}
	}

	// called with true when a request starts and with false when it is answered or stopped
	public void setWaitingHook(Consumer<Boolean> hook) {
		waitingHook = hook;
	}

	// Only the prompt at the end can be edited; text typed or pasted anywhere else goes to the end of it.
	void verify(VerifyEvent e) {
		if(internal || (!waiting && e.start >= inputStart)) {
			return;
		}
		e.doit = false;
		if(!waiting && !e.text.isEmpty()) {
			int end = text.getCharCount();
			internal = true;
			text.replaceTextRange(end, 0, e.text);
			internal = false;
			text.setCaretOffset(end + e.text.length());
		}
	}

	// Enter sends the prompt (and is not typed); Shift+Enter is a line break in it
	void key(VerifyEvent e) {
		if(e.keyCode == SWT.HOME && (e.stateMask & (SWT.MOD1 | SWT.ALT)) == 0 && promptStart >= 0 && !waiting) {
			int caret = text.getCaretOffset();
			int line = text.getLineAtOffset(caret);
			if(text.getOffsetAtLine(line) == promptStart && caret >= promptStart) {
				// Home goes to where the typing starts, after the "> "
				e.doit = false;
				int target = promptStart + PROMPT.length();
				if((e.stateMask & SWT.SHIFT) != 0) {
					Point sel = text.getSelection();
					int anchor = sel.x == caret ? sel.y : sel.x;
					text.setSelectionRange(anchor, target - anchor);
				} else {
					text.setSelection(target);
				}
				return;
			}
		}
		boolean enter = e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR;
		if(enter && (e.stateMask & SWT.SHIFT) == 0) {
			e.doit = false;
			send();
		}
	}

	public void focusInput() {
		text.setFocus();
		text.setCaretOffset(text.getCharCount());
		text.showSelection();
	}

	// the text sent to the AI: the path of the project, then what was typed
	static String message(String path, String prompt) {
		return "The project is at " + path + "\n\n" + prompt;
	}

	// the path of the project, written with ~ when it is in the home directory
	static String projectPath(File root) {
		String path = root.getAbsolutePath();
		String home = System.getProperty("user.home");
		if(path.equals(home)) {
			return "~";
		}
		if(path.startsWith(home + File.separator)) {
			return "~" + path.substring(home.length());
		}
		return path;
	}

	// a new conversation for one AI: the next prompt starts a session with a new id
	void newSession(Assistant ai) {
		Chat chat = chats.get(ai);
		chat.session = UUID.randomUUID().toString();
		chat.continuing = false;
	}

	// what runs in the machine: it goes to the project folder if the machine has it at the same path, then starts the chosen AI; the prompt is read from stdin
	// Claude is given a session id for its first prompt and resumes it afterwards. OpenCode chooses its own id, which is read from the stream and used with --session later.
	static List<String> command(String path, String sessionId, boolean resume) {
		return command(path, sessionId, resume, Assistant.CLAUDE);
	}

	static List<String> command(String path, String sessionId, boolean resume, Assistant ai) {
		List<String> c = new ArrayList<String>();
		c.add(COMMAND);
		c.add("exec");
		c.add(MACHINE);
		c.add("--");
		c.add("bash");
		c.add("-lc");
		c.add(ai == Assistant.OPENCODE ? OPENCODE_SHELL : CLAUDE_SHELL);
		// the first argument after -lc's command is $0, so the path is $1
		c.add("chat-pane");
		c.add(path);
		if(ai == Assistant.OPENCODE) {
			c.add("--auto");
			// the lines of JSON tell what OpenCode does while it works, not only its last words
			c.add("--format");
			c.add("json");
			if(resume) {
				c.add("--session");
				c.add(sessionId);
			}
		} else {
			c.add("-p");
			c.add("--permission-mode");
			c.add("auto");
			c.add("--output-format");
			c.add("stream-json");
			c.add("--verbose");
			c.add(resume ? "--resume" : "--session-id");
			c.add(sessionId);
		}
		return c;
	}

	// the prompt to send for what was typed: without the white space around it (any space character counts, the no-break space too), so empty when there is nothing to send
	static String promptOf(String typed) {
		int start = 0;
		int end = typed.length();
		while(start < end && blank(typed.charAt(start))) {
			start++;
		}
		while(end > start && blank(typed.charAt(end - 1))) {
			end--;
		}
		return typed.substring(start, end);
	}

	static boolean blank(char c) {
		return Character.isWhitespace(c) || Character.isSpaceChar(c);
	}

	// sends the prompt that was typed on the last line
	void send() {
		if(waiting) {
			return;
		}
		String prompt = promptOf(text.getText(inputStart, Math.max(inputStart, text.getCharCount() - 1)));
		if(prompt.isEmpty()) {
			// nothing but spaces and line breaks was typed: nothing is sent
			return;
		}
		if(project == null) {
			finishPrompt();
			append("Open a project first.\n", KIND_ERROR);
			newPrompt();
			return;
		}
		String path = projectPath(project.root);
		Assistant ai = assistant;
		Chat chat = chats.get(ai);
		finishPrompt();
		int id = ++request;
		boolean resume = chat.continuing;
		String session = chat.session;
		lastKind = -1;
		answered = false;
		errorShown = false;
		setWaiting(true);
		Thread t = new Thread(() -> talk(id, path, session, message(path, prompt), resume, ai), "chat-pane");
		t.setDaemon(true);
		t.start();
	}

	// the prompt on the last line is done: it keeps its colour and the answer starts on the next line
	void finishPrompt() {
		internal = true;
		text.replaceTextRange(text.getCharCount(), 0, "\n");
		internal = false;
		segments.add(new int[] {promptStart, text.getCharCount() - promptStart, KIND_PROMPT});
		promptStart = -1;
		inputStart = text.getCharCount();
	}

	// a blank line and a new "> " to type after
	void newPrompt() {
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

	void focusInputIfShowing() {
		text.setCaretOffset(text.getCharCount());
		text.showSelection();
	}

	// the prompt being typed has the prompt colour
	void stylePrompt() {
		if(promptStart < 0 || text.isDisposed() || promptStart > text.getCharCount()) {
			return;
		}
		StyleRange style = new StyleRange();
		style.start = promptStart;
		style.length = text.getCharCount() - promptStart;
		style.foreground = color(KIND_PROMPT);
		style.fontStyle = SWT.BOLD;
		text.setStyleRange(style);
	}

	Color color(int kind) {
		switch(kind) {
			case KIND_PROMPT:
				return theme.keyword;
			case KIND_ERROR:
				return theme.errorText;
			case KIND_NOTE:
			case KIND_THOUGHT:
				return theme.gutter;
			default:
				return theme.foreground;
		}
	}

	// adds finished text at the end, in the colour of its kind
	void append(String chunk, int kind) {
		if(chunk.isEmpty()) {
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

	// Adds to the answer that is arriving: all of it is shown again, so that bullets and bold that were cut by the end of a chunk come out right.
	void answer(String chunk) {
		if(answerStart < 0) {
			answerStart = text.getCharCount();
			answerRaw.setLength(0);
		}
		answerRaw.append(chunk);
		ChatMarkdown.Result shown = ChatMarkdown.render(answerRaw.toString());
		if(answerShown != null && answerShown.text.equals(shown.text) && sameRuns(answerShown.bold, shown.bold) && text.getCharCount() == answerStart + shown.text.length()) {
			return;
		}
		// only the end that changed is replaced, so the text before it is not redrawn
		int same = 0;
		if(answerShown != null && text.getCharCount() >= answerStart + answerShown.text.length()) {
			int most = Math.min(answerShown.text.length(), shown.text.length());
			while(same < most && answerShown.text.charAt(same) == shown.text.charAt(same)) {
				same++;
			}
		}
		answerShown = shown;
		internal = true;
		text.replaceTextRange(answerStart + same, text.getCharCount() - answerStart - same, shown.text.substring(same));
		internal = false;
		final int from = answerStart;
		segments.removeIf(seg -> seg[0] >= from);
		segments.add(new int[] {from, shown.text.length(), KIND_ANSWER});
		text.setStyleRange(style(from, shown.text.length(), KIND_ANSWER));
		for(int[] run : shown.bold) {
			segments.add(new int[] {from + run[0], run[1], KIND_BOLD});
			text.setStyleRange(style(from + run[0], run[1], KIND_BOLD));
		}
		inputStart = text.getCharCount();
		focusInputIfShowing();
	}

	static boolean sameRuns(List<int[]> a, List<int[]> b) {
		return Arrays.deepEquals(a.toArray(), b.toArray());
	}

	void endAnswer() {
		answerShown = null;
		answerStart = -1;
		answerRaw.setLength(0);
	}

	StyleRange style(int start, int length, int kind) {
		StyleRange style = new StyleRange();
		style.start = start;
		style.length = length;
		style.foreground = color(kind);
		style.fontStyle = kind == KIND_PROMPT || kind == KIND_BOLD ? SWT.BOLD : kind == KIND_THOUGHT ? SWT.ITALIC : SWT.NORMAL;
		return style;
	}

	// runs on a thread: starts the process, hands it the prompt and shows what it prints
	void talk(int id, String path, String session, String message, boolean resume, Assistant ai) {
		StringBuilder errors = new StringBuilder();
		String[] chosenSession = {null};
		int exit = -1;
		try {
			ProcessBuilder pb = new ProcessBuilder(command(path, session, resume, ai));
			Process p = pb.start();
			synchronized(this) {
				if(id != request) {
					p.destroy();
					return;
				}
				process = p;
			}
			Thread err = new Thread(() -> read(p.getErrorStream(), errors), "chat-pane-err");
			err.setDaemon(true);
			err.start();
			try(OutputStream out = p.getOutputStream()) {
				out.write(message.getBytes(StandardCharsets.UTF_8));
			} catch(IOException e) {
				// the process ended before it read everything; its output says why
			}
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
			String line;
			while((line = in.readLine()) != null) {
				if(ai == Assistant.OPENCODE) {
					String found = OpenCodeStream.session(line);
					if(found != null) {
						chosenSession[0] = found;
					}
				}
				List<ChatEvent> events = ai == Assistant.OPENCODE ? OpenCodeStream.parse(line) : ClaudeStream.parse(line);
				for(ChatEvent event : events) {
					show(id, event);
				}
			}
			exit = p.waitFor();
			err.join(2000);
		} catch(IOException e) {
			errors.append(COMMAND + " could not start " + ai.cli + ": " + e.getMessage());
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finish(id, exit, errors.toString().trim(), ai, chosenSession[0]);
	}

	static void read(InputStream in, StringBuilder into) {
		try {
			byte[] buffer = new byte[2048];
			int n;
			while((n = in.read(buffer)) > 0) {
				synchronized(into) {
					into.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
				}
			}
		} catch(IOException e) {
			// the process ended
		}
	}

	void show(int id, ChatEvent event) {
		display.asyncExec(() -> {
			if(!text.isDisposed() && id == request) {
				// no redraw in between, so that the indicator going and coming back is not seen
				text.setRedraw(false);
				try {
					hideIndicator();
					display(event);
					showIndicator();
				} finally {
					text.setRedraw(true);
				}
			}
		});
	}

	// shows one thing the AI did: a thought (dim, italic), a tool it uses (dim), or text of the answer
	void display(ChatEvent event) {
		switch(event.kind) {
			case ChatEvent.THOUGHT:
				block(event.text.strip(), KIND_THOUGHT, event.kind);
				break;
			case ChatEvent.TOOL:
				block("\u25b8 " + event.text, KIND_NOTE, event.kind);
				break;
			case ChatEvent.ERROR:
				errorShown = true;
				block(event.text.strip(), KIND_ERROR, event.kind);
				break;
			case ChatEvent.PLAIN:
				if(answerStart < 0) {
					separate(event.kind);
				}
				answer(event.text + "\n");
				answered = true;
				lastKind = ChatEvent.ANSWER;
				break;
			case ChatEvent.RESULT:
				if(answered) {
					break;
				}
				// no break: with no text before it, the result is the answer
			default:
				endAnswer();
				separate(ChatEvent.ANSWER);
				answer(event.text.strip());
				endAnswer();
				answered = true;
				lastKind = ChatEvent.ANSWER;
				break;
		}
	}

	// a paragraph of its own
	void block(String content, int kind, int eventKind) {
		endAnswer();
		separate(eventKind);
		append(content, kind);
		lastKind = eventKind;
	}

	// line breaks before the next thing: a blank line between paragraphs, but tool lines under each other
	void separate(int eventKind) {
		int wanted = lastKind < 0 || (eventKind == ChatEvent.TOOL && lastKind == ChatEvent.TOOL) ? 1 : 2;
		String all = text.getText();
		int have = 0;
		while(have < wanted && have < all.length() && all.charAt(all.length() - 1 - have) == '\n') {
			have++;
		}
		if(have < wanted) {
			append("\n".repeat(wanted - have), KIND_NOTE);
		}
	}

	void finish(int id, int exit, String errors, Assistant ai, String chosenSession) {
		display.asyncExec(() -> {
			if(text.isDisposed() || id != request) {
				return;
			}
			hideIndicator();
			endAnswer();
			Chat chat = chats.get(ai);
			if(exit == 0) {
				// OpenCode chooses its own session id and writes it in the stream
				if(chosenSession != null && !chosenSession.isEmpty()) {
					chat.session = chosenSession;
				}
				chat.continuing = true;
			} else if(exit >= 0 || !errors.isEmpty() || errorShown) {
				if(!chat.continuing) {
					// the session may or may not have been created: the next try uses a new id
					newSession(ai);
				}
				if(!errorShown || !errors.isEmpty()) {
					String message = errors.isEmpty() ? ai.cli + " exited with code " + exit : errors;
					block(message, KIND_ERROR, ChatEvent.ERROR);
				}
			}
			synchronized(this) {
				process = null;
			}
			setWaiting(false);
			newPrompt();
		});
	}

	// the Stop button: ends the wait and puts the caret back in the prompt, ready to type again
	void stopPressed() {
		cancel();
		if(!text.isDisposed()) {
			text.setFocus();
		}
	}

	// Stops the waiting for an answer; whatever arrived stays.
	void cancel() {
		Process p;
		synchronized(this) {
			p = process;
			process = null;
		}
		if(waiting) {
			request++;
			if(!text.isDisposed()) {
				hideIndicator();
				endAnswer();
				append("\n(stopped)\n", KIND_NOTE);
			}
			setWaiting(false);
			if(!text.isDisposed()) {
				newPrompt();
			}
		}
		if(p != null) {
			p.destroy();
		}
	}

	// Empties the tab and starts a new conversation with the next prompt.
	public void clear() {
		Process p;
		synchronized(this) {
			p = process;
			process = null;
		}
		request++;
		if(p != null) {
			p.destroy();
		}
		if(waiting) {
			setWaiting(false);
		}
		for(Assistant a : Assistant.values()) {
			newSession(a);
		}
		endAnswer();
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

	void setWaiting(boolean on) {
		waiting = on;
		dots = 0;
		// the AI cannot be changed while it is working: the answer belongs to the one that was chosen
		if(!aiChoice.isDisposed()) {
			aiChoice.setEnabled(!on);
		}
		if(!stop.isDisposed()) {
			stop.setEnabled(on);
		}
		if(waitingHook != null) {
			waitingHook.accept(on);
		}
		if(on) {
			showIndicator();
			tick(request);
		} else {
			hideIndicator();
		}
	}

	String indicator() {
		return "Waiting for " + assistant.label;
	}

	// the "Waiting for ..." line at the end of the text, with dots that come and go so that it is seen to be alive
	void showIndicator() {
		if(!waiting || text.isDisposed()) {
			return;
		}
		hideIndicator();
		// where the next block would start, so that nothing moves when it arrives: after a blank line, but under a tool line
		int wanted = text.getCharCount() == 0 ? 0 : lastKind < 0 || lastKind == ChatEvent.TOOL ? 1 : 2;
		String all = text.getText();
		int have = 0;
		while(have < wanted && have < all.length() && all.charAt(all.length() - 1 - have) == '\n') {
			have++;
		}
		indicatorPrefix = wanted - have;
		String label = "\n".repeat(indicatorPrefix) + indicator() + ".".repeat(dots);
		indicatorStart = text.getCharCount();
		internal = true;
		text.replaceTextRange(indicatorStart, 0, label);
		internal = false;
		text.setStyleRange(style(indicatorStart, label.length(), KIND_NOTE));
		inputStart = text.getCharCount();
		focusInputIfShowing();
	}

	void hideIndicator() {
		if(indicatorStart >= 0 && !text.isDisposed()) {
			internal = true;
			text.replaceTextRange(indicatorStart, text.getCharCount() - indicatorStart, "");
			internal = false;
			inputStart = text.getCharCount();
		}
		indicatorStart = -1;
	}

	void tick(int id) {
		if(text.isDisposed() || !waiting || id != request) {
			return;
		}
		dots = (dots + 1) % 4;
		if(indicatorStart >= 0) {
			// only the dots change; the rest of the text is left alone
			int from = indicatorStart + indicatorPrefix + indicator().length();
			text.setRedraw(false);
			try {
				internal = true;
				text.replaceTextRange(from, text.getCharCount() - from, ".".repeat(dots));
				internal = false;
				text.setStyleRange(style(indicatorStart, text.getCharCount() - indicatorStart, KIND_NOTE));
				inputStart = text.getCharCount();
			} finally {
				text.setRedraw(true);
			}
		}
		display.timerExec(DOT_MILLIS, () -> tick(id));
	}

	public void restyle() {
		panel.setBackground(theme.background);
		header.setBackground(theme.chrome);
		aiLabel.setForeground(theme.foreground);
		aiChoice.setBackground(theme.background);
		aiChoice.setForeground(theme.foreground);
		stop.setBackground(theme.chrome);
		stop.setForeground(theme.foreground);
		text.setFont(theme.font);
		text.setBackground(theme.background);
		text.setForeground(theme.foreground);
		for(int[] seg : segments) {
			text.setStyleRange(style(seg[0], seg[1], seg[2]));
		}
		stylePrompt();
		if(indicatorStart >= 0) {
			text.setStyleRange(style(indicatorStart, text.getCharCount() - indicatorStart, KIND_NOTE));
		}
	}
}
