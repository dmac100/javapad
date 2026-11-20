package javapad.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import javapad.model.Problem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;

// A strip to the right of the editor's scrollbar with a mark for each problem and each line that differs from the last commit, placed by where its line is in the file.
// Clicking a mark goes to its line.
class OverviewRuler
{
	static final int WIDTH = 14;
	static final int MARK_HEIGHT = 4;
	// a click or hover this close to a mark counts as on it
	static final int SLOP = 2;

	private final Canvas canvas;
	private final StyledText text;
	private final Theme theme;
	private final IntConsumer goToLine;
	private List<Problem> problems = new ArrayList<Problem>();
	// the 0-based lines that differ from the last commit
	private Set<Integer> changed = new HashSet<Integer>();
	private String shownTip;
	private final Cursor hand;

	OverviewRuler(Composite parent, StyledText text, Theme theme, IntConsumer goToLine)
	{
		this.text = text;
		this.theme = theme;
		this.goToLine = goToLine;
		canvas = new Canvas(parent, SWT.DOUBLE_BUFFERED);
		canvas.addListener(SWT.Paint, e -> paint(e.gc));
		canvas.addListener(SWT.Resize, e -> canvas.redraw());
		canvas.addListener(SWT.MouseDown, e -> click(e));
		canvas.addListener(SWT.MouseMove, e -> hover(e));
		hand = new Cursor(canvas.getDisplay(), SWT.CURSOR_HAND);
		canvas.addListener(SWT.Dispose, e -> hand.dispose());
	}

	Canvas getControl()
	{
		return canvas;
	}

	void setProblems(List<Problem> list)
	{
		problems = list;
		canvas.redraw();
	}

	void setChangedLines(Set<Integer> lines)
	{
		changed = lines;
		canvas.redraw();
	}

	void redraw()
	{
		canvas.redraw();
	}

	// the top of the mark for a line (1-based)
	int markY(int line)
	{
		int lines = Math.max(1, text.getLineCount());
		int h = canvas.getClientArea().height - MARK_HEIGHT;
		return (int)((long)(Math.min(line, lines) - 1) * h / lines);
	}

	void paint(GC gc)
	{
		Rectangle area = canvas.getClientArea();
		gc.setBackground(theme.background);
		gc.fillRectangle(area);
		gc.setBackground(theme.modified);
		for(int line : changed)
		{
			gc.fillRectangle(2, markY(line + 1), area.width - 4, MARK_HEIGHT);
		}
		// warnings first, so an error on the same spot is drawn over them
		for(int pass = 0; pass < 2; pass++)
		{
			boolean errors = pass == 1;
			gc.setBackground(errors ? theme.errorMark : theme.warning);
			for(Problem p : problems)
			{
				if(p.error == errors && p.line > 0)
				{
					gc.fillRectangle(2, markY(p.line), area.width - 4, MARK_HEIGHT);
				}
			}
		}
	}

	// the problems whose marks are at height y, errors first
	List<Problem> at(int y)
	{
		List<Problem> found = new ArrayList<Problem>();
		for(int pass = 0; pass < 2; pass++)
		{
			for(Problem p : problems)
			{
				int top = markY(p.line);
				boolean hit = p.line > 0 && y >= top - SLOP && y < top + MARK_HEIGHT + SLOP;
				if(hit && p.error == (pass == 0))
				{
					found.add(p);
				}
			}
		}
		return found;
	}

	// the first changed line (1-based) whose mark is at height y, or 0
	int changeAt(int y)
	{
		int best = 0;
		for(int line : changed)
		{
			int top = markY(line + 1);
			if(y >= top - SLOP && y < top + MARK_HEIGHT + SLOP && (best == 0 || line + 1 < best))
			{
				best = line + 1;
			}
		}
		return best;
	}

	void click(Event e)
	{
		List<Problem> found = at(e.y);
		if(e.button == 1 && !found.isEmpty())
		{
			goToLine.accept(found.get(0).line);
		}
		else if(e.button == 1)
		{
			int near = changeAt(e.y);
			if(near > 0)
			{
				goToLine.accept(near);
			}
		}
	}

	void hover(Event e)
	{
		List<Problem> found = at(e.y);
		canvas.setCursor(found.isEmpty() ? null : hand);
		String tip = null;
		if(!found.isEmpty())
		{
			StringBuilder sb = new StringBuilder();
			for(Problem p : found)
			{
				if(sb.length() > 0)
				{
					sb.append("\n");
				}
				sb.append("Line ").append(p.line).append(": ").append(p.message);
			}
			tip = sb.toString();
		}
		if(tip == null ? shownTip != null : !tip.equals(shownTip))
		{
			shownTip = tip;
			canvas.setToolTipText(tip);
		}
	}
}
