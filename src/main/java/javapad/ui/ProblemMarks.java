package javapad.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;

// The error and warning signs drawn in the editor's margin and the sidebar trees.
class ProblemMarks
{
	// A filled square with a white cross, its top left corner at x, y. The square is made an odd number of pixels across
	// and the cross is set pixel by pixel, so that it sits exactly in the middle.
	static void drawError(GC gc, Theme theme, int x, int y, int size)
	{
		int s = size % 2 == 0 ? size - 1 : size;
		int aa = gc.getAntialias();
		gc.setAntialias(SWT.OFF);
		gc.setBackground(theme.errorMark);
		gc.fillRectangle(x, y, s, s);
		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
		int in = (s + 1) / 4;
		int n = s - 2 * in;
		for(int i = 0; i < n; i++)
		{
			gc.fillRectangle(x + in + i, y + in + i, 1, 1);
			gc.fillRectangle(x + in + n - 1 - i, y + in + i, 1, 1);
		}
		gc.setAntialias(aa);
	}

	// a filled circle with a white exclamation mark: the file has a merge conflict
	static void drawConflict(GC gc, Theme theme, int x, int y, int size)
	{
		int aa = gc.getAntialias();
		gc.setAntialias(SWT.ON);
		gc.setBackground(theme.warning);
		gc.fillOval(x, y, size, size);
		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
		int w = Math.max(2, size / 7);
		int cx = x + size / 2 - w / 2;
		gc.fillRectangle(cx, y + size * 2 / 10, w, size * 4 / 10);
		gc.fillRectangle(cx, y + size * 7 / 10, w, w);
		gc.setAntialias(aa);
	}

	// a filled triangle with a white exclamation mark
	static void drawWarning(GC gc, Theme theme, int x, int y, int size)
	{
		int aa = gc.getAntialias();
		gc.setAntialias(SWT.ON);
		gc.setBackground(theme.warning);
		gc.fillPolygon(new int[] {x + size / 2, y, x + size, y + size - 1, x, y + size - 1});
		Color white = gc.getDevice().getSystemColor(SWT.COLOR_WHITE);
		gc.setBackground(white);
		int w = Math.max(2, size / 7);
		int cx = x + size / 2 - w / 2;
		gc.fillRectangle(cx, y + size * 3 / 10, w, size * 4 / 10);
		gc.fillRectangle(cx, y + size * 8 / 10 - 1, w, w);
		gc.setAntialias(aa);
	}
}
