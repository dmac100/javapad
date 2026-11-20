package javapad.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;

// Mouse wheel scrolling in whole lines, instead of the toolkit's pixel by pixel smooth scrolling.
class LineScroll
{
	static final int LINES = 3;

	static void install(StyledText text)
	{
		Listener wheel = e ->
		{
			if(e.widget != text || (e.stateMask & (SWT.SHIFT | SWT.MOD1)) != 0 || e.count == 0)
			{
				return;
			}
			e.doit = false;
			int top = text.getTopIndex() - Integer.signum(e.count) * LINES;
			int max = Math.max(0, text.getLineCount() - 1);
			text.setTopIndex(Math.max(0, Math.min(max, top)));
		};
		Display display = text.getDisplay();
		display.addFilter(SWT.MouseWheel, wheel);
		text.addListener(SWT.Dispose, e -> display.removeFilter(SWT.MouseWheel, wheel));
	}
}
