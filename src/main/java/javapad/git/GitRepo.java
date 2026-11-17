package javapad.git;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A git working tree, driven through the git command line. Paths are relative to the repository root, with forward slashes.
public class GitRepo
{
	static final String SEP = "\u001f";
	static final String END = "\u001e";
	static final String LOG_FORMAT = "--format=%H%x1f%P%x1f%an%x1f%ae%x1f%aI%x1f%cn%x1f%ce%x1f%cI%x1f%D%x1f%s%x1f%b%x1e";

	private final File root;
	// the root with symbolic links and . or .. segments resolved, for comparing paths
	private final Path base;

	public GitRepo(File root)
	{
		this.root = root;
		this.base = real(root.toPath());
	}

	static Path real(Path p)
	{
		try
		{
			return p.toRealPath();
		}
		catch(IOException e)
		{
			return p.toAbsolutePath().normalize();
		}
	}

	// The repository that contains dir, or null if there is none (or git is not installed).
	public static GitRepo find(File dir)
	{
		GitResult r = GitRunner.run(dir, "rev-parse", "--show-toplevel");
		if(!r.ok())
		{
			return null;
		}
		return new GitRepo(new File(r.out.trim()));
	}

	public static GitResult init(File dir)
	{
		return GitRunner.run(dir, "init");
	}

	public File root()
	{
		return root;
	}

	public File file(String path)
	{
		return new File(root, path);
	}

	// the path of a file relative to the repository root, "" for the root itself, null if it is outside
	public String relative(File f)
	{
		Path p = f.toPath().toAbsolutePath().normalize();
		if(!p.startsWith(base))
		{
			// the file may be reached through a symbolic link
			p = real(p);
		}
		if(!p.startsWith(base))
		{
			return null;
		}
		return base.relativize(p).toString().replace(File.separatorChar, '/');
	}

	GitResult git(String... args)
	{
		return GitRunner.run(root, args);
	}

	// ---- reading

	// allFiles: list every untracked file instead of just the untracked folder
	public GitStatus status(boolean allFiles)
	{
		GitResult r = git("status", "--porcelain=v2", "-z", "--branch", allFiles ? "--untracked-files=all" : "--untracked-files=normal");
		if(!r.ok())
		{
			return new GitStatus("", new ArrayList<FileChange>());
		}
		return GitStatus.parse(r.out);
	}

	public boolean hasCommits()
	{
		return git("rev-parse", "--verify", "-q", "HEAD").ok();
	}

	// Commits newest first. path limits it to one file; all includes every branch, tag and remote branch.
	public List<Commit> log(String path, int limit, boolean all)
	{
		List<String> args = new ArrayList<String>(Arrays.asList("log", "-n", String.valueOf(limit), "--decorate=full", "--topo-order", LOG_FORMAT));
		if(all)
		{
			args.add("--all");
		}
		if(path != null)
		{
			args.add("--follow");
			args.add("--");
			args.add(path);
		}
		GitResult r = git(args.toArray(new String[0]));
		return r.ok() ? parseLog(r.out) : new ArrayList<Commit>();
	}

	public static List<Commit> parseLog(String text)
	{
		List<Commit> list = new ArrayList<Commit>();
		for(String rec : text.split(END))
		{
			String r = rec.replaceFirst("^\n+", "");
			if(r.trim().isEmpty())
			{
				continue;
			}
			String[] f = r.split(SEP, -1);
			if(f.length < 11)
			{
				continue;
			}
			List<String> parents = new ArrayList<String>();
			for(String p : f[1].trim().split(" "))
			{
				if(!p.isEmpty())
				{
					parents.add(p);
				}
			}
			list.add(new Commit(f[0], parents, f[2], f[3], f[4], f[5], f[6], f[7], Commit.parseRefs(f[8]), f[9], f[10].trim()));
		}
		return list;
	}

	// The files a commit changed (against its first parent).
	public List<ChangedFile> changedFiles(String hash)
	{
		GitResult r = git("show", "--first-parent", "--name-status", "--format=", "-M", "-z", hash);
		return r.ok() ? parseNameStatus(r.out) : new ArrayList<ChangedFile>();
	}

	// "git show --name-status -z": status, NUL, path, NUL (renames and copies have two paths)
	public static List<ChangedFile> parseNameStatus(String text)
	{
		List<ChangedFile> list = new ArrayList<ChangedFile>();
		String[] f = text.split("\0");
		int i = 0;
		while(i < f.length)
		{
			String status = f[i].trim();
			if(status.isEmpty())
			{
				i++;
				continue;
			}
			char c = status.charAt(0);
			if((c == 'R' || c == 'C') && i + 2 < f.length)
			{
				list.add(new ChangedFile(c, f[i + 2], f[i + 1]));
				i += 3;
			}
			else if(i + 1 < f.length)
			{
				list.add(new ChangedFile(c, f[i + 1], null));
				i += 2;
			}
			else
			{
				break;
			}
		}
		return list;
	}

	// The unified diff of a commit against its first parent, for one file or (path null) all of it.
	public String commitDiff(String hash, String path)
	{
		List<String> args = new ArrayList<String>(Arrays.asList("show", "--first-parent", "--format=", "-M", "--no-color", hash));
		if(path != null)
		{
			args.add("--");
			args.add(path);
		}
		return text(git(args.toArray(new String[0])));
	}

	// The files that differ between a revision and the working tree (tracked files only).
	public List<ChangedFile> diffFiles(String rev)
	{
		GitResult r = git("diff", "--name-status", "-M", "-z", rev);
		return r.ok() ? parseNameStatus(r.out) : new ArrayList<ChangedFile>();
	}

	// The current contents of a file in the working tree, or "" if it does not exist.
	public String working(String path)
	{
		try
		{
			File f = file(path);
			return f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
		}
		catch(IOException e)
		{
			return "";
		}
	}

	// "git diff a b" (b null: against the working tree), optionally for one path
	public String diff(String a, String b, String path)
	{
		List<String> args = new ArrayList<String>(Arrays.asList("diff", "--no-color", a));
		if(b != null)
		{
			args.add(b);
		}
		if(path != null)
		{
			args.add("--");
			args.add(path);
		}
		return text(git(args.toArray(new String[0])));
	}

	static String text(GitResult r)
	{
		return r.ok() ? r.out : r.message();
	}

	// The contents of a file at a revision (HEAD, a hash, a branch), or null if it does not exist there.
	public String show(String rev, String path)
	{
		GitResult r = git("show", rev + ":" + path);
		return r.ok() ? r.out : null;
	}

	public List<String> branches()
	{
		return lines(git("branch", "--format=%(refname:short)"));
	}

	public List<String> remoteBranches()
	{
		List<String> list = new ArrayList<String>();
		for(String s : lines(git("branch", "-r", "--format=%(refname:short)")))
		{
			if(!s.endsWith("/HEAD"))
			{
				list.add(s);
			}
		}
		return list;
	}

	public boolean hasRemote(String name)
	{
		return lines(git("remote")).contains(name);
	}

	static List<String> lines(GitResult r)
	{
		List<String> list = new ArrayList<String>();
		if(r.ok())
		{
			for(String line : r.out.split("\n"))
			{
				if(!line.trim().isEmpty())
				{
					list.add(line.trim());
				}
			}
		}
		return list;
	}

	public String config(String key)
	{
		GitResult r = git("config", key);
		return r.ok() ? r.out.trim() : "";
	}

	// "Name <email>" from the git configuration
	public String identity()
	{
		String name = config("user.name");
		String email = config("user.email");
		return name.isEmpty() && email.isEmpty() ? "" : name + " <" + email + ">";
	}

	public String lastMessage()
	{
		GitResult r = git("log", "-1", "--format=%B");
		return r.ok() ? r.out.trim() : "";
	}

	public String lastAuthor()
	{
		GitResult r = git("log", "-1", "--format=%an <%ae>");
		return r.ok() ? r.out.trim() : "";
	}

	public boolean merging()
	{
		return git("rev-parse", "-q", "--verify", "MERGE_HEAD").ok();
	}

	// ---- the index and the working tree

	public GitResult add(List<String> paths)
	{
		return withPaths(Arrays.asList("add", "-A", "--"), paths);
	}

	public GitResult unstage(List<String> paths)
	{
		if(hasCommits())
		{
			return withPaths(Arrays.asList("reset", "-q", "--"), paths);
		}
		return withPaths(Arrays.asList("rm", "-r", "-q", "--cached", "--"), paths);
	}

	// Stops tracking the files but keeps them on disk.
	public GitResult untrack(List<String> paths)
	{
		return withPaths(Arrays.asList("rm", "-r", "-q", "--cached", "--"), paths);
	}

	// Adds the path to the .gitignore in the repository root.
	public GitResult ignore(String path, boolean directory)
	{
		File f = new File(root, ".gitignore");
		try
		{
			String existing = f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
			String line = "/" + path + (directory ? "/" : "") + "\n";
			String prefix = existing.isEmpty() || existing.endsWith("\n") ? "" : "\n";
			Files.write(f.toPath(), (prefix + line).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			// a file that is already tracked stays tracked until it is untracked
			return new GitResult(0, "", "");
		}
		catch(IOException e)
		{
			e.printStackTrace();
			return new GitResult(1, "", "Cannot write .gitignore: " + e.getMessage());
		}
	}

	// Replaces the files in the working tree (and index) with their state at a revision.
	public GitResult restore(String rev, List<String> paths)
	{
		return withPaths(Arrays.asList("checkout", rev, "--"), paths);
	}

	// Throws away the changes of a tracked file, or deletes an untracked one.
	public GitResult discard(FileChange c)
	{
		if(c.untracked())
		{
			return git("clean", "-f", "-q", "--", c.path);
		}
		return git("checkout", "HEAD", "--", c.path);
	}

	// Commits the given paths (untracked ones are added first). With no paths, commits what is staged. author and committer are "Name <email>", or empty for the configured ones.
	public GitResult commit(String message, String author, String committer, boolean amend, List<String> paths)
	{
		if(!paths.isEmpty())
		{
			GitResult added = add(paths);
			if(!added.ok())
			{
				return added;
			}
		}
		List<String> args = new ArrayList<String>(Arrays.asList("commit", "-m", message));
		if(amend)
		{
			args.add("--amend");
		}
		Map<String, String> env = new HashMap<String, String>();
		if(!author.trim().isEmpty())
		{
			args.add("--author=" + author.trim());
		}
		String[] c = split(committer);
		if(c != null)
		{
			env.put("GIT_COMMITTER_NAME", c[0]);
			env.put("GIT_COMMITTER_EMAIL", c[1]);
		}
		if(!paths.isEmpty())
		{
			args.add("--only");
			args.add("--");
			args.addAll(paths);
		}
		return GitRunner.run(root, env, args.toArray(new String[0]));
	}

	// "Name <email>" as {name, email}, or null if it is empty or malformed
	public static String[] split(String identity)
	{
		String s = identity.trim();
		int lt = s.lastIndexOf('<');
		int gt = s.lastIndexOf('>');
		if(s.isEmpty() || lt < 0 || gt < lt)
		{
			return null;
		}
		return new String[] {s.substring(0, lt).trim(), s.substring(lt + 1, gt).trim()};
	}

	// ---- branches, tags and history

	public GitResult checkout(String rev)
	{
		return git("checkout", rev);
	}

	public GitResult switchNew(String name, String start)
	{
		return start == null || start.isEmpty() ? git("checkout", "-b", name) : git("checkout", "-b", name, start);
	}

	public GitResult createBranch(String name, String at)
	{
		return git("branch", name, at);
	}

	public GitResult deleteBranch(String name, boolean force)
	{
		return git("branch", force ? "-D" : "-d", name);
	}

	public GitResult renameBranch(String from, String to)
	{
		return git("branch", "-m", from, to);
	}

	public GitResult createTag(String name, String at)
	{
		return git("tag", name, at);
	}

	public GitResult merge(String rev)
	{
		return git("merge", "--no-edit", rev);
	}

	public GitResult rebase(String onto)
	{
		return git("rebase", onto);
	}

	// mode: soft, mixed or hard
	public GitResult reset(String mode, String rev)
	{
		return git("reset", "--" + mode, rev);
	}

	public GitResult revert(String rev)
	{
		return git("revert", "--no-edit", rev);
	}

	public GitResult cherryPick(String rev)
	{
		return git("cherry-pick", rev);
	}

	public GitResult abortMerge()
	{
		return git("merge", "--abort");
	}

	// ---- remotes

	public GitResult push(String remote, String branch)
	{
		return git("push", "-u", remote, branch);
	}

	public GitResult fetch(String remote)
	{
		return git("fetch", "--prune", remote);
	}

	public GitResult pull()
	{
		return git("pull", "--no-edit");
	}

	// ---- stashes

	public GitResult stash(String message)
	{
		return message.trim().isEmpty() ? git("stash", "push", "--include-untracked") : git("stash", "push", "--include-untracked", "-m", message.trim());
	}

	public List<Stash> stashes()
	{
		List<Stash> list = new ArrayList<Stash>();
		GitResult r = git("stash", "list", "--format=%gd" + SEP + "%gs");
		if(r.ok())
		{
			for(String line : r.out.split("\n"))
			{
				String[] f = line.split(SEP, 2);
				if(f.length == 2)
				{
					list.add(new Stash(f[0], f[1]));
				}
			}
		}
		return list;
	}

	public GitResult stashApply(String ref)
	{
		return git("stash", "apply", ref);
	}

	public GitResult stashPop(String ref)
	{
		return git("stash", "pop", ref);
	}

	public GitResult stashDrop(String ref)
	{
		return git("stash", "drop", ref);
	}

	GitResult withPaths(List<String> head, List<String> paths)
	{
		List<String> args = new ArrayList<String>(head);
		args.addAll(paths);
		return git(args.toArray(new String[0]));
	}
}
