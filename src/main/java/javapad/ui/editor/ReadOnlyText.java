package javapad.ui.editor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;

public class ReadOnlyText
{
	public static void installKeys(StyledText text)
	{
		LineScroll.install(text);
		text.addListener(SWT.KeyDown, e ->
		{
			if((e.stateMask & SWT.MOD1) == 0 || (e.stateMask & (SWT.SHIFT | SWT.ALT)) != 0)
			{
				return;
			}
			if(e.keyCode == 'a')
			{
				text.selectAll();
				e.doit = false;
			}
			else if(e.keyCode == 'c')
			{
				text.copy();
				e.doit = false;
			}
		});
	}
}
