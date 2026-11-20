package javapad.git;

// One changed path from "git status": the index column and the working tree column use git's letters (M, A, D, R, ?, U or space).
public class FileChange
{
	public final String path;
	public final char index;
	public final char work;
	// for a rename, the path the file had before; otherwise null
	public final String oldPath;

	public FileChange(String path, char index, char work)
	{
		this(path, index, work, null);
	}

	public FileChange(String path, char index, char work, String oldPath)
	{
		this.path = path;
		this.index = index;
		this.work = work;
		this.oldPath = oldPath;
	}

	public boolean untracked()
	{
		return index == '?';
	}

	public boolean conflicted()
	{
		return index == 'U' || work == 'U' || (index == 'A' && work == 'A') || (index == 'D' && work == 'D');
	}

	public boolean staged()
	{
		return !untracked() && !conflicted() && index != ' ';
	}

	public String label()
	{
		if(untracked())
		{
			return "untracked";
		}
		if(conflicted())
		{
			return "conflict";
		}
		StringBuilder sb = new StringBuilder();
		if(staged())
		{
			sb.append("staged ").append(word(index));
		}
		if(work != ' ')
		{
			if(sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(word(work));
		}
		return sb.toString();
	}

	public static String word(char c)
	{
		switch(c)
		{
			case 'M':
				return "modified";
			case 'A':
				return "added";
			case 'D':
				return "deleted";
			case 'R':
				return "renamed";
			case 'C':
				return "copied";
			case 'T':
				return "type changed";
			default:
				return String.valueOf(c);
		}
	}
}
