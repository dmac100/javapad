package javapad.ui.chat;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class Prompt {
	public static String ask(Shell parent, String title, String message, String initial) {
		String[] result = new String[1];
		Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		dialog.setText(title);
		dialog.setLayout(new GridLayout(2, true));
		Label label = new Label(dialog, SWT.NONE);
		label.setText(message);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		Text text = new Text(dialog, SWT.BORDER);
		GridData td = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		td.widthHint = 360;
		text.setLayoutData(td);
		text.setText(initial);
		text.selectAll();
		Button ok = new Button(dialog, SWT.PUSH);
		ok.setText("OK");
		ok.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
		Button cancel = new Button(dialog, SWT.PUSH);
		cancel.setText("Cancel");
		ok.addListener(SWT.Selection, e -> {
			result[0] = text.getText();
			dialog.dispose();
		});
		cancel.addListener(SWT.Selection, e -> dialog.dispose());
		dialog.setDefaultButton(ok);
		dialog.pack();
		Shell p = parent;
		dialog.setLocation(p.getLocation().x + 80, p.getLocation().y + 80);
		dialog.open();
		Display display = parent.getDisplay();
		while(!dialog.isDisposed()) {
			if(!display.readAndDispatch()) {
				display.sleep();
			}
		}
		return result[0];
	}
}
