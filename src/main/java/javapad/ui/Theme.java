package javapad.ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;

public class Theme
{
	static final File PREFERENCE = new File(System.getProperty("user.home"), ".javapad/theme");

	public final Font font;
	public boolean dark;
	public Color background;
	public Color foreground;
	public Color sidebar;
	public Color sidebarText;
	public Color chrome;
	public Color keyword;
	public Color type;
	public Color string;
	public Color number;
	public Color comment;
	public Color doc;
	public Color annotation;
	public Color gutter;
	public Color error;
	public Color errorText;
	public Color pass;
	public Color bracket;
	private final Display display;

	public Theme(Display d)
	{
		display = d;
		font = new Font(d, new FontData("Monospace", 11, SWT.NORMAL));
		use(loadPreference());
	}

	public static boolean loadPreference()
	{
		String forced = System.getProperty("javapad.theme");
		if(forced != null)
		{
			return !forced.equals("light");
		}
		try
		{
			return !new String(Files.readAllBytes(PREFERENCE.toPath()), StandardCharsets.UTF_8).trim().equals("light");
		}
		catch(Exception e)
		{
			return true;
		}
	}

	public void savePreference()
	{
		try
		{
			PREFERENCE.getParentFile().mkdirs();
			Files.write(PREFERENCE.toPath(), (dark ? "dark" : "light").getBytes(StandardCharsets.UTF_8));
		}
		catch(Exception e)
		{
		}
	}

	public void use(boolean dark)
	{
		this.dark = dark;
		if(dark)
		{
			// Monokai, with the colours ScratchPad uses
			background = color(30, 30, 30);
			foreground = color(0xff, 0xff, 0xff);
			// the side panels keep the earlier dark grey
			sidebar = color(30, 30, 30);
			sidebarText = color(212, 212, 212);
			chrome = color(30, 30, 30);
			keyword = color(0xf9, 0x26, 0x72);
			type = color(0x66, 0xd9, 0xef);
			string = color(0xe6, 0xdb, 0x74);
			number = color(0xae, 0x81, 0xff);
			comment = color(0x75, 0x71, 0x5e);
			doc = color(0x75, 0x71, 0x5e);
			annotation = color(0x8a, 0xa6, 0xc1);
			gutter = color(0x6b, 0x7f, 0x8f);
			error = color(244, 71, 71);
			errorText = color(255, 110, 110);
			pass = color(106, 190, 100);
			bracket = color(0x25, 0x3e, 0x5a);
		}
		else
		{
			background = color(255, 255, 255);
			foreground = color(30, 30, 30);
			sidebar = background;
			sidebarText = foreground;
			chrome = color(240, 240, 240);
			keyword = color(127, 0, 85);
			type = color(0, 90, 130);
			string = color(42, 0, 255);
			number = color(20, 100, 20);
			comment = color(100, 130, 100);
			doc = color(60, 110, 160);
			annotation = color(100, 100, 100);
			gutter = color(140, 140, 140);
			error = color(220, 30, 30);
			errorText = color(200, 0, 0);
			pass = color(0, 130, 0);
			bracket = color(190, 215, 255);
		}
	}

	Color color(int r, int g, int b)
	{
		return new Color(display, r, g, b);
	}

	public Color colorFor(int style)
	{
		switch(style)
		{
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
