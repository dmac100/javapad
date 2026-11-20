package javapad.ui.git;

import javapad.ui.Theme;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;

// Shows a unified diff with added and removed lines coloured.
public class DiffView
{
	// Replaces the text of the widget and colours the diff lines from line "from" on (0-based).
	static void show(StyledText text, String content, Theme theme, int from)
	{
		text.setText(content);
		text.setForeground(theme.foreground);
		text.setBackground(theme.background);
		for(int line = from; line < text.getLineCount(); line++)
		{
			String s = text.getLine(line);
			StyleRange r = new StyleRange();
			r.start = text.getOffsetAtLine(line);
			r.length = s.length();
			if(s.startsWith("+++") || s.startsWith("---") || s.startsWith("diff ") || s.startsWith("index ") || s.startsWith("new file") || s.startsWith("deleted file")
				|| s.startsWith("rename ") || s.startsWith("similarity "))
			{
				r.foreground = theme.gutter;
				r.fontStyle = SWT.BOLD;
			}
			else if(s.startsWith("@@"))
			{
				r.foreground = theme.diffHunk;
			}
			else if(s.startsWith("+"))
			{
				r.background = theme.diffAdded;
				r.foreground = theme.foreground;
			}
			else if(s.startsWith("-"))
			{
				r.background = theme.diffRemoved;
				r.foreground = theme.foreground;
			}
			else
			{
				continue;
			}
			text.setStyleRange(r);
			if(r.background != null && s.length() > 0)
			{
				text.setLineBackground(line, 1, r.background);
			}
		}
	}
}
