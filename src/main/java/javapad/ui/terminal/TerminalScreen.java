package javapad.ui.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// The text a terminal shows: a grid of cells with a cursor, scrollback, and the escape sequences programs use to move the cursor, colour text and clear parts of the screen.
// Colours are -1 (the default), 0 to 255 (the xterm palette) or TRUE_COLOR | 0xRRGGBB. It has no SWT, so it can be tested.
public class TerminalScreen {
	static final int BOLD = 1;
	static final int UNDERLINE = 2;
	static final int REVERSE = 4;
	static final int ITALIC = 8;
	static final int DIM = 16;
	static final int TRUE_COLOR = 0x1000000;
	static final int MAX_SCROLLBACK = 5000;
	static final int GROUND = 0;
	static final int ESCAPE = 1;
	static final int CSI = 2;
	static final int OSC = 3;
	static final int SKIP_ONE = 4;
	static final int STRING = 5;

	static class Line {
		char[] ch;
		int[] fg;
		int[] bg;
		int[] attr;

		Line(int cols, int bg) {
			ch = new char[cols];
			fg = new int[cols];
			this.bg = new int[cols];
			attr = new int[cols];
			for(int i = 0; i < cols; i++) {
				ch[i] = ' ';
				fg[i] = -1;
				this.bg[i] = bg;
			}
		}

		Line resized(int cols) {
			Line n = new Line(cols, -1);
			int keep = Math.min(cols, ch.length);
			System.arraycopy(ch, 0, n.ch, 0, keep);
			System.arraycopy(fg, 0, n.fg, 0, keep);
			System.arraycopy(bg, 0, n.bg, 0, keep);
			System.arraycopy(attr, 0, n.attr, 0, keep);
			return n;
		}
	}

	private int cols;
	private int rows;
	private List<Line> lines = new ArrayList<Line>();
	private List<Line> mainLines;
	private final List<Line> scrollback = new ArrayList<Line>();
	private int cx;
	private int cy;
	private boolean pendingWrap;
	private int savedX;
	private int savedY;
	private int fg = -1;
	private int bg = -1;
	private int attr;
	private int top;
	private int bottom;
	private boolean alternate;
	private boolean applicationCursor;
	private boolean bracketedPaste;
	private boolean cursorVisible = true;
	private boolean autowrap = true;
	private int state = GROUND;
	private final StringBuilder params = new StringBuilder();
	private char prefix;
	private final StringBuilder oscText = new StringBuilder();
	private String title = "";
	private Consumer<String> reply = s -> {
	};

	TerminalScreen(int cols, int rows) {
		this.cols = Math.max(1, cols);
		this.rows = Math.max(1, rows);
		for(int i = 0; i < this.rows; i++) {
			lines.add(new Line(this.cols, -1));
		}
		bottom = this.rows - 1;
	}

	// where the answers to the program's questions (cursor position and so on) go
	void setReply(Consumer<String> reply) {
		this.reply = reply;
	}

	synchronized int cols() {
		return cols;
	}

	synchronized int rows() {
		return rows;
	}

	synchronized int cursorX() {
		return cx;
	}

	synchronized int cursorY() {
		return cy;
	}

	synchronized boolean cursorVisible() {
		return cursorVisible;
	}

	synchronized boolean applicationCursor() {
		return applicationCursor;
	}

	synchronized boolean bracketedPaste() {
		return bracketedPaste;
	}

	synchronized boolean alternate() {
		return alternate;
	}

	synchronized String title() {
		return title;
	}

	// scrollback and screen together: line 0 is the oldest
	synchronized int totalLines() {
		return scrollback.size() + rows;
	}

	synchronized int scrollbackSize() {
		return scrollback.size();
	}

	synchronized Line line(int index) {
		if(index < scrollback.size()) {
			return scrollback.get(index);
		}
		return lines.get(Math.min(rows - 1, index - scrollback.size()));
	}

	// the text of a line without its trailing spaces
	synchronized String text(int index, int from, int to) {
		Line l = line(index);
		int end = Math.min(to, l.ch.length);
		while(end > from && l.ch[end - 1] == ' ') {
			end--;
		}
		return from >= end ? "" : new String(l.ch, from, end - from);
	}

	synchronized void resize(int newCols, int newRows) {
		newCols = Math.max(1, newCols);
		newRows = Math.max(1, newRows);
		if(newCols == cols && newRows == rows) {
			return;
		}
		lines = resizeLines(lines, newCols, newRows);
		if(mainLines != null) {
			mainLines = resizeLines(mainLines, newCols, newRows);
		}
		for(int i = 0; i < scrollback.size(); i++) {
			scrollback.set(i, scrollback.get(i).resized(newCols));
		}
		cols = newCols;
		rows = newRows;
		cx = Math.min(cx, cols - 1);
		cy = Math.min(cy, rows - 1);
		pendingWrap = false;
		top = 0;
		bottom = rows - 1;
	}

	List<Line> resizeLines(List<Line> old, int newCols, int newRows) {
		List<Line> result = new ArrayList<Line>();
		for(Line l : old) {
			result.add(l.resized(newCols));
		}
		while(result.size() > newRows) {
			// the lines at the top go to the scrollback, unless the cursor is above the ones that would be cut
			if(old == lines && cy > 0 && !alternate) {
				scrollback.add(result.remove(0));
				cy--;
			} else {
				result.remove(result.size() - 1);
			}
		}
		while(result.size() < newRows) {
			result.add(new Line(newCols, -1));
		}
		return result;
	}

	synchronized void feed(String s) {
		for(int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch(state) {
			case GROUND:
				ground(c);
				break;
			case ESCAPE:
				escape(c);
				break;
			case CSI:
				csi(c);
				break;
			case OSC:
				osc(c);
				break;
			case SKIP_ONE:
				state = GROUND;
				break;
			default:
				// device control and similar strings: ignored up to the terminator
				if(c == 7) {
					state = GROUND;
				} else if(c == 27) {
					state = ESCAPE;
				}
				break;
			}
		}
	}

	void ground(char c) {
		if(c == 27) {
			state = ESCAPE;
		} else if(c < 32) {
			control(c);
		} else if(c != 127) {
			put(c);
		}
	}

	void control(char c) {
		switch(c) {
		case '\n':
		case 11:
		case 12:
			lineFeed();
			break;
		case '\r':
			cx = 0;
			pendingWrap = false;
			break;
		case '\b':
			if(cx > 0) {
				cx--;
			}
			pendingWrap = false;
			break;
		case '\t':
			int next = (cx / 8 + 1) * 8;
			cx = Math.min(cols - 1, next);
			pendingWrap = false;
			break;
		default:
			break;
		}
	}

	void escape(char c) {
		state = GROUND;
		switch(c) {
		case '[':
			state = CSI;
			params.setLength(0);
			prefix = 0;
			break;
		case ']':
			state = OSC;
			oscText.setLength(0);
			break;
		case 'P':
		case 'X':
		case '^':
		case '_':
			state = STRING;
			break;
		case '(':
		case ')':
		case '*':
		case '+':
		case '#':
		case '%':
			state = SKIP_ONE;
			break;
		case '7':
			savedX = cx;
			savedY = cy;
			break;
		case '8':
			cx = Math.min(savedX, cols - 1);
			cy = Math.min(savedY, rows - 1);
			pendingWrap = false;
			break;
		case 'M':
			reverseIndex();
			break;
		case 'D':
			lineFeed();
			break;
		case 'E':
			cx = 0;
			lineFeed();
			break;
		case 'c':
			reset();
			break;
		case 27:
			state = ESCAPE;
			break;
		default:
			break;
		}
	}

	void osc(char c) {
		if(c == 7 || c == 27) {
			String s = oscText.toString();
			if(s.startsWith("0;") || s.startsWith("2;")) {
				title = s.substring(2);
			}
			state = c == 27 ? ESCAPE : GROUND;
		} else {
			oscText.append(c);
		}
	}

	void csi(char c) {
		if(c >= '0' && c <= ';') {
			params.append(c);
		} else if(c == '?' || c == '>' || c == '<' || c == '=' || c == '!') {
			prefix = c;
		} else if(c >= 0x20 && c <= 0x2f) {
			// intermediate characters (as in "CSI 1 SP q") are not used here
			params.append(' ');
		} else if(c >= 0x40 && c <= 0x7e) {
			state = GROUND;
			dispatch(c);
		} else if(c == 27) {
			state = ESCAPE;
		} else if(c < 32) {
			control(c);
		}
	}

	int[] numbers() {
		String[] parts = params.toString().split(";", -1);
		int[] n = new int[parts.length];
		for(int i = 0; i < parts.length; i++) {
			String p = parts[i].replace(" ", "");
			int colon = p.indexOf(':');
			if(colon >= 0) {
				p = p.substring(0, colon);
			}
			try {
				n[i] = p.isEmpty() ? 0 : Integer.parseInt(p);
			} catch(NumberFormatException e) {
				n[i] = 0;
			}
		}
		return n;
	}

	static int arg(int[] n, int i, int def) {
		return i < n.length && n[i] > 0 ? n[i] : def;
	}

	void dispatch(char c) {
		int[] n = numbers();
		int a = arg(n, 0, 1);
		if(prefix == '?' && (c == 'h' || c == 'l')) {
			for(int mode : n) {
				setMode(mode, c == 'h');
			}
			return;
		}
		if(prefix != 0 && prefix != '?') {
			return;
		}
		switch(c) {
		case 'A':
			moveTo(cx, cy - a, true);
			break;
		case 'B':
			moveTo(cx, cy + a, true);
			break;
		case 'C':
			moveTo(cx + a, cy, false);
			break;
		case 'D':
			moveTo(cx - a, cy, false);
			break;
		case 'E':
			moveTo(0, cy + a, true);
			break;
		case 'F':
			moveTo(0, cy - a, true);
			break;
		case 'G':
			moveTo(a - 1, cy, false);
			break;
		case 'H':
		case 'f':
			moveTo(arg(n, 1, 1) - 1, arg(n, 0, 1) - 1, false);
			break;
		case 'd':
			moveTo(cx, a - 1, false);
			break;
		case 'J':
			eraseDisplay(n[0]);
			break;
		case 'K':
			eraseLine(n[0]);
			break;
		case 'L':
			insertLines(a);
			break;
		case 'M':
			deleteLines(a);
			break;
		case 'P':
			deleteChars(a);
			break;
		case '@':
			insertChars(a);
			break;
		case 'X':
			erase(cy, cx, Math.min(cols, cx + a));
			break;
		case 'S':
			scrollUp(a);
			break;
		case 'T':
			scrollDown(a);
			break;
		case 'm':
			sgr(n);
			break;
		case 'r':
			setRegion(arg(n, 0, 1) - 1, arg(n, 1, rows) - 1);
			break;
		case 's':
			savedX = cx;
			savedY = cy;
			break;
		case 'u':
			cx = Math.min(savedX, cols - 1);
			cy = Math.min(savedY, rows - 1);
			pendingWrap = false;
			break;
		case 'n':
			if(n[0] == 5) {
				reply.accept("\u001b[0n");
			} else if(n[0] == 6) {
				reply.accept("\u001b[" + (cy + 1) + ";" + (cx + 1) + "R");
			}
			break;
		case 'c':
			reply.accept("\u001b[?1;2c");
			break;
		default:
			break;
		}
	}

	void setMode(int mode, boolean on) {
		switch(mode) {
		case 1:
			applicationCursor = on;
			break;
		case 7:
			autowrap = on;
			break;
		case 25:
			cursorVisible = on;
			break;
		case 47:
		case 1047:
		case 1049:
			setAlternate(on, mode == 1049);
			break;
		case 2004:
			bracketedPaste = on;
			break;
		default:
			break;
		}
	}

	void setAlternate(boolean on, boolean saveCursor) {
		if(on == alternate) {
			return;
		}
		alternate = on;
		if(on) {
			if(saveCursor) {
				savedX = cx;
				savedY = cy;
			}
			mainLines = lines;
			lines = new ArrayList<Line>();
			for(int i = 0; i < rows; i++) {
				lines.add(new Line(cols, -1));
			}
		} else {
			lines = mainLines;
			mainLines = null;
			if(saveCursor) {
				cx = Math.min(savedX, cols - 1);
				cy = Math.min(savedY, rows - 1);
			}
		}
		pendingWrap = false;
		top = 0;
		bottom = rows - 1;
	}

	void reset() {
		fg = -1;
		bg = -1;
		attr = 0;
		top = 0;
		bottom = rows - 1;
		applicationCursor = false;
		bracketedPaste = false;
		cursorVisible = true;
		autowrap = true;
		if(alternate) {
			setAlternate(false, false);
		}
		lines.clear();
		for(int i = 0; i < rows; i++) {
			lines.add(new Line(cols, -1));
		}
		cx = 0;
		cy = 0;
		pendingWrap = false;
	}

	// Moves the cursor. vertical: the move is a relative one up or down, which stops at the scroll region.
	void moveTo(int x, int y, boolean vertical) {
		cx = Math.max(0, Math.min(cols - 1, x));
		int low = 0;
		int high = rows - 1;
		if(vertical && y != cy) {
			if(y < cy && cy >= top) {
				low = top;
			}
			if(y > cy && cy <= bottom) {
				high = bottom;
			}
		}
		cy = Math.max(low, Math.min(high, y));
		pendingWrap = false;
	}

	void setRegion(int t, int b) {
		if(t < 0 || b >= rows || t >= b) {
			top = 0;
			bottom = rows - 1;
		} else {
			top = t;
			bottom = b;
		}
		cx = 0;
		cy = 0;
		pendingWrap = false;
	}

	void put(char c) {
		if(pendingWrap && autowrap) {
			cx = 0;
			lineFeed();
		}
		pendingWrap = false;
		Line l = lines.get(cy);
		l.ch[cx] = c;
		l.fg[cx] = fg;
		l.bg[cx] = bg;
		l.attr[cx] = attr;
		if(cx == cols - 1) {
			pendingWrap = true;
		} else {
			cx++;
		}
	}

	void lineFeed() {
		if(cy == bottom) {
			scrollUp(1);
		} else if(cy < rows - 1) {
			cy++;
		}
	}

	void reverseIndex() {
		if(cy == top) {
			scrollDown(1);
		} else if(cy > 0) {
			cy--;
		}
	}

	void scrollUp(int n) {
		for(int i = 0; i < n; i++) {
			Line gone = lines.remove(top);
			lines.add(bottom, new Line(cols, bg));
			if(!alternate && top == 0) {
				scrollback.add(gone);
				if(scrollback.size() > MAX_SCROLLBACK) {
					scrollback.remove(0);
				}
			}
		}
	}

	void scrollDown(int n) {
		for(int i = 0; i < n; i++) {
			lines.remove(bottom);
			lines.add(top, new Line(cols, bg));
		}
	}

	void insertLines(int n) {
		if(cy < top || cy > bottom) {
			return;
		}
		for(int i = 0; i < Math.min(n, bottom - cy + 1); i++) {
			lines.remove(bottom);
			lines.add(cy, new Line(cols, bg));
		}
		cx = 0;
		pendingWrap = false;
	}

	void deleteLines(int n) {
		if(cy < top || cy > bottom) {
			return;
		}
		for(int i = 0; i < Math.min(n, bottom - cy + 1); i++) {
			lines.remove(cy);
			lines.add(bottom, new Line(cols, bg));
		}
		cx = 0;
		pendingWrap = false;
	}

	void deleteChars(int n) {
		Line l = lines.get(cy);
		int count = Math.min(n, cols - cx);
		shift(l, cx + count, cx, cols - cx - count);
		erase(cy, cols - count, cols);
	}

	void insertChars(int n) {
		Line l = lines.get(cy);
		int count = Math.min(n, cols - cx);
		shift(l, cx, cx + count, cols - cx - count);
		erase(cy, cx, cx + count);
	}

	void shift(Line l, int from, int to, int length) {
		System.arraycopy(l.ch, from, l.ch, to, length);
		System.arraycopy(l.fg, from, l.fg, to, length);
		System.arraycopy(l.bg, from, l.bg, to, length);
		System.arraycopy(l.attr, from, l.attr, to, length);
	}

	void erase(int row, int from, int to) {
		Line l = lines.get(row);
		for(int i = Math.max(0, from); i < Math.min(cols, to); i++) {
			l.ch[i] = ' ';
			l.fg[i] = -1;
			l.bg[i] = bg;
			l.attr[i] = 0;
		}
	}

	void eraseLine(int mode) {
		if(mode == 0) {
			erase(cy, cx, cols);
		} else if(mode == 1) {
			erase(cy, 0, cx + 1);
		} else {
			erase(cy, 0, cols);
		}
	}

	void eraseDisplay(int mode) {
		if(mode == 0) {
			erase(cy, cx, cols);
			for(int r = cy + 1; r < rows; r++) {
				erase(r, 0, cols);
			}
		} else if(mode == 1) {
			for(int r = 0; r < cy; r++) {
				erase(r, 0, cols);
			}
			erase(cy, 0, cx + 1);
		} else {
			for(int r = 0; r < rows; r++) {
				erase(r, 0, cols);
			}
			if(mode == 3) {
				scrollback.clear();
			}
		}
	}

	void sgr(int[] n) {
		for(int i = 0; i < n.length; i++) {
			int v = n[i];
			if(v == 0) {
				fg = -1;
				bg = -1;
				attr = 0;
			} else if(v == 1) {
				attr |= BOLD;
			} else if(v == 2) {
				attr |= DIM;
			} else if(v == 3) {
				attr |= ITALIC;
			} else if(v == 4) {
				attr |= UNDERLINE;
			} else if(v == 7) {
				attr |= REVERSE;
			} else if(v == 22) {
				attr &= ~(BOLD | DIM);
			} else if(v == 23) {
				attr &= ~ITALIC;
			} else if(v == 24) {
				attr &= ~UNDERLINE;
			} else if(v == 27) {
				attr &= ~REVERSE;
			} else if(v >= 30 && v <= 37) {
				fg = v - 30;
			} else if(v == 39) {
				fg = -1;
			} else if(v >= 40 && v <= 47) {
				bg = v - 40;
			} else if(v == 49) {
				bg = -1;
			} else if(v >= 90 && v <= 97) {
				fg = v - 90 + 8;
			} else if(v >= 100 && v <= 107) {
				bg = v - 100 + 8;
			} else if(v == 38 || v == 48) {
				int color = -1;
				if(i + 2 < n.length && n[i + 1] == 5) {
					color = Math.min(255, n[i + 2]);
					i += 2;
				} else if(i + 4 < n.length && n[i + 1] == 2) {
					color = TRUE_COLOR | (Math.min(255, n[i + 2]) << 16) | (Math.min(255, n[i + 3]) << 8) | Math.min(255, n[i + 4]);
					i += 4;
				}
				if(v == 38) {
					fg = color;
				} else {
					bg = color;
				}
			}
		}
	}
}
