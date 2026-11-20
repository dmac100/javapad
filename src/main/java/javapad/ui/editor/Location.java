package javapad.ui.editor;

import java.io.File;

public class Location {
	public final File file;
	public final int line;
	public final int column;

	public Location(File file, int line, int column) {
		this.file = file;
		this.line = line;
		this.column = column;
	}
}
