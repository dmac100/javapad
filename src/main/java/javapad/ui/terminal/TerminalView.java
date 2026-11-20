package javapad.ui.terminal;

import javapad.ui.Theme;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

// Draws a TerminalScreen and turns key presses, pastes and the mouse into the bytes a terminal sends.
public class TerminalView {
	private final Canvas canvas;
	private final Theme theme;
	private final TerminalScreen screen = new TerminalScreen(80, 24);
	private final Map<Integer, Color> colors = new HashMap<Integer, Color>();
	private final AtomicBoolean pending = new AtomicBoolean();
	private Font bold;
	private int cellWidth = 8;
	private int cellHeight = 16;
	// how many lines the view is scrolled back from the bottom
	private int scroll;
	private int anchorRow = -1;
	private int anchorColumn;
	private int endRow;
	private int endColumn;
	private Consumer<String> input = s -> {
	};
	private BiConsumer<Integer, Integer> resizeListener = (c, r) -> {
	};
	private Consumer<Boolean> focusListener = f -> {
	};

	TerminalView(Composite parent, Theme theme) {
		this.theme = theme;
		canvas = new Canvas(parent, SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED);
		measure();
		canvas.addListener(SWT.Paint, e -> paint(e.gc));
		canvas.addListener(SWT.Resize, e -> resized());
		canvas.addListener(SWT.Traverse, e -> e.doit = false);
		canvas.addListener(SWT.KeyDown, e -> key(e));
		canvas.addListener(SWT.MouseDown, e -> mouseDown(e));
		canvas.addListener(SWT.MouseMove, e -> mouseMove(e));
		canvas.addListener(SWT.MouseVerticalWheel, e -> scrollBy(e.count * 3));
		canvas.addListener(SWT.FocusIn, e -> {
			focusListener.accept(true);
			canvas.redraw();
		});
		canvas.addListener(SWT.FocusOut, e -> {
			focusListener.accept(false);
			canvas.redraw();
		});
		canvas.addListener(SWT.Dispose, e -> {
			clearResources();
		});
		Menu menu = new Menu(canvas);
		MenuItem copy = new MenuItem(menu, SWT.PUSH);
		copy.setText("Copy\tCtrl+Shift+C");
		copy.addListener(SWT.Selection, e -> copy());
		MenuItem paste = new MenuItem(menu, SWT.PUSH);
		paste.setText("Paste\tCtrl+Shift+V");
		paste.addListener(SWT.Selection, e -> paste());
		canvas.setMenu(menu);
	}

	Canvas control() {
		return canvas;
	}

	TerminalScreen screen() {
		return screen;
	}

	void setInput(Consumer<String> input) {
		this.input = input;
	}

	void setResizeListener(BiConsumer<Integer, Integer> listener) {
		resizeListener = listener;
	}

	// called with true when the terminal gets the keyboard, false when it loses it
	void setFocusListener(Consumer<Boolean> listener) {
		focusListener = listener;
	}

	// Safe from any thread; many calls before the redraw happens cost one redraw.
	void invalidate() {
		if(pending.compareAndSet(false, true)) {
			canvas.getDisplay().asyncExec(() -> {
				pending.set(false);
				if(!canvas.isDisposed()) {
					canvas.redraw();
				}
			});
		}
	}

	void restyle() {
		clearResources();
		measure();
		resized();
		canvas.redraw();
	}

	void clearResources() {
		for(Color c : colors.values()) {
			c.dispose();
		}
		colors.clear();
		if(bold != null) {
			bold.dispose();
			bold = null;
		}
	}

	void measure() {
		GC gc = new GC(canvas);
		gc.setFont(theme.font);
		cellWidth = Math.max(1, Math.round(gc.stringExtent("MMMMMMMMMM").x / 10f));
		cellHeight = Math.max(1, gc.getFontMetrics().getHeight());
		gc.dispose();
		FontData[] data = theme.font.getFontData();
		for(FontData d : data) {
			d.setStyle(SWT.BOLD);
		}
		bold = new Font(canvas.getDisplay(), data);
	}

	void resized() {
		Rectangle area = canvas.getClientArea();
		int cols = Math.max(2, area.width / cellWidth);
		int rows = Math.max(1, area.height / cellHeight);
		screen.resize(cols, rows);
		scroll = Math.min(scroll, screen.scrollbackSize());
		resizeListener.accept(cols, rows);
	}

	Color color(int code) {
		Color c = colors.get(code);
		if(c == null) {
			c = new Color(canvas.getDisplay(), rgb(code));
			colors.put(code, c);
		}
		return c;
	}

	RGB rgb(int code) {
		if((code & TerminalScreen.TRUE_COLOR) != 0) {
			return new RGB((code >> 16) & 255, (code >> 8) & 255, code & 255);
		}
		if(code < 16) {
			int[] p = (theme.dark ? ConsolePane.DARK : ConsolePane.LIGHT)[code];
			return new RGB(p[0], p[1], p[2]);
		}
		if(code < 232) {
			int v = code - 16;
			return new RGB(ConsolePane.level(v / 36), ConsolePane.level(v / 6 % 6), ConsolePane.level(v % 6));
		}
		int g = 8 + (code - 232) * 10;
		return new RGB(g, g, g);
	}

	Color defaultColor(boolean foreground) {
		return foreground ? theme.foreground : theme.background;
	}

	void paint(GC gc) {
		gc.setFont(theme.font);
		gc.setBackground(theme.background);
		Rectangle area = canvas.getClientArea();
		gc.fillRectangle(area);
		synchronized(screen) {
			int rows = screen.rows();
			int cols = screen.cols();
			int offset = screen.alternate() ? 0 : scroll;
			int first = screen.totalLines() - rows - offset;
			for(int r = 0; r < rows; r++) {
				int index = first + r;
				if(index >= 0 && index < screen.totalLines()) {
					paintLine(gc, screen.line(index), index, r * cellHeight, cols);
				}
			}
			if(screen.cursorVisible() && offset == 0) {
				paintCursor(gc);
			}
		}
	}

	void paintLine(GC gc, TerminalScreen.Line line, int index, int y, int cols) {
		for(int c = 0; c < Math.min(cols, line.ch.length); c++) {
			int attr = line.attr[c];
			int fg = line.fg[c];
			int bg = line.bg[c];
			if((attr & TerminalScreen.BOLD) != 0 && fg >= 0 && fg < 8) {
				fg += 8;
			}
			Color foreground = fg < 0 ? defaultColor(true) : color(fg);
			Color background = bg < 0 ? defaultColor(false) : color(bg);
			if((attr & TerminalScreen.REVERSE) != 0) {
				Color t = foreground;
				foreground = background;
				background = t;
			}
			if(selected(index, c)) {
				background = canvas.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION);
				foreground = canvas.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
			}
			int x = c * cellWidth;
			if(background != theme.background) {
				gc.setBackground(background);
				gc.fillRectangle(x, y, cellWidth, cellHeight);
			}
			char ch = line.ch[c];
			if(ch != ' ') {
				gc.setFont((attr & TerminalScreen.BOLD) != 0 ? bold : theme.font);
				gc.setForeground(foreground);
				gc.drawString(String.valueOf(ch), x, y, true);
			}
			if((attr & TerminalScreen.UNDERLINE) != 0) {
				gc.setForeground(foreground);
				gc.drawLine(x, y + cellHeight - 1, x + cellWidth - 1, y + cellHeight - 1);
			}
		}
		gc.setFont(theme.font);
	}

	void paintCursor(GC gc) {
		int x = screen.cursorX() * cellWidth;
		int y = screen.cursorY() * cellHeight;
		if(canvas.isFocusControl()) {
			gc.setBackground(theme.foreground);
			gc.fillRectangle(x, y, cellWidth, cellHeight);
			TerminalScreen.Line l = screen.line(screen.scrollbackSize() + screen.cursorY());
			char ch = l.ch[Math.min(screen.cursorX(), l.ch.length - 1)];
			if(ch != ' ') {
				gc.setForeground(theme.background);
				gc.drawString(String.valueOf(ch), x, y, true);
			}
		} else {
			gc.setForeground(theme.foreground);
			gc.drawRectangle(x, y, cellWidth - 1, cellHeight - 1);
		}
	}

	boolean selected(int row, int column) {
		if(anchorRow < 0) {
			return false;
		}
		int r1 = anchorRow;
		int c1 = anchorColumn;
		int r2 = endRow;
		int c2 = endColumn;
		if(r1 > r2 || r1 == r2 && c1 > c2) {
			int t = r1;
			r1 = r2;
			r2 = t;
			t = c1;
			c1 = c2;
			c2 = t;
		}
		if(row < r1 || row > r2) {
			return false;
		}
		return (row > r1 || column >= c1) && (row < r2 || column <= c2);
	}

	int rowAt(int y) {
		int rows = screen.rows();
		int offset = screen.alternate() ? 0 : scroll;
		int r = Math.max(0, Math.min(rows - 1, y / cellHeight));
		return screen.totalLines() - rows - offset + r;
	}

	int columnAt(int x) {
		return Math.max(0, Math.min(screen.cols() - 1, x / cellWidth));
	}

	void mouseDown(Event e) {
		canvas.setFocus();
		if(e.button == 1) {
			anchorRow = rowAt(e.y);
			anchorColumn = columnAt(e.x);
			endRow = anchorRow;
			endColumn = anchorColumn;
			canvas.redraw();
		}
	}

	void mouseMove(Event e) {
		if((e.stateMask & SWT.BUTTON1) != 0 && anchorRow >= 0) {
			endRow = rowAt(e.y);
			endColumn = columnAt(e.x);
			canvas.redraw();
		}
	}

	String selection() {
		if(anchorRow < 0 || anchorRow == endRow && anchorColumn == endColumn) {
			return "";
		}
		int r1 = anchorRow;
		int c1 = anchorColumn;
		int r2 = endRow;
		int c2 = endColumn;
		if(r1 > r2 || r1 == r2 && c1 > c2) {
			int t = r1;
			r1 = r2;
			r2 = t;
			t = c1;
			c1 = c2;
			c2 = t;
		}
		StringBuilder sb = new StringBuilder();
		for(int r = r1; r <= r2; r++) {
			sb.append(screen.text(r, r == r1 ? c1 : 0, r == r2 ? c2 + 1 : screen.cols()));
			if(r < r2) {
				sb.append('\n');
			}
		}
		return sb.toString();
	}

	void copy() {
		String s = selection();
		if(!s.isEmpty()) {
			Clipboard cb = new Clipboard(canvas.getDisplay());
			cb.setContents(new Object[] {s}, new Transfer[] {TextTransfer.getInstance()});
			cb.dispose();
		}
	}

	void paste() {
		Clipboard cb = new Clipboard(canvas.getDisplay());
		Object o = cb.getContents(TextTransfer.getInstance());
		cb.dispose();
		if(o instanceof String) {
			String s = ((String)o).replace("\r\n", "\r").replace('\n', '\r');
			scroll = 0;
			input.accept(screen.bracketedPaste() ? "\u001b[200~" + s + "\u001b[201~" : s);
		}
	}

	void scrollBy(int lines) {
		if(!screen.alternate()) {
			scroll = Math.max(0, Math.min(screen.scrollbackSize(), scroll + lines));
			canvas.redraw();
		}
	}

	void key(Event e) {
		boolean ctrl = (e.stateMask & SWT.CTRL) != 0;
		boolean shift = (e.stateMask & SWT.SHIFT) != 0;
		boolean alt = (e.stateMask & SWT.ALT) != 0;
		if(ctrl && shift && (e.keyCode == 'c' || e.keyCode == 'C')) {
			copy();
			return;
		}
		if(ctrl && shift && (e.keyCode == 'v' || e.keyCode == 'V') || shift && e.keyCode == SWT.INSERT) {
			paste();
			return;
		}
		if(shift && e.keyCode == SWT.PAGE_UP) {
			scrollBy(screen.rows() - 1);
			return;
		}
		if(shift && e.keyCode == SWT.PAGE_DOWN) {
			scrollBy(1 - screen.rows());
			return;
		}
		String s = sequence(e, ctrl, shift, alt);
		if(s != null) {
			scroll = 0;
			anchorRow = -1;
			input.accept(s);
			canvas.redraw();
		}
	}

	static String csiKey(String code, int modifier, boolean application, String letter) {
		if(modifier > 1) {
			return "\u001b[" + (code.isEmpty() ? "1" : code) + ";" + modifier + letter;
		}
		if(letter.equals("~")) {
			return "\u001b[" + code + "~";
		}
		return (application ? "\u001bO" : "\u001b[") + letter;
	}

	String sequence(Event e, boolean ctrl, boolean shift, boolean alt) {
		int modifier = 1 + (shift ? 1 : 0) + (alt ? 2 : 0) + (ctrl ? 4 : 0);
		boolean app = screen.applicationCursor();
		switch(e.keyCode) {
		case SWT.ARROW_UP:
			return csiKey("", modifier, app, "A");
		case SWT.ARROW_DOWN:
			return csiKey("", modifier, app, "B");
		case SWT.ARROW_RIGHT:
			return csiKey("", modifier, app, "C");
		case SWT.ARROW_LEFT:
			return csiKey("", modifier, app, "D");
		case SWT.HOME:
			return csiKey("", modifier, app, "H");
		case SWT.END:
			return csiKey("", modifier, app, "F");
		case SWT.PAGE_UP:
			return csiKey("5", modifier, false, "~");
		case SWT.PAGE_DOWN:
			return csiKey("6", modifier, false, "~");
		case SWT.INSERT:
			return csiKey("2", modifier, false, "~");
		case SWT.DEL:
			return csiKey("3", modifier, false, "~");
		case SWT.F1:
			return modifier > 1 ? "\u001b[1;" + modifier + "P" : "\u001bOP";
		case SWT.F2:
			return modifier > 1 ? "\u001b[1;" + modifier + "Q" : "\u001bOQ";
		case SWT.F3:
			return modifier > 1 ? "\u001b[1;" + modifier + "R" : "\u001bOR";
		case SWT.F4:
			return modifier > 1 ? "\u001b[1;" + modifier + "S" : "\u001bOS";
		case SWT.F5:
			return csiKey("15", modifier, false, "~");
		case SWT.F6:
			return csiKey("17", modifier, false, "~");
		case SWT.F7:
			return csiKey("18", modifier, false, "~");
		case SWT.F8:
			return csiKey("19", modifier, false, "~");
		case SWT.F9:
			return csiKey("20", modifier, false, "~");
		case SWT.F10:
			return csiKey("21", modifier, false, "~");
		case SWT.F11:
			return csiKey("23", modifier, false, "~");
		case SWT.F12:
			return csiKey("24", modifier, false, "~");
		default:
			break;
		}
		char c = e.character;
		if(c == 0) {
			return null;
		}
		if(c == SWT.BS) {
			return alt ? "\u001b\u007f" : "\u007f";
		}
		if(c == SWT.TAB && shift) {
			return "\u001b[Z";
		}
		if(c == SWT.CR) {
			return alt ? "\u001b\r" : "\r";
		}
		return alt ? "\u001b" + c : String.valueOf(c);
	}
}
