package javapad.ui.terminal;

import javapad.ui.Theme;
import javapad.ui.chat.ChatPane;
import javapad.ui.editor.Location;
import javapad.ui.editor.ReadOnlyText;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javapad.model.EclipseProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

public class ConsolePane {
	static final Pattern TRACE = Pattern.compile("at ([\\w.$]+)\\.[\\w$<>]+\\((\\w+\\.java):(\\d+)\\)");

	private final Composite composite;
	private final StyledText text;
	private int inputStart;
	private boolean internal;
	private final List<Seg> segments = new ArrayList<Seg>();
	private final Map<Integer, Color> colors = new HashMap<Integer, Color>();
	private AnsiParser ansi = new AnsiParser();
	private final Theme theme;
	private EclipseProject project;
	private Consumer<Location> opener;
	private Consumer<String> inputSink;
	static final int[][] DARK = {{80, 80, 80}, {244, 71, 71}, {106, 190, 100}, {229, 192, 123}, {86, 156, 214}, {198, 120, 221}, {78, 201, 176}, {212, 212, 212},
		{128, 128, 128}, {255, 110, 110}, {140, 220, 130}, {255, 215, 130}, {120, 180, 240}, {220, 150, 240}, {110, 230, 200}, {255, 255, 255}};
	static final int[][] LIGHT = {{0, 0, 0}, {200, 0, 0}, {0, 130, 0}, {150, 110, 0}, {0, 60, 200}, {150, 0, 150}, {0, 130, 130}, {120, 120, 120},
		{90, 90, 90}, {230, 40, 40}, {0, 160, 0}, {180, 130, 0}, {40, 90, 230}, {180, 40, 180}, {0, 160, 160}, {60, 60, 60}};
	static final int[][] XTERM = {{0, 0, 0}, {205, 0, 0}, {0, 160, 0}, {190, 150, 0}, {30, 60, 230}, {190, 0, 190}, {0, 170, 170}, {229, 229, 229},
		{127, 127, 127}, {255, 60, 60}, {60, 220, 60}, {255, 235, 80}, {90, 120, 255}, {255, 90, 255}, {60, 230, 230}, {255, 255, 255}};

	static class Seg {
		int start;
		int length;
		boolean error;
		AnsiParser.Style style;
	}

	public ConsolePane(Composite parent, Theme theme) {
		this.theme = theme;
		composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.verticalSpacing = 0;
		composite.setLayout(layout);
		text = new StyledText(composite, SWT.V_SCROLL | SWT.H_SCROLL);
		text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		text.setFont(theme.font);
		ReadOnlyText.installKeys(text);
		// output and stdin share one text: everything before inputStart is output, everything after is the line being typed
		text.addVerifyListener(e -> {
			if(!internal && e.start < inputStart) {
				e.doit = false;
				if(!e.text.isEmpty()) {
					// typed or pasted into old output: it belongs on the input line instead
					int end = text.getCharCount();
					internal = true;
					text.replaceTextRange(end, 0, e.text);
					internal = false;
					text.setCaretOffset(end + e.text.length());
				}
			}
		});
		text.addListener(SWT.KeyDown, e -> key(e));
		text.addListener(SWT.MouseUp, e -> clicked(e.x, e.y));
		text.setMenu(menu());
		text.addListener(SWT.Dispose, e -> {
			for(Color c : colors.values()) {
				c.dispose();
			}
		});
	}

	Menu menu() {
		Menu menu = new Menu(text);
		MenuItem copy = ChatPane.item(menu, "Copy", () -> text.copy());
		ChatPane.item(menu, "Select All", () -> text.selectAll());
		new MenuItem(menu, SWT.SEPARATOR);
		ChatPane.item(menu, "Clear", () -> clear());
		menu.addListener(SWT.Show, e -> copy.setEnabled(text.getSelectionCount() > 0));
		return menu;
	}

	public Composite getControl() {
		return composite;
	}

	public void setProject(EclipseProject project) {
		this.project = project;
	}

	public void setOpener(Consumer<Location> opener) {
		this.opener = opener;
	}

	public void setInputSink(Consumer<String> sink) {
		inputSink = sink;
	}

	void key(Event e) {
		boolean enter = e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR;
		if(enter && (e.stateMask & (SWT.MOD1 | SWT.SHIFT | SWT.ALT)) == 0) {
			e.doit = false;
			sendInput();
			return;
		}
		boolean edit = e.character != 0 && e.character != SWT.ESC;
		boolean copyOrAll = (e.stateMask & SWT.MOD1) != 0 && (e.keyCode == 'c' || e.keyCode == 'a');
		if(edit && !copyOrAll && text.getSelectionCount() == 0 && text.getCaretOffset() < inputStart) {
			text.setCaretOffset(text.getCharCount());
		}
	}

	void sendInput() {
		String line = text.getText(inputStart, text.getCharCount() - 1) + "\n";
		Seg seg = new Seg();
		seg.start = inputStart;
		seg.length = line.length();
		seg.style = new AnsiParser.Style();
		internal = true;
		text.append("\n");
		internal = false;
		segments.add(seg);
		text.setStyleRange(styleFor(seg));
		inputStart = text.getCharCount();
		text.setCaretOffset(inputStart);
		text.setTopIndex(text.getLineCount() - 1);
		if(inputSink != null) {
			inputSink.accept(line);
		}
	}

	public void clear() {
		segments.clear();
		ansi = new AnsiParser();
		internal = true;
		text.setText("");
		internal = false;
		inputStart = 0;
	}

	public String getText() {
		return text.getText();
	}

	public void append(String s, boolean error) {
		int first = inputStart;
		int caret = text.getCaretOffset();
		boolean typing = caret >= inputStart;
		internal = true;
		for(AnsiParser.Span span : ansi.parse(s)) {
			Seg seg = new Seg();
			seg.start = inputStart;
			seg.length = span.text.length();
			seg.error = error;
			seg.style = span.style;
			text.replaceTextRange(inputStart, 0, span.text);
			inputStart += seg.length;
			text.setStyleRange(styleFor(seg));
			segments.add(seg);
			if(typing) {
				caret += seg.length;
			}
		}
		internal = false;
		if(inputStart > first) {
			linkify(first);
		}
		text.setCaretOffset(caret);
		text.setTopIndex(text.getLineCount() - 1);
	}

	StyleRange styleFor(Seg seg) {
		StyleRange range = new StyleRange();
		range.start = seg.start;
		range.length = seg.length;
		AnsiParser.Style st = seg.style;
		// with an explicit background, use true terminal colours so pairs like white-on-blue keep their contrast
		boolean literal = st.bg != AnsiParser.DEFAULT && !theme.dark;
		Color fg = st.fg == AnsiParser.DEFAULT ? null : colorOf(st.fg, literal);
		Color bg = st.bg == AnsiParser.DEFAULT ? null : colorOf(st.bg, !theme.dark);
		Color normal = seg.error ? theme.errorText : theme.foreground;
		if(st.reverse) {
			range.foreground = bg != null ? bg : theme.background;
			range.background = fg != null ? fg : normal;
		} else {
			range.foreground = fg != null ? fg : normal;
			range.background = bg;
		}
		if(st.bold) {
			range.fontStyle |= SWT.BOLD;
		}
		if(st.italic) {
			range.fontStyle |= SWT.ITALIC;
		}
		range.underline = st.underline;
		return range;
	}

	// ANSI colour code to a colour: 0-15 follow the theme, 16-255 are the xterm cube/greys, RGB is literal
	Color colorOf(int code, boolean literal) {
		int key = code < 16 ? code + (theme.dark ? 0 : literal ? 2000 : 1000) : code;
		Color c = colors.get(key);
		if(c != null) {
			return c;
		}
		int r;
		int g;
		int b;
		if(code >= AnsiParser.RGB) {
			r = (code >> 16) & 255;
			g = (code >> 8) & 255;
			b = code & 255;
		} else if(code < 16) {
			int[] rgb = (theme.dark ? DARK : literal ? XTERM : LIGHT)[code];
			r = rgb[0];
			g = rgb[1];
			b = rgb[2];
		} else if(code < 232) {
			int n = code - 16;
			r = level(n / 36);
			g = level((n / 6) % 6);
			b = level(n % 6);
		} else {
			r = 8 + 10 * (code - 232);
			g = r;
			b = r;
		}
		c = new Color(text.getDisplay(), r, g, b);
		colors.put(key, c);
		return c;
	}

	static int level(int v) {
		return v == 0 ? 0 : 55 + 40 * v;
	}

	public void restyle() {
		text.setFont(theme.font);
		text.setBackground(theme.background);
		text.setForeground(theme.foreground);
		for(Seg seg : segments) {
			text.setStyleRange(styleFor(seg));
		}
		linkify(0);
	}

	void linkify(int start) {
		if(text.getCharCount() == 0) {
			return;
		}
		int firstLine = text.getLineAtOffset(start);
		int from = text.getOffsetAtLine(firstLine);
		String chunk = text.getText(from, text.getCharCount() - 1);
		Matcher m = TRACE.matcher(chunk);
		while(m.find()) {
			File file = resolve(m.group(1), m.group(2));
			if(file == null) {
				continue;
			}
			StyleRange link = new StyleRange();
			link.start = from + m.start() + 3;
			link.length = m.end() - m.start() - 3;
			link.foreground = theme.errorText;
			link.underline = true;
			link.underlineStyle = SWT.UNDERLINE_LINK;
			link.data = new Location(file, Integer.parseInt(m.group(3)), 1);
			text.setStyleRange(link);
		}
	}

	File resolve(String qualifiedClass, String fileName) {
		if(project == null) {
			return null;
		}
		return project.findSource(qualifiedClass, fileName);
	}

	void clicked(int x, int y) {
		if(opener == null) {
			return;
		}
		try {
			int offset = text.getOffsetAtPoint(new Point(x, y));
			StyleRange r = text.getStyleRangeAtOffset(offset);
			if(r != null && r.data instanceof Location) {
				opener.accept((Location)r.data);
			}
		} catch(IllegalArgumentException e) {
			// the click was not over any text
		}
	}
}
