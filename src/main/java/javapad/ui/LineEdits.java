package javapad.ui;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;

// Line-oriented editing commands on a text widget: smart Home, delete/move/shift lines, and newline with indentation.
class LineEdits
{
	private final StyledText text;

	LineEdits(StyledText text)
	{
		this.text = text;
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
		int end = line + 1 < text.getLineCount() ? text.getOffsetAtLine(line + 1) : start + text.getLine(line).length();
		text.replaceTextRange(start, end - start, "");
	}

	// the line break after a line: whatever lies between its text and the next line, or the widget's default after the last line
	String delimiterOf(int line)
	{
		if(line + 1 >= text.getLineCount())
		{
			return line > 0 ? delimiterOf(line - 1) : text.getLineDelimiter();
		}
		int end = text.getOffsetAtLine(line) + text.getLine(line).length();
		return text.getText(end, text.getOffsetAtLine(line + 1) - 1);
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
		String delimiter = delimiterOf(first);
		StringBuilder block = new StringBuilder();
		for(int l = first; l <= last; l++)
		{
			if(l > first)
			{
				block.append(delimiter);
			}
			block.append(text.getLine(l));
		}
		String other = text.getLine(up ? first - 1 : last + 1);
		String result = up ? block + delimiter + other : other + delimiter + block;
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
		String insert = delimiterOf(line) + indent;
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
		int end = text.getOffsetAtLine(last) + text.getLine(last).length();
		StringBuilder result = new StringBuilder();
		int firstDelta = 0;
		int total = 0;
		for(int l = first; l <= last; l++)
		{
			String line = text.getLine(l);
			String shifted = outdent ? Indenter.outdentLines(line) : Indenter.indentLines(line);
			result.append(shifted);
			if(l < last)
			{
				result.append(delimiterOf(l));
			}
			if(l == first)
			{
				firstDelta = shifted.length() - line.length();
			}
			total += shifted.length() - line.length();
		}
		text.replaceTextRange(start, end - start, result.toString());
		int x = Math.max(start, sel.x + firstDelta);
		int y = Math.max(x, sel.y + total);
		text.setSelection(x, y);
	}
}
