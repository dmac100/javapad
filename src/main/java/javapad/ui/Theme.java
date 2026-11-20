package javapad.ui;

import javapad.ui.editor.JavaLexer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;

public class Theme {
	static final File PREFERENCE = new File(System.getProperty("user.home"), ".javapad/theme");
	static final File FONT_PREFERENCE = new File(System.getProperty("user.home"), ".javapad/font");
	static final File TOOLBAR_PREFERENCE = new File(System.getProperty("user.home"), ".javapad/toolbar");

	static final File WHITESPACE_PREFERENCE = new File(System.getProperty("user.home"), ".javapad/whitespace");

	public Font font;
	public boolean dark;
	// whether the editors draw spaces, tabs and line ends
	public boolean showWhitespace = loadWhitespace();
	public Color background;
	public Color foreground;
	public Color sidebar;
	public Color sidebarText;
	public Color chrome;
	public Color divider;
	public Color keyword;
	public Color type;
	public Color string;
	public Color number;
	public Color comment;
	public Color doc;
	public Color annotation;
	public Color gutter;
	public Color error;
	// the error signs in the margins and trees, a little darker than error
	public Color errorMark;
	public Color warning;
	public Color errorText;
	public Color pass;
	public Color bracket;
	public Color occurrence;
	public Color diffAdded;
	public Color diffRemoved;
	public Color diffHunk;
	public Color branch;
	// modified files in the sidebar and modified lines in the editor margin
	public Color modified;
	private final Display display;

	public Theme(Display d) {
		display = d;
		font = loadFont();
		use(loadPreference());
	}

	Font loadFont() {
		String name = "Monospace";
		int size = 11;
		try {
			String[] f = new String(Files.readAllBytes(FONT_PREFERENCE.toPath()), StandardCharsets.UTF_8).trim().split("\t");
			name = f[0];
			size = Integer.parseInt(f[1]);
		} catch(Exception e) {
			// the font preference is optional
		}
		return new Font(display, new FontData(name, size, SWT.NORMAL));
	}

	// Replaces the editor font; the caller restyles the widgets and then calls disposeOld.
	public Font changeFont(FontData data) {
		Font old = font;
		FontData d = new FontData(data.getName(), Math.max(6, Math.round(data.height)), SWT.NORMAL);
		font = new Font(display, d);
		try {
			FONT_PREFERENCE.getParentFile().mkdirs();
			Files.write(FONT_PREFERENCE.toPath(), (d.getName() + "\t" + d.getHeight()).getBytes(StandardCharsets.UTF_8));
		} catch(Exception e) {
			// the font preference is optional
		}
		return old;
	}

	public static void saveWhitespace(boolean shown) {
		try {
			WHITESPACE_PREFERENCE.getParentFile().mkdirs();
			Files.write(WHITESPACE_PREFERENCE.toPath(), (shown ? "shown" : "hidden").getBytes(StandardCharsets.UTF_8));
		} catch(Exception e) {
			// the whitespace preference is optional
		}
	}

	public static boolean loadWhitespace() {
		try {
			return new String(Files.readAllBytes(WHITESPACE_PREFERENCE.toPath()), StandardCharsets.UTF_8).trim().equals("shown");
		} catch(Exception e) {
			return false;
		}
	}

	public static boolean loadToolbar() {
		try {
			return !new String(Files.readAllBytes(TOOLBAR_PREFERENCE.toPath()), StandardCharsets.UTF_8).trim().equals("hidden");
		} catch(Exception e) {
			return true;
		}
	}

	public static void saveToolbar(boolean shown) {
		try {
			TOOLBAR_PREFERENCE.getParentFile().mkdirs();
			Files.write(TOOLBAR_PREFERENCE.toPath(), (shown ? "shown" : "hidden").getBytes(StandardCharsets.UTF_8));
		} catch(Exception e) {
			// the toolbar preference is optional
		}
	}

	public static boolean loadPreference() {
		String forced = System.getProperty("javapad.theme");
		if(forced != null) {
			return !forced.equals("light");
		}
		try {
			return !new String(Files.readAllBytes(PREFERENCE.toPath()), StandardCharsets.UTF_8).trim().equals("light");
		} catch(Exception e) {
			return true;
		}
	}

	public void savePreference() {
		try {
			PREFERENCE.getParentFile().mkdirs();
			Files.write(PREFERENCE.toPath(), (dark ? "dark" : "light").getBytes(StandardCharsets.UTF_8));
		} catch(Exception e) {
			// the theme preference is optional
		}
	}

	public void use(boolean dark) {
		this.dark = dark;
		if(dark) {
			// Monokai, with the colours ScratchPad uses
			background = color(30, 30, 30);
			foreground = color(0xff, 0xff, 0xff);
			// the side panels keep the earlier dark grey
			sidebar = color(30, 30, 30);
			sidebarText = color(212, 212, 212);
			chrome = color(30, 30, 30);
			divider = color(100, 100, 100);
			keyword = color(0xf9, 0x26, 0x72);
			type = color(0x66, 0xd9, 0xef);
			string = color(0xe6, 0xdb, 0x74);
			number = color(0xae, 0x81, 0xff);
			comment = color(0x75, 0x71, 0x5e);
			doc = color(0x75, 0x71, 0x5e);
			annotation = color(0x8a, 0xa6, 0xc1);
			gutter = color(0x6b, 0x7f, 0x8f);
			error = color(244, 71, 71);
			errorMark = color(205, 45, 45);
			warning = color(255, 165, 40);
			errorText = color(255, 110, 110);
			pass = color(106, 190, 100);
			bracket = color(0x25, 0x3e, 0x5a);
			occurrence = color(84, 80, 44);
			diffAdded = color(36, 70, 36);
			diffRemoved = color(84, 36, 36);
			diffHunk = color(86, 156, 214);
			branch = color(120, 170, 220);
			modified = color(110, 200, 100);
		} else {
			background = color(255, 255, 255);
			foreground = color(30, 30, 30);
			sidebar = background;
			sidebarText = foreground;
			chrome = color(240, 240, 240);
			divider = color(160, 160, 160);
			keyword = color(127, 0, 85);
			type = color(0, 90, 130);
			string = color(42, 0, 255);
			number = color(20, 100, 20);
			comment = color(100, 130, 100);
			doc = color(60, 110, 160);
			annotation = color(100, 100, 100);
			gutter = color(140, 140, 140);
			error = color(220, 30, 30);
			errorMark = color(185, 20, 20);
			warning = color(230, 130, 0);
			errorText = color(200, 0, 0);
			pass = color(0, 130, 0);
			bracket = color(190, 215, 255);
			occurrence = color(255, 240, 170);
			diffAdded = color(214, 245, 214);
			diffRemoved = color(255, 218, 218);
			diffHunk = color(0, 90, 160);
			branch = color(30, 100, 170);
			modified = color(30, 140, 50);
		}
	}

	Color color(int r, int g, int b) {
		return new Color(display, r, g, b);
	}

	public Color colorFor(int style) {
		switch(style) {
		case JavaLexer.KEYWORD:
			return keyword;
		case JavaLexer.TYPE:
			return type;
		case JavaLexer.STRING:
			return string;
		case JavaLexer.NUMBER:
			return number;
		case JavaLexer.COMMENT:
			return comment;
		case JavaLexer.DOC:
			return doc;
		case JavaLexer.ANNOTATION:
			return annotation;
		default:
			return foreground;
		}
	}
}
