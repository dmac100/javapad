package javapad.ui;

import java.util.ArrayList;
import java.util.List;
import javapad.lang.JavaFormatter;
import javapad.lang.ImportEditor;
import javapad.lang.Proposal;
import javapad.model.Problem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.Bullet;
import org.eclipse.swt.custom.ExtendedModifyEvent;
import org.eclipse.swt.custom.ExtendedModifyListener;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GlyphMetrics;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

public class CodeEditor
{
	private final StyledText text;
	private final Theme theme;
	private final JavaLexer lexer = new JavaLexer();
	private final List<Integer> lineStates = new ArrayList<Integer>();
	private final List<Edit> undo = new ArrayList<Edit>();
	private final List<Edit> redo = new ArrayList<Edit>();
	private List<Problem> problems = new ArrayList<Problem>();
	private Bullet normalBullet;
	private Bullet errorBullet;
	private int bulletDigits;
	private boolean replaying;
	private Runnable modifyHook;
	private Runnable compileHook;
	private final Completion completion;
	private boolean[] codeMask;
	private int[] bracketPair;
	private String shownTip;
	private Assist assist;
	private Navigation navigation;

	public interface Navigation
	{
		void declaration(int offset);

		void implementation(int offset);
	}
	private CompletionPopup popup;
	private int completionGeneration;

	public interface Assist
	{
		void complete(String text, int offset, java.util.function.Consumer<javapad.lang.CompletionEngine.Completions> done);
	}

	static class Edit
	{
		int start;
		String oldText;
		String newText;
	}

	public CodeEditor(Composite parent, Theme theme)
	{
		this.theme = theme;
		text = new StyledText(parent, SWT.V_SCROLL | SWT.H_SCROLL);
		text.setFont(theme.font);
		text.setBackground(theme.background);
		text.setForeground(theme.foreground);
		text.setTabs(4);
		text.setLeftMargin(4);
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
		text.addListener(SWT.FocusOut, e -> popup.close());
		text.addListener(SWT.MouseDown, e -> controlClick(e));
		text.addListener(SWT.MouseDown, e -> popup.close());
		text.addListener(SWT.Dispose, e -> popup.dispose());
		text.addListener(SWT.MouseMove, e -> updateTip(e.x, e.y));
		Listener wheel = e -> wheel(e);
		Display display = text.getDisplay();
		display.addFilter(SWT.MouseWheel, wheel);
		text.addListener(SWT.Dispose, e -> display.removeFilter(SWT.MouseWheel, wheel));
	}

	void wheel(Event e)
	{
		if(e.widget != text || (e.stateMask & (SWT.SHIFT | SWT.MOD1)) != 0 || e.count == 0)
		{
			return;
		}
		e.doit = false;
		int top = text.getTopIndex() - Integer.signum(e.count) * 3;
		int max = Math.max(0, text.getLineCount() - 1);
		text.setTopIndex(Math.max(0, Math.min(max, top)));
	}

	public StyledText getControl()
	{
		return text;
	}

	public void restyle()
	{
		text.setBackground(theme.background);
		text.setForeground(theme.foreground);
		makeBullets();
		text.redraw();
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
		bulletDigits = Math.max(3, String.valueOf(text.getLineCount()).length());
		StyleRange normal = new StyleRange();
		normal.foreground = theme.gutter;
		normal.metrics = new GlyphMetrics(0, 0, (bulletDigits + 1) * charWidth);
		normalBullet = new Bullet(org.eclipse.swt.custom.ST.BULLET_NUMBER, normal);
		StyleRange bad = new StyleRange();
		bad.foreground = theme.error;
		bad.fontStyle = SWT.BOLD;
		bad.metrics = new GlyphMetrics(0, 0, (bulletDigits + 1) * charWidth);
		errorBullet = new Bullet(org.eclipse.swt.custom.ST.BULLET_NUMBER, bad);
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

	boolean lineHasError(int line)
	{
		for(Problem p : problems)
		{
			if(p.error && p.line - 1 == line)
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
		if(!java.util.Arrays.equals(pair, bracketPair))
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
				offset = text.getOffsetAtPoint(new org.eclipse.swt.graphics.Point(x, y));
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

	void styleLine(org.eclipse.swt.custom.LineStyleEvent e)
	{
		int line = text.getLineAtOffset(e.lineOffset);
		e.bulletIndex = line;
		e.bullet = lineHasError(line) ? errorBullet : normalBullet;
		String content = e.lineText;
		int state = line < lineStates.size() ? lineStates.get(line) : JavaLexer.STATE_NORMAL;
		int[] styles = lexer.styles(content, state);
		boolean[] bad = new boolean[content.length()];
		for(Problem p : problems)
		{
			if(p.line - 1 == line)
			{
				markBad(content, bad, p.column - 1);
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
			while(j < content.length() && styles[j] == styles[i] && bad[j] == bad[i] && match[j] == match[i])
			{
				j++;
			}
			if(styles[i] != JavaLexer.PLAIN || bad[i] || match[i])
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
				if(bad[i])
				{
					r.underline = true;
					r.underlineStyle = SWT.UNDERLINE_SQUIGGLE;
					r.underlineColor = theme.error;
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
		int firstLine = text.getLineAtOffset(e.start);
		lineStates.clear();
		rebuildStates(0);
		if(firstLine >= 0)
		{
			text.redraw();
		}
		updateBullets();
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
		if(popup.handleKey(e))
		{
			return;
		}
		if(ctrl && !shift && e.keyCode == ' ')
		{
			e.doit = false;
			triggerCompletion(true);
			return;
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
			return;
		}
		if(ctrl && (e.keyCode == 'y' || e.keyCode == 'Y'))
		{
			e.doit = false;
			redo();
			return;
		}
		if(ctrl && (e.character == '\r' || e.character == '\n'))
		{
			e.doit = false;
			if(compileHook != null)
			{
				compileHook.run();
			}
			return;
		}
		if(ctrl && e.keyCode == 'a')
		{
			e.doit = false;
			text.selectAll();
			return;
		}
		if(ctrl && e.keyCode == 'd')
		{
			e.doit = false;
			deleteLine();
			return;
		}
		if(ctrl && e.keyCode == SWT.ARROW_UP)
		{
			text.setTopPixel(text.getTopPixel() - 20);
			return;
		}
		if(ctrl && e.keyCode == SWT.ARROW_DOWN)
		{
			text.setTopPixel(text.getTopPixel() + 20);
			return;
		}
		if((e.stateMask & SWT.ALT) != 0 && e.keyCode == SWT.ARROW_UP)
		{
			e.doit = false;
			moveLines(true);
			return;
		}
		if((e.stateMask & SWT.ALT) != 0 && e.keyCode == SWT.ARROW_DOWN)
		{
			e.doit = false;
			moveLines(false);
			return;
		}
		if(e.keyCode == SWT.HOME && (shift || e.stateMask == 0))
		{
			e.doit = false;
			smartHome(shift);
			return;
		}
		if(ctrl)
		{
			return;
		}
		if(e.keyCode == SWT.TAB || e.character == '\t')
		{
			if(shift || text.getSelectionCount() > 0)
			{
				e.doit = false;
				shiftLines(shift);
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
			newline();
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

	void smartHome(boolean select)
	{
		int caret = text.getCaretOffset();
		Point range = text.getSelectionRange();
		int anchor = caret == range.x ? range.x + range.y : range.x;
		int line = text.getLineAtOffset(caret);
		int lineStart = text.getOffsetAtLine(line);
		String content = text.getLine(line);
		int firstText = Indenter.indentOf(content).length();
		int target = lineStart;
		if(select)
		{
			if(caret - lineStart > firstText && lineStart + firstText != anchor)
			{
				target = lineStart + firstText;
			}
		}
		else if(caret - lineStart != firstText)
		{
			target = lineStart + firstText;
		}
		if(select)
		{
			text.setSelection(anchor, target);
		}
		else
		{
			text.setCaretOffset(target);
		}
		text.showSelection();
	}

	void deleteLine()
	{
		int line = text.getLineAtOffset(text.getCaretOffset());
		int start = text.getOffsetAtLine(line);
		int end = start + text.getLine(line).length();
		if(end < text.getCharCount())
		{
			end++;
		}
		text.replaceTextRange(start, end - start, "");
	}

	void moveLines(boolean up)
	{
		Point sel = text.getSelection();
		int first = text.getLineAtOffset(sel.x);
		int last = text.getLineAtOffset(sel.y);
		if(last > first && sel.y == text.getOffsetAtLine(last))
		{
			last--;
		}
		if(up && first == 0)
		{
			return;
		}
		if(!up && last + 1 >= text.getLineCount())
		{
			return;
		}
		int from = up ? first - 1 : first;
		int to = up ? last : last + 1;
		StringBuilder block = new StringBuilder();
		for(int l = first; l <= last; l++)
		{
			if(l > first)
			{
				block.append('\n');
			}
			block.append(text.getLine(l));
		}
		String other = text.getLine(up ? first - 1 : last + 1);
		String result = up ? block + "\n" + other : other + "\n" + block;
		int start = text.getOffsetAtLine(from);
		int end = text.getOffsetAtLine(to) + text.getLine(to).length();
		text.replaceTextRange(start, end - start, result);
		int newFirst = up ? first - 1 : first + 1;
		int newLast = up ? last - 1 : last + 1;
		text.setSelection(text.getOffsetAtLine(newFirst), text.getOffsetAtLine(newLast) + text.getLine(newLast).length());
	}

	void newline()
	{
		Point sel = text.getSelection();
		int line = text.getLineAtOffset(sel.x);
		int lineStart = text.getOffsetAtLine(line);
		String before = text.getText(lineStart, sel.x - 1 < lineStart ? lineStart : sel.x - 1);
		if(sel.x == lineStart)
		{
			before = "";
		}
		String indent = Indenter.newlineIndent(before);
		String insert = "\n" + indent;
		text.replaceTextRange(sel.x, sel.y - sel.x, insert);
		text.setCaretOffset(sel.x + insert.length());
		text.showSelection();
	}

	void shiftLines(boolean outdent)
	{
		Point sel = text.getSelection();
		int first = text.getLineAtOffset(sel.x);
		int last = text.getLineAtOffset(sel.y);
		if(last > first && sel.y == text.getOffsetAtLine(last))
		{
			last--;
		}
		int start = text.getOffsetAtLine(first);
		int end = last + 1 < text.getLineCount() ? text.getOffsetAtLine(last + 1) - 1 : text.getCharCount();
		String block = text.getTextRange(start, end - start);
		String[] lines = block.split("\n", -1);
		StringBuilder result = new StringBuilder();
		int firstDelta = 0;
		int total = 0;
		for(int i = 0; i < lines.length; i++)
		{
			String shifted = outdent ? Indenter.outdentLines(lines[i]) : Indenter.indentLines(lines[i]);
			if(i > 0)
			{
				result.append('\n');
			}
			result.append(shifted);
			if(i == 0)
			{
				firstDelta = shifted.length() - lines[i].length();
			}
			total += shifted.length() - lines[i].length();
		}
		text.replaceTextRange(start, end - start, result.toString());
		int x = Math.max(start, sel.x + firstDelta);
		int y = Math.max(x, sel.y + total);
		text.setSelection(x, y);
	}
}
