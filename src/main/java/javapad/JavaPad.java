package javapad;

import java.io.File;

import org.eclipse.swt.widgets.Display;

import javapad.ui.MainWindow;

public class JavaPad
{
	public static void main(String[] args)
	{
		Display display = new Display();
		MainWindow window = new MainWindow(display);
		window.getShell().open();
		if(args.length > 0)
		{
			window.openProject(new File(args[0]));
		}
		display.asyncExec(() -> window.automate());
		while(!window.getShell().isDisposed())
		{
			if(!display.readAndDispatch())
			{
				display.sleep();
			}
		}
		display.dispose();
	}
}
