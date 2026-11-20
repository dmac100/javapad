package javapad.ui.editor;

import javapad.ui.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javapad.lang.CompletionEngine;
import javapad.lang.Names;
import javapad.lang.Proposal;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

// The proposal list under the caret. It never takes focus: the editor keeps it and forwards keys here while the list is open.
public class CompletionPopup
{
	static final int ROWS = 10;

	private final StyledText text;
	private final Theme theme;
	private final Consumer<Proposal> acceptor;
	private Table table;
	private List<Proposal> all = new ArrayList<Proposal>();
	private List<Proposal> shown = new ArrayList<Proposal>();
	private int wordStart;

	public CompletionPopup(StyledText text, Theme theme, Consumer<Proposal> acceptor)
	{
		this.text = text;
		this.theme = theme;
		this.acceptor = acceptor;
	}

	public boolean isOpen()
	{
		return table != null && !table.isDisposed() && table.isVisible();
	}

	public int wordStart()
	{
		return wordStart;
	}

	public List<Proposal> proposals()
	{
		return shown;
	}

	public Table getControl()
	{
		return table;
	}

	public void show(CompletionEngine.Completions completions, boolean manual)
	{
		all = completions.proposals;
		wordStart = completions.start;
		if(!filter())
		{
			close();
			return;
		}
		if(manual && shown.size() == 1)
		{
			Proposal only = shown.get(0);
			close();
			acceptor.accept(only);
			return;
		}
		if(table == null || table.isDisposed())
		{
			build();
		}
		// the editor font may have changed since the list was made
		table.setFont(theme.font);
		fill();
		place();
		table.setVisible(true);
		table.moveAbove(null);
	}

	String prefix()
	{
		int caret = text.getCaretOffset();
		if(caret < wordStart)
		{
			return null;
		}
		if(caret == wordStart)
		{
			return "";
		}
		String word = text.getText(wordStart, caret - 1);
		for(int i = 0; i < word.length(); i++)
		{
			if(!Character.isJavaIdentifierPart(word.charAt(i)))
			{
				return null;
			}
		}
		return word;
	}

	boolean filter()
	{
		String prefix = prefix();
		if(prefix == null)
		{
			return false;
		}
		shown = new ArrayList<Proposal>();
		for(Proposal p : all)
		{
			if(Names.matches(prefix, p.name))
			{
				shown.add(p);
			}
		}
		return !shown.isEmpty();
	}

	void build()
	{
		// a child of the editor rather than a window of its own, so it can never take the keyboard focus
		table = new Table(text, SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.BORDER);
		table.setVisible(false);
		table.setLinesVisible(false);
		table.setHeaderVisible(false);
		table.setFont(theme.font);
		new TableColumn(table, SWT.NONE);
		new TableColumn(table, SWT.NONE);
		table.addListener(SWT.MouseDoubleClick, e -> acceptSelected());
		table.addListener(SWT.MouseUp, e -> text.setFocus());
	}

	void fill()
	{
		table.setBackground(theme.background);
		table.setForeground(theme.foreground);
		table.setRedraw(false);
		table.removeAll();
		for(Proposal p : shown)
		{
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(new String[] {p.kindLetter() + "  " + p.label, p.detail});
			item.setForeground(1, theme.gutter);
			if(p.kind == Proposal.LOCAL)
			{
				item.setForeground(0, theme.pass);
			}
			else if(p.kind == Proposal.KEYWORD)
			{
				item.setForeground(0, theme.keyword);
			}
			else if(p.kind == Proposal.TYPE)
			{
				item.setForeground(0, theme.type);
			}
		}
		table.getColumn(0).pack();
		table.getColumn(1).pack();
		table.setSelection(0);
		table.setRedraw(true);
	}

	void place()
	{
		int caret = Math.max(wordStart, 0);
		Point location = text.getLocationAtOffset(caret);
		int lineHeight = text.getLineHeight(caret);
		int rowHeight = table.getItemHeight();
		int rows = Math.min(shown.size(), ROWS);
		Rectangle area = text.getClientArea();
		int width = Math.min(Math.max(table.getColumn(0).getWidth() + table.getColumn(1).getWidth() + 28, 280), Math.max(280, area.width - 8));
		int height = rows * rowHeight + 6;
		int x = Math.max(0, Math.min(location.x, area.width - width));
		int y = location.y + lineHeight;
		if(y + height > area.height && location.y - height >= 0)
		{
			y = location.y - height;
		}
		table.setBounds(x, y, width, height);
	}

	public void close()
	{
		if(table != null && !table.isDisposed())
		{
			table.setVisible(false);
		}
	}

	public void dispose()
	{
		if(table != null && !table.isDisposed())
		{
			table.dispose();
		}
	}

	// Called after every edit and caret move while the list is open.
	public void refresh()
	{
		if(!isOpen())
		{
			return;
		}
		if(!filter())
		{
			close();
			return;
		}
		fill();
		place();
	}

	void move(int delta)
	{
		int count = table.getItemCount();
		if(count == 0)
		{
			return;
		}
		int next = table.getSelectionIndex() + delta;
		if(delta == 1 || delta == -1)
		{
			next = (next + count) % count;
		}
		else
		{
			next = Math.max(0, Math.min(count - 1, next));
		}
		table.setSelection(next);
	}

	public Proposal selected()
	{
		int i = table == null || table.isDisposed() ? -1 : table.getSelectionIndex();
		return i < 0 || i >= shown.size() ? null : shown.get(i);
	}

	void acceptSelected()
	{
		Proposal p = selected();
		close();
		if(p != null)
		{
			acceptor.accept(p);
		}
	}

	// Returns true when the key was used by the list.
	public boolean handleKey(VerifyEvent e)
	{
		if(!isOpen())
		{
			return false;
		}
		boolean modified = (e.stateMask & (SWT.MOD1 | SWT.ALT)) != 0;
		if(e.keyCode == SWT.ARROW_DOWN && !modified)
		{
			e.doit = false;
			move(1);
			return true;
		}
		if(e.keyCode == SWT.ARROW_UP && !modified)
		{
			e.doit = false;
			move(-1);
			return true;
		}
		if(e.keyCode == SWT.PAGE_DOWN)
		{
			e.doit = false;
			move(ROWS);
			return true;
		}
		if(e.keyCode == SWT.PAGE_UP)
		{
			e.doit = false;
			move(-ROWS);
			return true;
		}
		if(e.keyCode == SWT.ESC)
		{
			e.doit = false;
			close();
			return true;
		}
		if(e.character == '\r' || e.character == '\n' || e.character == '\t' || e.keyCode == SWT.KEYPAD_CR)
		{
			e.doit = false;
			acceptSelected();
			return true;
		}
		if(e.keyCode == SWT.ARROW_LEFT || e.keyCode == SWT.ARROW_RIGHT || e.keyCode == SWT.HOME || e.keyCode == SWT.END)
		{
			close();
			return false;
		}
		if(e.character != 0 && !Character.isJavaIdentifierPart(e.character) && e.character != SWT.BS && !modified)
		{
			close();
		}
		return false;
	}
}
