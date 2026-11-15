package javapad.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

// Toolbar icons, drawn in code so they need no resource files and look right on light and dark themes
public class Icons
{
	public static final int FOLDER = 0;
	public static final int SAVE = 1;
	public static final int BUILD = 2;
	public static final int RUN = 3;
	public static final int TESTS = 4;
	public static final int STOP = 5;

	static final int SIZE = 20;
	static final RGB KEY = new RGB(255, 0, 255);

	public static Image create(Display display, int icon)
	{
		Image image = new Image(display, SIZE, SIZE);
		GC gc = new GC(image);
		gc.setAntialias(SWT.OFF);
		Color key = new Color(display, KEY);
		gc.setBackground(key);
		key.dispose();
		gc.fillRectangle(0, 0, SIZE, SIZE);
		draw(display, gc, icon);
		gc.dispose();
		ImageData data = image.getImageData();
		data.transparentPixel = data.palette.getPixel(KEY);
		image.dispose();
		return new Image(display, data);
	}

	static void fill(Display d, GC gc, int r, int g, int b)
	{
		Color c = new Color(d, r, g, b);
		gc.setBackground(c);
		gc.setForeground(c);
		c.dispose();
	}

	static void draw(Display d, GC gc, int icon)
	{
		switch(icon)
		{
			case FOLDER:
				fill(d, gc, 220, 170, 50);
				gc.fillRectangle(2, 5, 7, 3);
				gc.fillRectangle(2, 7, 16, 10);
				fill(d, gc, 245, 205, 100);
				gc.fillRectangle(2, 9, 16, 8);
				break;
			case SAVE:
				fill(d, gc, 60, 100, 170);
				gc.fillRectangle(3, 3, 14, 14);
				fill(d, gc, 235, 235, 235);
				gc.fillRectangle(6, 3, 8, 5);
				gc.fillRectangle(6, 11, 8, 6);
				fill(d, gc, 60, 100, 170);
				gc.fillRectangle(11, 4, 2, 3);
				break;
			case BUILD:
				fill(d, gc, 120, 130, 150);
				for(int i = 0; i < 8; i++)
				{
					double angle = Math.PI * i / 4;
					double dx = Math.cos(angle);
					double dy = Math.sin(angle);
					double px = -dy;
					double py = dx;
					gc.fillPolygon(new int[] {
						(int)Math.round(10 + dx * 6 + px * 2), (int)Math.round(10 + dy * 6 + py * 2),
						(int)Math.round(10 + dx * 9 + px * 1.5), (int)Math.round(10 + dy * 9 + py * 1.5),
						(int)Math.round(10 + dx * 9 - px * 1.5), (int)Math.round(10 + dy * 9 - py * 1.5),
						(int)Math.round(10 + dx * 6 - px * 2), (int)Math.round(10 + dy * 6 - py * 2)});
				}
				gc.fillOval(4, 4, 12, 12);
				gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_MAGENTA));
				gc.fillOval(7, 7, 6, 6);
				break;
			case RUN:
				fill(d, gc, 40, 170, 70);
				gc.fillPolygon(new int[] {5, 3, 17, 10, 5, 17});
				break;
			case TESTS:
				fill(d, gc, 40, 170, 70);
				gc.fillPolygon(new int[] {3, 10, 6, 7, 8, 10, 15, 3, 18, 6, 8, 16});
				break;
			default:
				fill(d, gc, 210, 50, 50);
				gc.fillRectangle(4, 4, 12, 12);
				break;
		}
	}
}
