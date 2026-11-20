package javapad.git;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// The parsed result of "git status --porcelain=v2 -z --branch".
public class GitStatus
{
	public final String branch;
	public final int ahead;
	public final int behind;
	public final List<FileChange> changes;
	private final Set<String> untracked = new HashSet<String>();
	private final Set<String> modified = new HashSet<String>();

	public GitStatus(String branch, List<FileChange> changes)
	{
		this(branch, changes, 0, 0);
	}

	public GitStatus(String branch, List<FileChange> changes, int ahead, int behind)
	{
		this.branch = branch;
		this.ahead = ahead;
		this.behind = behind;
		this.changes = changes;
		for(FileChange c : changes)
		{
			if(c.untracked())
			{
				untracked.add(c.path);
			}
			else if(!c.conflicted())
			{
				modified.add(c.path);
				modified.add("");
				for(int slash = c.path.lastIndexOf('/'); slash > 0; slash = c.path.lastIndexOf('/', slash - 1))
				{
					modified.add(c.path.substring(0, slash));
				}
			}
		}
	}

	public FileChange find(String path)
	{
		for(FileChange c : changes)
		{
			if(c.path.equals(path))
			{
				return c;
			}
		}
		return null;
	}

	// Whether the file (or folder), given relative to the repository root, is untracked. A folder that git lists as "dir/" counts, and so does everything in it.
	public boolean untracked(String path)
	{
		String p = path;
		while(true)
		{
			if(untracked.contains(p) || untracked.contains(p + "/"))
			{
				return true;
			}
			int slash = p.lastIndexOf('/');
			if(slash < 0)
			{
				return false;
			}
			p = p.substring(0, slash);
		}
	}

	// Whether the file, or anything in the folder, given relative to the repository root, has a merge conflict.
	public boolean conflicted(String path)
	{
		for(FileChange c : changes)
		{
			if(c.conflicted() && (path.isEmpty() || c.path.equals(path) || c.path.startsWith(path + "/")))
			{
				return true;
			}
		}
		return false;
	}

	// Whether the tracked file, or a folder holding such a file, given relative to the repository root, has changes in the index or the working tree (conflicts are not counted).
	public boolean modified(String path)
	{
		return modified.contains(path);
	}

	public boolean hasConflicts()
	{
		for(FileChange c : changes)
		{
			if(c.conflicted())
			{
				return true;
			}
		}
		return false;
	}

	public static GitStatus parse(String text)
	{
		String branch = "";
		int ahead = 0;
		int behind = 0;
		List<FileChange> list = new ArrayList<FileChange>();
		String[] parts = text.split("\0");
		for(int i = 0; i < parts.length; i++)
		{
			String p = parts[i];
			if(p.startsWith("# branch.head "))
			{
				branch = p.substring("# branch.head ".length());
			}
			else if(p.startsWith("# branch.ab "))
			{
				// "# branch.ab +2 -1"
				String[] f = p.split(" ");
				ahead = Integer.parseInt(f[2].substring(1));
				behind = Integer.parseInt(f[3].substring(1));
			}
			else if(p.startsWith("1 "))
			{
				String[] f = p.split(" ", 9);
				list.add(new FileChange(f[8], column(f[1].charAt(0)), column(f[1].charAt(1))));
			}
			else if(p.startsWith("2 "))
			{
				String[] f = p.split(" ", 10);
				list.add(new FileChange(f[9], column(f[1].charAt(0)), column(f[1].charAt(1))));
				// the original path follows as its own entry
				i++;
			}
			else if(p.startsWith("u "))
			{
				String[] f = p.split(" ", 11);
				list.add(new FileChange(f[10], 'U', 'U'));
			}
			else if(p.startsWith("? "))
			{
				list.add(new FileChange(p.substring(2), '?', '?'));
			}
		}
		return new GitStatus(branch, list, ahead, behind);
	}

	static char column(char c)
	{
		return c == '.' ? ' ' : c;
	}
}
