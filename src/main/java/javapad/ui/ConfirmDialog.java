package javapad.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class ConfirmDialog
{
	public static int ask(Shell parent, String title, String message)
	{
		return show(parent, title, message, SWT.ICON_QUESTION, SWT.YES | SWT.NO | SWT.CANCEL, null);
	}

	public static boolean confirm(Shell parent, String title, String message)
	{
		return show(parent, title, message, SWT.ICON_WARNING, SWT.YES | SWT.NO, null) == SWT.YES;
	}

	public static void message(Shell parent, String title, String message, int icon)
	{
		show(parent, title, message, icon, SWT.OK, null);
	}

	static int show(Shell parent, String title, String message, int icon, int buttonFlags, Runnable shown)
	{
		int closed = (buttonFlags & SWT.CANCEL) != 0 ? SWT.CANCEL : (buttonFlags & SWT.NO) != 0 ? SWT.NO : SWT.OK;
		int[] result = {closed};
		Display display = parent.getDisplay();
		Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		dialog.setText(title);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 16;
		layout.marginHeight = 14;
		layout.horizontalSpacing = 16;
		layout.verticalSpacing = 16;
		dialog.setLayout(layout);
		Label iconLabel = new Label(dialog, SWT.NONE);
		iconLabel.setImage(display.getSystemImage(icon));
		iconLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		Label text = new Label(dialog, SWT.WRAP);
		text.setText(message);
		GridData textData = new GridData(SWT.FILL, SWT.CENTER, true, true);
		textData.widthHint = 280;
		text.setLayoutData(textData);
		Composite buttons = new Composite(dialog, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
		int[] flags = {SWT.YES, SWT.NO, SWT.CANCEL, SWT.OK};
		String[] labels = {"Yes", "No", "Cancel", "OK"};
		int count = 0;
		for(int flag : flags)
		{
			count += (buttonFlags & flag) != 0 ? 1 : 0;
		}
		GridLayout bl = new GridLayout(count, true);
		bl.marginWidth = 0;
		bl.marginHeight = 0;
		buttons.setLayout(bl);
		Button first = null;
		for(int i = 0; i < flags.length; i++)
		{
			if((buttonFlags & flags[i]) == 0)
			{
				continue;
			}
			int flag = flags[i];
			Button b = button(buttons, labels[i]);
			b.addListener(SWT.Selection, e ->
			{
				result[0] = flag;
				dialog.dispose();
			});
			if(first == null)
			{
				first = b;
			}
		}
		dialog.setDefaultButton(first);
		dialog.pack();
		Rectangle b = parent.getBounds();
		Point size = dialog.getSize();
		dialog.setLocation(b.x + (b.width - size.x) / 2, b.y + (b.height - size.y) / 2);
		dialog.open();
		first.setFocus();
		if(shown != null)
		{
			display.timerExec(400, shown);
		}
		while(!dialog.isDisposed())
		{
			if(!display.readAndDispatch())
			{
				display.sleep();
			}
		}
		return result[0];
	}

	static Button button(Composite parent, String label)
	{
		Button b = new Button(parent, SWT.PUSH);
		b.setText(label);
		b.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		return b;
	}
}
