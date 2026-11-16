package javapad.git;

// A file touched by a commit: the status letter (A, M, D, R) and its path (the new path for a rename).
public class ChangedFile
{
	public final char status;
	public final String path;
	public final String oldPath;

	public ChangedFile(char status, String path, String oldPath)
	{
		this.status = status;
		this.path = path;
		this.oldPath = oldPath;
	}

	public String label()
	{
		return FileChange.word(status);
	}
}
