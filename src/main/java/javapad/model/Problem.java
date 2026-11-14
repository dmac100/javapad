package javapad.model;

public class Problem
{
	public final String file;
	public final int line;
	public final int column;
	public final boolean error;
	public final String message;

	public Problem(String file, int line, int column, boolean error, String message)
	{
		this.file = file;
		this.line = line;
		this.column = column;
		this.error = error;
		this.message = message;
	}

	public String toString()
	{
		return file + ":" + line + " " + message;
	}
}
