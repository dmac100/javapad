package javapad.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

// Toolbar icons, drawn in code so they need no resource files and look right on light and dark themes
public class Icons {
	public static final int FOLDER = 0;
	public static final int SAVE = 1;
	public static final int BUILD = 2;
	public static final int RUN = 3;
	public static final int TESTS = 4;
	public static final int STOP = 5;
	public static final int RERUN_ALL = 6;
	public static final int RERUN_FAILED = 7;
	public static final int SIDEBAR = 8;
	public static final int COMMIT = 9;

	static final int SIZE = 20;
	static final RGB KEY = new RGB(255, 0, 255);

	public static Image create(Display display, int icon) {
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

	static void fill(Display d, GC gc, int r, int g, int b) {
		Color c = new Color(d, r, g, b);
		gc.setBackground(c);
		gc.setForeground(c);
		c.dispose();
	}

	// an arc of almost a full circle that ends in an arrow head: "again"
	static void circularArrow(GC gc) {
		gc.setLineWidth(3);
		gc.drawArc(3, 5, 12, 12, 80, 270);
		gc.setLineWidth(1);
		gc.fillPolygon(new int[] {11, 10, 19, 10, 15, 4});
	}

	static void draw(Display d, GC gc, int icon) {
		switch(icon) {
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
				// the teeth are drawn as pixel blocks, mirrored exactly about the centre, and overlap the body so that none float free
				gc.fillRectangle(8, 1, 4, 5);
				gc.fillRectangle(8, 14, 4, 5);
				gc.fillRectangle(1, 8, 5, 4);
				gc.fillRectangle(14, 8, 5, 4);
				// each diagonal tooth is an outer block and an inner block that overlap, and the inner one overlaps the body
				gc.fillRectangle(3, 3, 3, 3);
				gc.fillRectangle(5, 5, 3, 3);
				gc.fillRectangle(14, 3, 3, 3);
				gc.fillRectangle(12, 5, 3, 3);
				gc.fillRectangle(3, 14, 3, 3);
				gc.fillRectangle(5, 12, 3, 3);
				gc.fillRectangle(14, 14, 3, 3);
				gc.fillRectangle(12, 12, 3, 3);
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
			case RERUN_ALL:
				fill(d, gc, 40, 170, 70);
				circularArrow(gc);
				break;
			case RERUN_FAILED:
				fill(d, gc, 210, 50, 50);
				circularArrow(gc);
				break;
			case SIDEBAR:
				fill(d, gc, 120, 130, 150);
				gc.drawRectangle(2, 3, 15, 13);
				gc.fillRectangle(3, 4, 5, 12);
				gc.fillRectangle(10, 6, 5, 2);
				gc.fillRectangle(10, 10, 5, 2);
				break;
			case COMMIT:
				fill(d, gc, 230, 120, 40);
				gc.fillRectangle(9, 1, 2, 7);
				gc.fillRectangle(9, 13, 2, 6);
				gc.setLineWidth(3);
				gc.drawOval(6, 6, 8, 8);
				gc.setLineWidth(1);
				break;
			default:
				fill(d, gc, 210, 50, 50);
				gc.fillRectangle(4, 4, 12, 12);
				break;
		}
	}
}
