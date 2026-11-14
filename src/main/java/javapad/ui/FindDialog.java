package javapad.ui;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class FindDialog
{
	private final Shell parent;
	private final Supplier<StyledText> editor;
	private Shell shell;
	private Text findText;
	private Text replaceText;
	private Button regexCheck;
	private Button caseCheck;
	private Button replaceButton;
	private Label status;
	private boolean foundMatch;
	private int matchStart;
	private int matchLength;
	private String matchText;
	private String matchReplacement;

	public FindDialog(Shell parent, Supplier<StyledText> editor)
	{
		this.parent = parent;
		this.editor = editor;
	}

	public void open()
	{
		StyledText text = editor.get();
		String selected = text == null ? "" : text.getSelectionText();
		if(shell != null && !shell.isDisposed())
		{
			if(!selected.isEmpty() && !selected.contains("\n"))
			{
				findText.setText(selected);
			}
			shell.forceActive();
			findText.selectAll();
			findText.setFocus();
			return;
		}
		shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setText("Find/Replace");
		shell.setLayout(new GridLayout());
		Composite form = new Composite(shell, SWT.NONE);
		form.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		form.setLayout(new GridLayout(2, false));
		findText = field(form, "Find:");
		replaceText = field(form, "Replace:");
		if(!selected.contains("\n"))
		{
			findText.setText(selected);
		}
		regexCheck = check("Regular Expression");
		caseCheck = check("Case Sensitive");
		status = new Label(shell, SWT.NONE);
		status.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		Composite buttons = new Composite(shell, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.NONE, false, false));
		GridLayout bl = new GridLayout(2, true);
		bl.marginHeight = 0;
		buttons.setLayout(bl);
		Button find = button(buttons, "Find");
		replaceButton = button(buttons, "Replace");
		Button replaceAll = button(buttons, "Replace All");
		Button close = button(buttons, "Close");
		replaceButton.setEnabled(false);
		shell.setDefaultButton(find);
		find.addListener(SWT.Selection, e -> find());
		replaceButton.addListener(SWT.Selection, e -> replace());
		replaceAll.addListener(SWT.Selection, e -> replaceAll());
		close.addListener(SWT.Selection, e -> shell.dispose());
		shell.pack();
		Rectangle b = parent.getBounds();
		Point size = shell.getSize();
		shell.setLocation(b.x + (b.width - size.x) / 2, b.y + (b.height - size.y) / 2);
		shell.open();
		findText.selectAll();
		findText.setFocus();
	}

	public void close()
	{
		if(shell != null && !shell.isDisposed())
		{
			shell.dispose();
		}
	}

	Text field(Composite form, String label)
	{
		new Label(form, SWT.NONE).setText(label);
		Text t = new Text(form, SWT.BORDER);
		GridData data = new GridData(SWT.FILL, SWT.NONE, true, false);
		data.minimumWidth = 240;
		t.setLayoutData(data);
		return t;
	}

	Button check(String label)
	{
		Button b = new Button(shell, SWT.CHECK);
		b.setText(label);
		b.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		return b;
	}

	Button button(Composite parent, String label)
	{
		Button b = new Button(parent, SWT.PUSH);
		b.setText(label);
		b.setLayoutData(new GridData(SWT.FILL, SWT.NONE, false, false));
		return b;
	}

	Pattern pattern()
	{
		String source = regexCheck.getSelection() ? findText.getText() : Pattern.quote(findText.getText());
		int flags = caseCheck.getSelection() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		try
		{
			status.setText("");
			return Pattern.compile(source, flags);
		}
		catch(PatternSyntaxException e)
		{
			status.setText("Invalid regular expression: " + e.getDescription());
			return null;
		}
	}

	String replacementText()
	{
		return regexCheck.getSelection() ? replaceText.getText() : Matcher.quoteReplacement(replaceText.getText());
	}

	void find()
	{
		foundMatch = false;
		replaceButton.setEnabled(false);
		StyledText text = editor.get();
		Pattern pattern = findText.getText().isEmpty() ? null : pattern();
		if(text == null || pattern == null)
		{
			return;
		}
		String content = text.getText();
		Point sel = text.getSelection();
		int from = sel.y > sel.x ? sel.y : sel.x;
		Matcher m = pattern.matcher(content);
		boolean found = from <= content.length() && m.find(from);
		if(found && m.end() == m.start() && m.start() == sel.x && sel.x == sel.y)
		{
			found = m.start() + 1 <= content.length() && m.find(m.start() + 1);
		}
		boolean wrapped = false;
		if(!found)
		{
			m = pattern.matcher(content);
			found = m.find(0);
			wrapped = found;
		}
		if(!found)
		{
			status.setText("Not found");
			return;
		}
		matchStart = m.start();
		matchLength = m.end() - m.start();
		matchText = content.substring(matchStart, m.end());
		StringBuilder sb = new StringBuilder();
		try
		{
			m.appendReplacement(sb, replacementText());
			matchReplacement = sb.substring(matchStart);
		}
		catch(RuntimeException e)
		{
			status.setText("Invalid replacement: " + e.getMessage());
			return;
		}
		foundMatch = true;
		status.setText(wrapped ? "Wrapped to the start" : "");
		text.setSelectionRange(matchStart, matchLength);
		text.showSelection();
		replaceButton.setEnabled(true);
	}

	void replace()
	{
		StyledText text = editor.get();
		if(!foundMatch || text == null || matchStart + matchLength > text.getCharCount())
		{
			return;
		}
		if(text.getTextRange(matchStart, matchLength).equals(matchText))
		{
			text.replaceTextRange(matchStart, matchLength, matchReplacement);
			text.setCaretOffset(matchStart + matchReplacement.length());
			find();
		}
	}

	void replaceAll()
	{
		StyledText text = editor.get();
		Pattern pattern = findText.getText().isEmpty() ? null : pattern();
		if(text == null || pattern == null)
		{
			return;
		}
		String content = text.getText();
		Matcher m = pattern.matcher(content);
		int count = 0;
		while(m.find())
		{
			count++;
		}
		try
		{
			String result = pattern.matcher(content).replaceAll(replacementText());
			if(count > 0)
			{
				text.replaceTextRange(0, content.length(), result);
			}
		}
		catch(RuntimeException e)
		{
			status.setText("Invalid replacement: " + e.getMessage());
			return;
		}
		status.setText(count + " replaced");
		foundMatch = false;
		replaceButton.setEnabled(false);
	}
}
