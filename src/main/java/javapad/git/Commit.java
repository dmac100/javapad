package javapad.git;

import java.util.ArrayList;
import java.util.List;

// One entry of "git log" with its parents and the branches and tags that point at it.
public class Commit {
	public static final int LOCAL = 0;
	public static final int REMOTE = 1;
	public static final int TAG = 2;
	public final String hash;
	public final List<String> parents;
	public final String authorName;
	public final String authorEmail;
	public final String authorDate;
	public final String committerName;
	public final String committerEmail;
	public final String commitDate;
	public final List<Ref> refs;
	public final String subject;
	public final String body;

	public static class Ref {
		public final String name;
		public final int kind;
		// the branch (or detached HEAD) that is checked out
		public final boolean current;

		public Ref(String name, int kind, boolean current) {
			this.name = name;
			this.kind = kind;
			this.current = current;
		}
	}

	public Commit(String hash, List<String> parents, String authorName, String authorEmail, String authorDate, String committerName, String committerEmail, String commitDate,
		List<Ref> refs, String subject, String body) {
		this.hash = hash;
		this.parents = parents;
		this.authorName = authorName;
		this.authorEmail = authorEmail;
		this.authorDate = authorDate;
		this.committerName = committerName;
		this.committerEmail = committerEmail;
		this.commitDate = commitDate;
		this.refs = refs;
		this.subject = subject;
		this.body = body;
	}

	// The same commit with other parents, for a log that skips some commits (the history of one file).
	public Commit withParents(List<String> newParents) {
		return new Commit(hash, newParents, authorName, authorEmail, authorDate, committerName, committerEmail, commitDate, refs, subject, body);
	}

	public String shortHash() {
		return hash.length() > 8 ? hash.substring(0, 8) : hash;
	}

	// "2026-01-02T03:04:05+00:00" as "2026-01-02 03:04"
	public static String shortDate(String iso) {
		String s = iso.replace('T', ' ');
		return s.length() > 16 ? s.substring(0, 16) : s;
	}

	public List<Ref> branches() {
		List<Ref> list = new ArrayList<Ref>();
		for(Ref r : refs) {
			if(r.kind == LOCAL) {
				list.add(r);
			}
		}
		return list;
	}

	// "HEAD -> refs/heads/main, refs/remotes/origin/main, tag: refs/tags/v1" (the %D of git log --decorate=full)
	public static List<Ref> parseRefs(String decoration) {
		List<Ref> list = new ArrayList<Ref>();
		for(String part : decoration.split(", ")) {
			String p = part.trim();
			if(p.isEmpty()) {
				continue;
			}
			boolean current = false;
			if(p.startsWith("HEAD -> ")) {
				current = true;
				p = p.substring("HEAD -> ".length());
			}
			if(p.equals("HEAD")) {
				list.add(new Ref("HEAD", LOCAL, true));
			} else if(p.startsWith("tag: refs/tags/")) {
				list.add(new Ref(p.substring("tag: refs/tags/".length()), TAG, false));
			} else if(p.startsWith("refs/heads/")) {
				list.add(new Ref(p.substring("refs/heads/".length()), LOCAL, current));
			} else if(p.startsWith("refs/remotes/")) {
				String name = p.substring("refs/remotes/".length());
				if(!name.endsWith("/HEAD")) {
					list.add(new Ref(name, REMOTE, false));
				}
			}
		}
		return list;
	}
}
