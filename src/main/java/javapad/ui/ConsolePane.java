package javapad.ui;

import java.io.File;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javapad.model.EclipseProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

public class ConsolePane
{
	static final Pattern TRACE = Pattern.compile("at ([\\w.$]+)\\.[\\w$<>]+\\((\\w+\\.java):(\\d+)\\)");

	private final Composite composite;
	private final StyledText text;
	private final Text input;
	private final java.util.List<int[]> segments = new java.util.ArrayList<int[]>();
	private final Theme theme;
	private EclipseProject project;
	private Consumer<Location> opener;
	private Consumer<String> inputSink;

	public ConsolePane(Composite parent, Theme theme)
	{
		this.theme = theme;
		composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.verticalSpacing = 0;
		composite.setLayout(layout);
		text = new StyledText(composite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
		text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		text.setFont(theme.font);
		input = new Text(composite, SWT.BORDER | SWT.SINGLE);
		input.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		input.setMessage("stdin: type a line and press Enter");
		input.addListener(SWT.DefaultSelection, e -> sendInput());
		text.addListener(SWT.MouseUp, e -> clicked(e.x, e.y));
	}

	public Composite getControl()
	{
		return composite;
	}

	public void setProject(EclipseProject project)
	{
		this.project = project;
	}

	public void setOpener(Consumer<Location> opener)
	{
		this.opener = opener;
	}

	public void setInputSink(Consumer<String> sink)
	{
		inputSink = sink;
	}

	void sendInput()
	{
		String line = input.getText();
		input.setText("");
		append(line + "\n", false);
		if(inputSink != null)
		{
			inputSink.accept(line + "\n");
		}
	}

	public void clear()
	{
		segments.clear();
		text.setText("");
	}

	public String getText()
	{
		return text.getText();
	}

	public void append(String s, boolean error)
	{
		int start = text.getCharCount();
		text.append(s);
		StyleRange range = new StyleRange();
		range.start = start;
		range.length = s.length();
		range.foreground = error ? theme.errorText : theme.foreground;
		text.setStyleRange(range);
		segments.add(new int[] {start, s.length(), error ? 1 : 0});
		linkify(start);
		text.setTopIndex(text.getLineCount() - 1);
	}

	public void restyle()
	{
		text.setBackground(theme.background);
		text.setForeground(theme.foreground);
		input.setBackground(theme.background);
		input.setForeground(theme.foreground);
		for(int[] seg : segments)
		{
			StyleRange range = new StyleRange();
			range.start = seg[0];
			range.length = seg[1];
			range.foreground = seg[2] == 1 ? theme.errorText : theme.foreground;
			text.setStyleRange(range);
		}
		linkify(0);
	}

	void linkify(int start)
	{
		if(text.getCharCount() == 0)
		{
			return;
		}
		int firstLine = text.getLineAtOffset(start);
		int from = text.getOffsetAtLine(firstLine);
		String chunk = text.getText(from, text.getCharCount() - 1);
		Matcher m = TRACE.matcher(chunk);
		while(m.find())
		{
			File file = resolve(m.group(1), m.group(2));
			if(file == null)
			{
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

	File resolve(String qualifiedClass, String fileName)
	{
		if(project == null)
		{
			return null;
		}
		return project.findSource(qualifiedClass, fileName);
	}

	void clicked(int x, int y)
	{
		if(opener == null)
		{
			return;
		}
		try
		{
			int offset = text.getOffsetAtPoint(new Point(x, y));
			StyleRange r = text.getStyleRangeAtOffset(offset);
			if(r != null && r.data instanceof Location)
			{
				opener.accept((Location)r.data);
			}
		}
		catch(IllegalArgumentException e)
		{
		}
	}
}
