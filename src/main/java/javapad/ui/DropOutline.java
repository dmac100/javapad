package javapad.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

// The frame, or the bar, that shows where a dragged tab will land: a small shell without decorations over the target.
class DropOutline
{
	static final int THICKNESS = 3;

	private final Shell shell;
	private Region region;

	DropOutline(Shell parent)
	{
		Display display = parent.getDisplay();
		shell = new Shell(parent, SWT.NO_TRIM | SWT.ON_TOP | SWT.TOOL | SWT.NO_FOCUS);
		shell.setBackground(display.getSystemColor(SWT.COLOR_LIST_SELECTION));
		shell.addListener(SWT.Dispose, e -> disposeRegion());
	}

	// bounds are in display coordinates; a frame is hollow
	void show(Rectangle bounds, boolean frame)
	{
		shell.setBounds(bounds);
		Region next = new Region(shell.getDisplay());
		if(frame && bounds.width > 2 * THICKNESS && bounds.height > 2 * THICKNESS)
		{
			next.add(0, 0, bounds.width, THICKNESS);
			next.add(0, bounds.height - THICKNESS, bounds.width, THICKNESS);
			next.add(0, 0, THICKNESS, bounds.height);
			next.add(bounds.width - THICKNESS, 0, THICKNESS, bounds.height);
		}
		else
		{
			next.add(0, 0, bounds.width, bounds.height);
		}
		shell.setRegion(next);
		disposeRegion();
		region = next;
		if(!shell.isVisible())
		{
			shell.setVisible(true);
		}
	}

	void hide()
	{
		if(!shell.isDisposed())
		{
			shell.setVisible(false);
		}
	}

	private void disposeRegion()
	{
		if(region != null)
		{
			region.dispose();
			region = null;
		}
	}
}
