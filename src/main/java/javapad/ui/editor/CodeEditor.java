package javapad.ui.editor;

import javapad.ui.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javapad.git.LineDiff;
import javapad.lang.CompletionEngine;
import javapad.lang.ImportEditor;
import javapad.lang.IndentConverter;
import javapad.lang.JavaFormatter;
import javapad.lang.LineEndings;
import javapad.lang.Proposal;
import javapad.lang.SelectionRanges;
import javapad.model.Problem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.Bullet;
import org.eclipse.swt.custom.ExtendedModifyEvent;
import org.eclipse.swt.custom.ExtendedModifyListener;
import org.eclipse.swt.custom.LineStyleEvent;
import org.eclipse.swt.custom.ST;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GlyphMetrics;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;

public class CodeEditor
{
	private final Composite frame;
	private final StyledText text;
	private final OverviewRuler ruler;
	private final Theme theme;
	private final JavaLexer lexer;
	private final List<Integer> lineStates = new ArrayList<Integer>();
	private final List<Edit> undo = new ArrayList<Edit>();
	private final List<Edit> redo = new ArrayList<Edit>();
	private List<Problem> problems = new ArrayList<Problem>();
	private Bullet normalBullet;
	private Bullet errorBullet;
	private Bullet warningBullet;
	private static final int TAB_WIDTH = 4;
	// the committed text of the file, and the lines of the editor that differ from it
	private String baseline;
	private Set<Integer> changedLines = new HashSet<Integer>();
	private int changeRequest;
	private int bulletDigits;
	private boolean replaying;
	private Runnable modifyHook;
	private Runnable compileHook;
	private final Completion completion;
	private final LineEdits edits;
	private boolean[] codeMask;
	private int[] bracketPair;
	private String shownTip;
	private Assist assist;
	private Navigation navigation;
	private OccurrenceFinder occurrenceFinder;
	// the places that use the selected identifier, as {start, end} offsets
	private List<int[]> occurrences = new ArrayList<int[]>();
	private int occurrenceRequest;
	private CompletionPopup popup;
	private int completionGeneration;
	private final boolean java;
	// the selections that Alt+Shift+Up grew from, so Alt+Shift+Down can go back through them
	private final List<Point> grownFrom = new ArrayList<Point>();
	private Point grownTo;

	// Finds the other uses of the identifier text[offset, offset + length); calls back on the UI thread.
	public interface OccurrenceFinder
	{
		void find(String text, int offset, int length, Consumer<List<int[]>> done);
	}

	public interface Navigation
	{
		void declaration(int offset);

		void implementation(int offset);
	}

	public interface Assist
	{
		void complete(String text, int offset, Consumer<CompletionEngine.Completions> done);
	}

	static class Edit
	{
		int start;
		String oldText;
		String newText;
	}

	// java: full syntax colouring; otherwise only numbers and quoted text are coloured
	public CodeEditor(Composite parent, Theme theme, boolean java)
	{
		this.theme = theme;
		this.java = java;
		lexer = new JavaLexer(java);
		frame = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.horizontalSpacing = 0;
		frame.setLayout(layout);
		frame.setBackground(theme.background);
		text = new StyledText(frame, SWT.V_SCROLL | SWT.H_SCROLL);
		text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		text.setFont(theme.font);
		text.setBackground(theme.background);
		text.setForeground(theme.foreground);
		text.setTabs(TAB_WIDTH);
		ruler = new OverviewRuler(frame, text, theme, line -> goTo(line, 1));
		GridData rulerData = new GridData(SWT.FILL, SWT.FILL, false, true);
		rulerData.widthHint = OverviewRuler.WIDTH;
		ruler.getControl().setLayoutData(rulerData);
		text.addListener(SWT.Paint, e ->
		{
			paintWhitespace(e.gc);
			paintChanges(e.gc);
			paintMarks(e.gc);
		});
		lineStates.add(JavaLexer.STATE_NORMAL);
		makeBullets();
		text.addLineStyleListener(e -> styleLine(e));
		text.addExtendedModifyListener(new ExtendedModifyListener()
		{
			public void modifyText(ExtendedModifyEvent e)
			{
				modified(e);
			}
		});
		completion = new Completion(text);
		edits = new LineEdits(text);
		text.addTraverseListener(e ->
		{
			if(e.detail == SWT.TRAVERSE_TAB_PREVIOUS || e.detail == SWT.TRAVERSE_TAB_NEXT)
			{
				e.doit = false;
			}
		});
		text.addVerifyKeyListener(e -> keyPressed(e));
		popup = new CompletionPopup(text, theme, p -> acceptProposal(p));
		text.addCaretListener(e ->
		{
			updateBracket();
			popup.refresh();
		});
		text.addListener(SWT.Selection, e -> selectionChanged());
		text.addListener(SWT.FocusOut, e -> popup.close());
		text.addListener(SWT.MouseDown, e -> controlClick(e));
		text.addListener(SWT.MouseDown, e -> popup.close());
		text.addListener(SWT.Dispose, e -> popup.dispose());
		text.addListener(SWT.MouseMove, e -> updateTip(e.x, e.y));
		LineScroll.install(text);
	}

	public void setOccurrenceFinder(OccurrenceFinder finder)
	{
		this.occurrenceFinder = finder;
	}

	// When a whole identifier is selected, shows where else the same thing is used.
	void selectionChanged()
	{
		if(occurrenceFinder == null)
		{
			return;
		}
		int request = ++occurrenceRequest;
		Point sel = text.getSelection();
		boolean small = sel.y > sel.x && sel.y - sel.x <= 100;
		String content = small ? text.getText() : "";
		if(!small || !isIdentifier(content, sel.x, sel.y))
		{
			setOccurrences(new ArrayList<int[]>());
			return;
		}
		// wait a moment so that dragging a selection does not start an analysis for every position
		text.getDisplay().timerExec(250, () ->
		{
			if(text.isDisposed() || request != occurrenceRequest)
			{
				return;
			}
			occurrenceFinder.find(content, sel.x, sel.y - sel.x, found ->
			{
				if(!text.isDisposed() && request == occurrenceRequest && found != null)
				{
					setOccurrences(found.size() > 1 ? found : new ArrayList<int[]>());
				}
			});
		});
	}

	static boolean isIdentifier(String content, int start, int end)
	{
		if(end > content.length() || !Character.isJavaIdentifierStart(content.charAt(start)))
		{
			return false;
		}
		for(int i = start; i < end; i++)
		{
			if(!Character.isJavaIdentifierPart(content.charAt(i)))
			{
				return false;
			}
		}
		boolean before = start == 0 || !Character.isJavaIdentifierPart(content.charAt(start - 1));
		boolean after = end >= content.length() || !Character.isJavaIdentifierPart(content.charAt(end));
		return before && after && !JavaLexer.KEYWORDS.contains(content.substring(start, end));
	}

	void setOccurrences(List<int[]> list)
	{
		if(occurrences.isEmpty() && list.isEmpty())
		{
			return;
		}
		occurrences = list;
		text.redraw();
	}

	public StyledText getControl()
	{
		return text;
	}

	// the editor with its overview ruler, the control to put in a tab
	public Composite getFrame()
	{
		return frame;
	}

	public void restyle()
	{
		text.setFont(theme.font);
		text.setBackground(theme.background);
		text.setForeground(theme.foreground);
		frame.setBackground(theme.background);
		makeBullets();
		text.redraw();
		ruler.redraw();
	}

	public void setCompileHook(Runnable hook)
	{
		compileHook = hook;
	}

	public void setModifyHook(Runnable hook)
	{
		modifyHook = hook;
	}

	public void setContent(String content)
	{
		replaying = true;
		text.setText(content);
		replaying = false;
		undo.clear();
		redo.clear();
		rebuildStates(0);
		updateBullets();
		text.setTopIndex(0);
	}

	public String getContent()
	{
		return text.getText();
	}

	public void setProblems(List<Problem> list)
	{
		problems = list;
		makeBullets();
		text.redraw();
		ruler.setProblems(list);
	}

	public void goTo(int line, int column)
	{
		int lines = text.getLineCount();
		int l = Math.max(0, Math.min(lines - 1, line - 1));
		int offset = text.getOffsetAtLine(l);
		int lineLength = text.getLine(l).length();
		offset += Math.max(0, Math.min(lineLength, column - 1));
		text.setCaretOffset(offset);
		text.setTopIndex(Math.max(0, l - 8));
		text.showSelection();
		text.setFocus();
	}

	// Selects the whole line and scrolls to it, leaving the keyboard focus where it is.
	public void highlightLine(int line)
	{
		int l = Math.max(0, Math.min(text.getLineCount() - 1, line - 1));
		int start = text.getOffsetAtLine(l);
		text.setSelection(start, start + text.getLine(l).length());
		int visible = Math.max(1, text.getClientArea().height / Math.max(1, text.getLineHeight()));
		if(l < text.getTopIndex() || l >= text.getTopIndex() + visible - 1)
		{
			text.setTopIndex(Math.max(0, l - visible / 3));
		}
	}

	void makeBullets()
	{
		GC gc = new GC(text);
		gc.setFont(theme.font);
		int charWidth = gc.getFontMetrics().getAverageCharWidth();
		gc.dispose();
		text.setLeftMargin(markSize() + 6);
		bulletDigits = Math.max(3, String.valueOf(text.getLineCount()).length());
		// a whole number of tab stops, as tabs are measured from the start of the line number column
		int bulletWidth = (bulletDigits + 1 + TAB_WIDTH - 1) / TAB_WIDTH * TAB_WIDTH;
		StyleRange normal = new StyleRange();
		normal.foreground = theme.gutter;
		normal.metrics = new GlyphMetrics(0, 0, bulletWidth * charWidth);
		normalBullet = new Bullet(ST.BULLET_NUMBER, normal);
		StyleRange bad = new StyleRange();
		bad.foreground = theme.error;
		bad.fontStyle = SWT.BOLD;
		bad.metrics = new GlyphMetrics(0, 0, bulletWidth * charWidth);
		errorBullet = new Bullet(ST.BULLET_NUMBER, bad);
		StyleRange warn = new StyleRange();
		warn.foreground = theme.warning;
		warn.fontStyle = SWT.BOLD;
		warn.metrics = new GlyphMetrics(0, 0, bulletWidth * charWidth);
		warningBullet = new Bullet(ST.BULLET_NUMBER, warn);
	}

	void updateBullets()
	{
		int digits = Math.max(3, String.valueOf(text.getLineCount()).length());
		if(digits != bulletDigits)
		{
			makeBullets();
			text.redraw();
		}
	}

	// the error and warning signs are a little smaller than a line
	int markSize()
	{
		return Math.max(7, Math.min(11, text.getLineHeight() - 6));
	}

	// an error or warning sign in the left margin of each line with a problem
	// Draws a dot over each space, an arrow over each tab and the kind of line break at the end of each visible line.
	void paintWhitespace(GC gc)
	{
		if(!theme.showWhitespace)
		{
			return;
		}
		int lines = text.getLineCount();
		int first = text.getTopIndex();
		int visible = text.getClientArea().height / Math.max(1, text.getLineHeight()) + 2;
		int width = text.getClientArea().width;
		// the text colour, faded: visible on both the dark and the light background
		gc.setForeground(theme.foreground);
		gc.setBackground(theme.foreground);
		gc.setAlpha(60);
		for(int line = first; line < Math.min(lines, first + visible); line++)
		{
			String content = text.getLine(line);
			int start = text.getOffsetAtLine(line);
			int y = text.getLinePixel(line);
			int lineHeight = text.getLineHeight(start);
			for(int i = 0; i < content.length(); i++)
			{
				char c = content.charAt(i);
				if(c != ' ' && c != '\t')
				{
					continue;
				}
				Point from = text.getLocationAtOffset(start + i);
				Point to = text.getLocationAtOffset(start + i + 1);
				if(to.x < 0 || from.x > width)
				{
					continue;
				}
				int mid = y + lineHeight / 2;
				if(c == ' ')
				{
					gc.fillRectangle((from.x + to.x) / 2 - 1, mid, 3, 3);
				}
				else if(to.x - from.x > 4)
				{
					gc.drawLine(from.x + 2, mid, to.x - 3, mid);
					gc.drawLine(to.x - 6, mid - 3, to.x - 3, mid);
					gc.drawLine(to.x - 6, mid + 3, to.x - 3, mid);
				}
			}
			int end = start + content.length();
			String ending = line + 1 < lines ? text.getText(end, text.getOffsetAtLine(line + 1) - 1) : "";
			String label = ending.replace("\r", "\u2190").replace("\n", "\u2193");
			Point at = text.getLocationAtOffset(end);
			if(!label.isEmpty() && at.x < width)
			{
				gc.drawString(label, at.x + 4, y, true);
			}
		}
		gc.setAlpha(255);
	}

	// Sets every line break of the file to the given ending (a single undo step).
	public void convertLineEndings(String ending)
	{
		replaceContentMinimal(LineEndings.convert(text.getText(), ending));
	}

	public void redrawWhitespace()
	{
		text.redraw();
	}

	// Sets the text the file has in the last commit (null if it has none) and marks the lines that differ from it.
	public void setBaseline(String text)
	{
		baseline = text;
		updateChangedLines();
	}

	void updateChangedLines()
	{
		int request = ++changeRequest;
		if(baseline == null)
		{
			changedLines = new HashSet<Integer>();
			ruler.setChangedLines(changedLines);
			text.redraw();
			return;
		}
		// wait for a pause in typing
		text.getDisplay().timerExec(200, () ->
		{
			if(text.isDisposed() || request != changeRequest)
			{
				return;
			}
			changedLines = LineDiff.changedLines(baseline, text.getText());
			ruler.setChangedLines(changedLines);
			text.redraw();
		});
	}

	// a bar at the left edge of each line that is new or differs from the last commit
	void paintChanges(GC gc)
	{
		if(changedLines.isEmpty())
		{
			return;
		}
		gc.setBackground(theme.modified);
		int lines = text.getLineCount();
		int first = text.getTopIndex();
		int visible = text.getClientArea().height / Math.max(1, text.getLineHeight()) + 2;
		for(int line = first; line < Math.min(lines, first + visible); line++)
		{
			if(changedLines.contains(line))
			{
				gc.fillRectangle(0, text.getLinePixel(line), 3, text.getLineHeight(text.getOffsetAtLine(line)));
			}
		}
	}

	void paintMarks(GC gc)
	{
		if(problems.isEmpty())
		{
			return;
		}
		int size = markSize();
		int lines = text.getLineCount();
		int first = text.getTopIndex();
		int visible = text.getClientArea().height / Math.max(1, text.getLineHeight()) + 2;
		for(int line = first; line < Math.min(lines, first + visible); line++)
		{
			boolean error = lineHasProblem(line, true);
			if(!error && !lineHasProblem(line, false))
			{
				continue;
			}
			int y = text.getLinePixel(line) + (text.getLineHeight(text.getOffsetAtLine(line)) - size) / 2;
			if(error)
			{
				ProblemMarks.drawError(gc, theme, 3, y, size);
			}
			else
			{
				ProblemMarks.drawWarning(gc, theme, 3, y, size);
			}
		}
	}

	boolean lineHasProblem(int line, boolean error)
	{
		for(Problem p : problems)
		{
			if(p.error == error && p.line - 1 == line)
			{
				return true;
			}
		}
		return false;
	}

	void updateBracket()
	{
		if(codeMask == null || codeMask.length != text.getCharCount())
		{
			codeMask = BracketMatcher.codeMask(text.getText());
		}
		int[] pair = BracketMatcher.find(text.getText(), codeMask, text.getCaretOffset());
		if(!Arrays.equals(pair, bracketPair))
		{
			bracketPair = pair;
			text.redraw();
		}
	}

	public void goToMatchingBracket()
	{
		updateBracket();
		if(bracketPair != null)
		{
			// caret just after the partner when jumping forward, so the pair stays highlighted
			int target = bracketPair[1] > bracketPair[0] ? bracketPair[1] + 1 : bracketPair[1];
			text.setCaretOffset(target);
			text.showSelection();
		}
	}

	// Selects the next larger piece of code around the selection: word, expression, statement, block, method, class, file.
	public void expandSelection()
	{
		Point sel = text.getSelection();
		if(!sel.equals(grownTo))
		{
			grownFrom.clear();
		}
		int[] r = SelectionRanges.expand(text.getText(), sel.x, sel.y, java);
		if(r[0] == sel.x && r[1] == sel.y)
		{
			return;
		}
		grownFrom.add(new Point(sel.x, sel.y));
		select(r[0], r[1]);
	}

	// Undoes the last expandSelection, or picks the largest piece of code inside the selection around the caret.
	public void shrinkSelection()
	{
		Point sel = text.getSelection();
		if(sel.x == sel.y)
		{
			return;
		}
		if(sel.equals(grownTo) && !grownFrom.isEmpty())
		{
			Point p = grownFrom.remove(grownFrom.size() - 1);
			select(p.x, p.y);
			return;
		}
		grownFrom.clear();
		int[] r = SelectionRanges.shrink(text.getText(), sel.x, sel.y, text.getCaretOffset(), java);
		select(r[0], r[1]);
	}

	void select(int start, int end)
	{
		text.setSelection(start, end);
		grownTo = text.getSelection();
		text.showSelection();
		selectionChanged();
	}

	String problemTip(int x, int y)
	{
		int lineCount = text.getLineCount();
		int line;
		try
		{
			line = text.getLineIndex(y);
		}
		catch(IllegalArgumentException ex)
		{
			return null;
		}
		if(line < 0 || line >= lineCount)
		{
			return null;
		}
		int lineStart = text.getOffsetAtLine(line);
		String content = text.getLine(line);
		int textX = text.getLocationAtOffset(lineStart).x;
		boolean gutter = x < textX;
		int offset = -1;
		if(!gutter)
		{
			try
			{
				offset = text.getOffsetAtPoint(new Point(x, y));
			}
			catch(IllegalArgumentException ex)
			{
				return null;
			}
		}
		StringBuilder tip = new StringBuilder();
		for(Problem p : problems)
		{
			if(p.line - 1 != line)
			{
				continue;
			}
			boolean hit = gutter;
			if(!hit && offset >= 0)
			{
				boolean[] bad = new boolean[content.length()];
				markBad(content, bad, p.column - 1);
				int rel = offset - lineStart;
				hit = rel >= 0 && rel < bad.length && bad[rel];
			}
			if(hit)
			{
				if(tip.length() > 0)
				{
					tip.append("\n");
				}
				tip.append(p.error ? "Error: " : "Warning: ").append(p.message);
			}
		}
		return tip.length() == 0 ? null : tip.toString();
	}

	void updateTip(int x, int y)
	{
		String tip = problems.isEmpty() ? null : problemTip(x, y);
		if(tip == null ? shownTip != null : !tip.equals(shownTip))
		{
			shownTip = tip;
			text.setToolTipText(tip);
		}
	}

	void styleLine(LineStyleEvent e)
	{
		int line = text.getLineAtOffset(e.lineOffset);
		e.bulletIndex = line;
		e.bullet = lineHasProblem(line, true) ? errorBullet : lineHasProblem(line, false) ? warningBullet : normalBullet;
		String content = e.lineText;
		int state = line < lineStates.size() ? lineStates.get(line) : JavaLexer.STATE_NORMAL;
		int[] styles = lexer.styles(content, state);
		boolean[] bad = new boolean[content.length()];
		boolean[] warn = new boolean[content.length()];
		for(Problem p : problems)
		{
			if(p.line - 1 == line)
			{
				markBad(content, p.error ? bad : warn, p.column - 1);
			}
		}
		boolean[] occ = new boolean[content.length()];
		for(int[] range : occurrences)
		{
			for(int k = Math.max(range[0], e.lineOffset); k < Math.min(range[1], e.lineOffset + content.length()); k++)
			{
				occ[k - e.lineOffset] = true;
			}
		}
		boolean[] match = new boolean[content.length()];
		if(bracketPair != null)
		{
			for(int p : bracketPair)
			{
				if(p >= e.lineOffset && p < e.lineOffset + content.length())
				{
					match[p - e.lineOffset] = true;
				}
			}
		}
		List<StyleRange> ranges = new ArrayList<StyleRange>();
		int i = 0;
		while(i < content.length())
		{
			int j = i + 1;
			while(j < content.length() && styles[j] == styles[i] && bad[j] == bad[i] && warn[j] == warn[i] && occ[j] == occ[i] && match[j] == match[i])
			{
				j++;
			}
			if(styles[i] != JavaLexer.PLAIN || bad[i] || warn[i] || occ[i] || match[i])
			{
				StyleRange r = new StyleRange();
				r.start = e.lineOffset + i;
				r.length = j - i;
				r.foreground = theme.colorFor(styles[i]);
				if(styles[i] == JavaLexer.KEYWORD)
				{
					r.fontStyle = SWT.BOLD;
				}
				if(match[i])
				{
					r.background = theme.bracket;
					r.fontStyle = SWT.BOLD;
				}
				else if(occ[i])
				{
					r.background = theme.occurrence;
				}
				if(bad[i] || warn[i])
				{
					r.underline = true;
					r.underlineStyle = SWT.UNDERLINE_SQUIGGLE;
					r.underlineColor = bad[i] ? theme.error : theme.warning;
				}
				ranges.add(r);
			}
			i = j;
		}
		e.styles = ranges.toArray(new StyleRange[0]);
	}

	static void markBad(String content, boolean[] bad, int column)
	{
		if(column < 0 || column >= content.length())
		{
			return;
		}
		int j = column;
		if(Character.isJavaIdentifierPart(content.charAt(j)))
		{
			while(j < content.length() && Character.isJavaIdentifierPart(content.charAt(j)))
			{
				j++;
			}
		}
		else
		{
			j++;
		}
		for(int k = column; k < j; k++)
		{
			bad[k] = true;
		}
	}

	void rebuildStates(int fromLine)
	{
		int lines = text.getLineCount();
		while(lineStates.size() > lines + 1)
		{
			lineStates.remove(lineStates.size() - 1);
		}
		while(lineStates.size() < lines + 1)
		{
			lineStates.add(JavaLexer.STATE_NORMAL);
		}
		int state = lineStates.get(Math.min(fromLine, lines));
		for(int l = fromLine; l < lines; l++)
		{
			lineStates.set(l, state);
			lexer.styles(text.getLine(l), state);
			state = lexer.endState;
		}
		lineStates.set(lines, state);
	}

	void modified(ExtendedModifyEvent e)
	{
		codeMask = null;
		occurrenceRequest++;
		occurrences = new ArrayList<int[]>();
		int firstLine = text.getLineAtOffset(e.start);
		lineStates.clear();
		rebuildStates(0);
		if(firstLine >= 0)
		{
			text.redraw();
		}
		updateBullets();
		ruler.redraw();
		if(baseline != null)
		{
			updateChangedLines();
		}
		if(!replaying)
		{
			record(e);
			autoTrigger(e);
		}
		if(modifyHook != null)
		{
			modifyHook.run();
		}
	}

	// Typing "." right after a name opens member completion, like Eclipse.
	void autoTrigger(ExtendedModifyEvent e)
	{
		if(assist == null || e.length != 1 || !text.getTextRange(e.start, 1).equals(".") || !e.replacedText.isEmpty())
		{
			return;
		}
		if(e.start == 0)
		{
			return;
		}
		char before = text.getTextRange(e.start - 1, 1).charAt(0);
		if(!(Character.isJavaIdentifierPart(before) || before == ')' || before == ']'))
		{
			return;
		}
		int dotOffset = e.start;
		text.getDisplay().timerExec(150, () ->
		{
			if(text.isDisposed() || text.getCaretOffset() != dotOffset + 1 || text.getCharCount() <= dotOffset || !text.getTextRange(dotOffset, 1).equals("."))
			{
				return;
			}
			boolean[] mask = BracketMatcher.codeMask(text.getText());
			if(dotOffset < mask.length && mask[dotOffset])
			{
				triggerCompletion(false);
			}
		});
	}

	void record(ExtendedModifyEvent e)
	{
		Edit edit = new Edit();
		edit.start = e.start;
		edit.oldText = e.replacedText;
		edit.newText = text.getTextRange(e.start, e.length);
		redo.clear();
		if(!undo.isEmpty())
		{
			Edit prev = undo.get(undo.size() - 1);
			boolean typing = prev.oldText.isEmpty() && edit.oldText.isEmpty() && edit.newText.length() == 1;
			if(typing && edit.start == prev.start + prev.newText.length() && Character.isLetterOrDigit(edit.newText.charAt(0)))
			{
				prev.newText = prev.newText + edit.newText;
				return;
			}
		}
		undo.add(edit);
	}

	public void undo()
	{
		if(undo.isEmpty())
		{
			return;
		}
		Edit edit = undo.remove(undo.size() - 1);
		replaying = true;
		text.replaceTextRange(edit.start, edit.newText.length(), edit.oldText);
		replaying = false;
		text.setCaretOffset(edit.start + edit.oldText.length());
		redo.add(edit);
	}

	public void redo()
	{
		if(redo.isEmpty())
		{
			return;
		}
		Edit edit = redo.remove(redo.size() - 1);
		replaying = true;
		text.replaceTextRange(edit.start, edit.oldText.length(), edit.newText);
		replaying = false;
		text.setCaretOffset(edit.start + edit.newText.length());
		undo.add(edit);
	}

	public void insertImport(String fqn, String pkg)
	{
		String content = text.getText();
		ImportEditor.Edit edit = ImportEditor.addImport(content, fqn, pkg);
		if(edit != null)
		{
			replaceContentMinimal(ImportEditor.apply(content, edit));
		}
	}

	// Formats the selected lines, or the whole file when nothing is selected.
	public void format()
	{
		Point sel = text.getSelection();
		boolean whole = sel.x == sel.y;
		int first = whole ? 0 : text.getLineAtOffset(sel.x);
		int last = whole ? Integer.MAX_VALUE : text.getLineAtOffset(sel.y);
		if(!whole && last > first && sel.y == text.getOffsetAtLine(last))
		{
			last--;
		}
		JavaFormatter.Result result = JavaFormatter.format(text.getText(), first, last);
		replaceContentMinimal(result.text);
		if(!whole)
		{
			int end = Math.min(result.lastLine + 1, text.getLineCount());
			int to = end < text.getLineCount() ? text.getOffsetAtLine(end) : text.getCharCount();
			text.setSelection(text.getOffsetAtLine(first), to);
		}
	}

	// Converts the indentation of the selected lines, or of the whole file, to tabs or to 4-column spaces.
	public void convertIndent(boolean toTabs)
	{
		Point sel = text.getSelection();
		boolean whole = sel.x == sel.y;
		int first = whole ? 0 : text.getLineAtOffset(sel.x);
		int last = whole ? Integer.MAX_VALUE : text.getLineAtOffset(sel.y);
		if(!whole && last > first && sel.y == text.getOffsetAtLine(last))
		{
			last--;
		}
		String result = toTabs ? IndentConverter.toTabs(text.getText(), first, last) : IndentConverter.toSpaces(text.getText(), first, last);
		replaceContentMinimal(result);
	}

	// Swaps in new content by replacing only the part that differs, so undo is one step and the caret and scroll position hold.
	public void replaceContentMinimal(String newContent)
	{
		String old = text.getText();
		int prefix = 0;
		int limit = Math.min(old.length(), newContent.length());
		while(prefix < limit && old.charAt(prefix) == newContent.charAt(prefix))
		{
			prefix++;
		}
		int suffix = 0;
		while(suffix < limit - prefix && old.charAt(old.length() - 1 - suffix) == newContent.charAt(newContent.length() - 1 - suffix))
		{
			suffix++;
		}
		int caret = text.getCaretOffset();
		int topOffset = text.getOffsetAtLine(text.getTopIndex());
		int delta = newContent.length() - old.length();
		int oldEnd = old.length() - suffix;
		text.replaceTextRange(prefix, oldEnd - prefix, newContent.substring(prefix, newContent.length() - suffix));
		text.setCaretOffset(caret >= oldEnd ? caret + delta : Math.min(caret, prefix + (newContent.length() - suffix - prefix)));
		int newTop = topOffset >= oldEnd ? topOffset + delta : Math.min(topOffset, text.getCharCount());
		text.setTopIndex(text.getLineAtOffset(Math.max(0, Math.min(newTop, text.getCharCount()))));
	}

	public void setNavigation(Navigation navigation)
	{
		this.navigation = navigation;
	}

	// Ctrl+click opens the declaration, Ctrl+Alt+click the implementation (Eclipse).
	void controlClick(Event e)
	{
		if(navigation == null || e.button != 1 || (e.stateMask & SWT.MOD1) == 0 || (e.stateMask & SWT.SHIFT) != 0)
		{
			return;
		}
		int offset;
		try
		{
			offset = text.getOffsetAtPoint(new Point(e.x, e.y));
		}
		catch(IllegalArgumentException ex)
		{
			return;
		}
		String content = text.getText();
		boolean onWord = (offset < content.length() && Character.isJavaIdentifierPart(content.charAt(offset))) || (offset > 0 && Character.isJavaIdentifierPart(content.charAt(offset - 1)));
		if(!onWord)
		{
			return;
		}
		text.setCaretOffset(offset);
		if((e.stateMask & SWT.ALT) != 0)
		{
			navigation.implementation(offset);
		}
		else
		{
			navigation.declaration(offset);
		}
	}

	public void setAssist(Assist assist)
	{
		this.assist = assist;
	}

	public CompletionPopup popup()
	{
		return popup;
	}

	// Asks the assist for proposals at the caret; manual (Ctrl+Space) inserts directly when there is only one.
	public void triggerCompletion(boolean manual)
	{
		if(assist == null || text.isDisposed())
		{
			return;
		}
		String content = text.getText();
		int offset = text.getCaretOffset();
		int generation = ++completionGeneration;
		assist.complete(content, offset, result ->
		{
			if(generation != completionGeneration || text.isDisposed() || !text.isFocusControl() && !Boolean.getBoolean("javapad.nofocuscheck"))
			{
				return;
			}
			popup.show(result, manual);
		});
	}

	void acceptProposal(Proposal p)
	{
		int caret = text.getCaretOffset();
		int wordStart = popup.wordStart();
		if(wordStart > caret)
		{
			return;
		}
		String content = text.getText();
		String insert = p.insert;
		int back = 0;
		if(p.isMethod())
		{
			boolean parenFollows = caret < content.length() && content.charAt(caret) == '(';
			if(!parenFollows)
			{
				insert += "()";
				back = p.hasParams ? 1 : 0;
			}
		}
		ImportEditor.Edit imp = p.importType == null ? null : ImportEditor.addImport(content, p.importType, p.detail);
		int top = text.getTopIndex();
		int newCaret;
		int addedLines = 0;
		if(imp != null && imp.offset <= wordStart && imp.length == 0)
		{
			String between = content.substring(imp.offset, wordStart);
			text.replaceTextRange(imp.offset, caret - imp.offset, imp.text + between + insert);
			newCaret = imp.offset + imp.text.length() + between.length() + insert.length() - back;
			addedLines = imp.text.length() - imp.text.replace("\n", "").length();
		}
		else
		{
			text.replaceTextRange(wordStart, caret - wordStart, insert);
			newCaret = wordStart + insert.length() - back;
		}
		text.setCaretOffset(newCaret);
		text.setTopIndex(top + addedLines);
		text.showSelection();
	}

	void keyPressed(VerifyEvent e)
	{
		boolean ctrl = (e.stateMask & SWT.MOD1) != 0;
		boolean shift = (e.stateMask & SWT.SHIFT) != 0;
		if(popup.handleKey(e) || shortcut(e, ctrl, shift))
		{
			return;
		}
		if(ctrl)
		{
			return;
		}
		typed(e, shift);
	}

	// Ctrl and Alt commands and Home; true when the key was handled here.
	boolean shortcut(VerifyEvent e, boolean ctrl, boolean shift)
	{
		if(ctrl && !shift && e.keyCode == ' ')
		{
			e.doit = false;
			triggerCompletion(true);
			return true;
		}
		if(ctrl && (e.keyCode == 'z' || e.keyCode == 'Z'))
		{
			e.doit = false;
			if(shift)
			{
				redo();
			}
			else
			{
				undo();
			}
			return true;
		}
		if(ctrl && (e.keyCode == 'y' || e.keyCode == 'Y'))
		{
			e.doit = false;
			redo();
			return true;
		}
		if(ctrl && (e.character == '\r' || e.character == '\n'))
		{
			e.doit = false;
			if(compileHook != null)
			{
				compileHook.run();
			}
			return true;
		}
		if(ctrl && e.keyCode == 'a')
		{
			e.doit = false;
			text.selectAll();
			return true;
		}
		if(ctrl && e.keyCode == 'd')
		{
			e.doit = false;
			edits.deleteLine();
			return true;
		}
		if(ctrl && e.keyCode == SWT.ARROW_UP)
		{
			text.setTopPixel(text.getTopPixel() - 20);
			return true;
		}
		if(ctrl && e.keyCode == SWT.ARROW_DOWN)
		{
			text.setTopPixel(text.getTopPixel() + 20);
			return true;
		}
		if((e.stateMask & SWT.ALT) != 0 && shift && e.keyCode == SWT.ARROW_UP)
		{
			e.doit = false;
			expandSelection();
			return true;
		}
		if((e.stateMask & SWT.ALT) != 0 && shift && e.keyCode == SWT.ARROW_DOWN)
		{
			e.doit = false;
			shrinkSelection();
			return true;
		}
		if((e.stateMask & SWT.ALT) != 0 && e.keyCode == SWT.ARROW_UP)
		{
			e.doit = false;
			edits.moveLines(true);
			return true;
		}
		if((e.stateMask & SWT.ALT) != 0 && e.keyCode == SWT.ARROW_DOWN)
		{
			e.doit = false;
			edits.moveLines(false);
			return true;
		}
		if(e.keyCode == SWT.HOME && (shift || e.stateMask == 0))
		{
			e.doit = false;
			edits.smartHome(shift);
			return true;
		}
		return false;
	}

	// Tab, Enter and the closing brace, which indent as you type.
	void typed(VerifyEvent e, boolean shift)
	{
		if(e.keyCode == SWT.TAB || e.character == '\t')
		{
			if(shift || text.getSelectionCount() > 0)
			{
				e.doit = false;
				edits.shiftLines(shift);
			}
			else if(completion.canComplete())
			{
				e.doit = false;
				completion.complete();
			}
			return;
		}
		if(e.character == '\r' || e.character == '\n')
		{
			e.doit = false;
			edits.newline();
			return;
		}
		if(e.character == '}')
		{
			int caret = text.getCaretOffset();
			int line = text.getLineAtOffset(caret);
			int lineStart = text.getOffsetAtLine(line);
			String before = text.getText(lineStart, caret - 1 < lineStart ? lineStart : caret - 1);
			if(caret > lineStart && before.trim().isEmpty() && text.getSelectionCount() == 0)
			{
				e.doit = false;
				String indent = Indenter.dedentOnce(before);
				text.replaceTextRange(lineStart, caret - lineStart, indent + "}");
				text.setCaretOffset(lineStart + indent.length() + 1);
			}
		}
	}
}
