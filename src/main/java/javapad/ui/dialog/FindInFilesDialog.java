package javapad.ui.dialog;

import java.util.regex.PatternSyntaxException;
import javapad.lang.FileSearch;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

// Asks what to search the project for.
public class FindInFilesDialog
{
	private final Shell parent;
	private Request result;

	public static class Request
	{
		public String query;
		public boolean caseSensitive;
		public boolean regex;
	}

	public FindInFilesDialog(Shell parent)
	{
		this.parent = parent;
	}

	// initial: the text to start with; null on cancel
	public Request open(String initial, boolean caseSensitive, boolean regex)
	{
		Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		dialog.setText("Find in Files");
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 8;
		layout.marginHeight = 8;
		dialog.setLayout(layout);
		new Label(dialog, SWT.NONE).setText("Search the project files for:");
		Text text = new Text(dialog, SWT.BORDER);
		GridData td = new GridData(SWT.FILL, SWT.CENTER, true, false);
		td.widthHint = 420;
		text.setLayoutData(td);
		text.setText(initial);
		text.selectAll();
		Button caseBox = new Button(dialog, SWT.CHECK);
		caseBox.setText("Case sensitive");
		caseBox.setSelection(caseSensitive);
		Button regexBox = new Button(dialog, SWT.CHECK);
		regexBox.setText("Regular expression");
		regexBox.setSelection(regex);
		Label error = new Label(dialog, SWT.NONE);
		error.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Composite buttons = new Composite(dialog, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
		GridLayout bl = new GridLayout(2, false);
		bl.marginWidth = 0;
		bl.marginHeight = 0;
		buttons.setLayout(bl);
		Button search = new Button(buttons, SWT.PUSH);
		search.setText("Search");
		Button cancel = new Button(buttons, SWT.PUSH);
		cancel.setText("Cancel");
		search.addListener(SWT.Selection, e ->
		{
			if(text.getText().isEmpty())
			{
				return;
			}
			try
			{
				FileSearch.pattern(text.getText(), caseBox.getSelection(), regexBox.getSelection());
			}
			catch(PatternSyntaxException ex)
			{
				error.setText("Not a valid regular expression: " + ex.getDescription());
				dialog.pack();
				return;
			}
			Request r = new Request();
			r.query = text.getText();
			r.caseSensitive = caseBox.getSelection();
			r.regex = regexBox.getSelection();
			result = r;
			dialog.dispose();
		});
		cancel.addListener(SWT.Selection, e -> dialog.dispose());
		dialog.setDefaultButton(search);
		dialog.pack();
		dialog.setLocation(parent.getLocation().x + 100, parent.getLocation().y + 80);
		dialog.open();
		text.setFocus();
		Display display = parent.getDisplay();
		while(!dialog.isDisposed())
		{
			if(!display.readAndDispatch())
			{
				display.sleep();
			}
		}
		return result;
	}
}
