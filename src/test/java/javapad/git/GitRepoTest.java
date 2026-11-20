package javapad.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class GitRepoTest
{
	private File dir;
	private GitRepo repo;

	@Before
	public void setUp() throws Exception
	{
		dir = Files.createTempDirectory("gitrepo").toFile();
		assertTrue(GitRepo.init(dir).ok());
		GitRunner.run(dir, "config", "user.name", "Tester");
		GitRunner.run(dir, "config", "user.email", "t@example.com");
		GitRunner.run(dir, "checkout", "-q", "-b", "main");
		repo = GitRepo.find(dir);
	}

	void write(String name, String text) throws Exception
	{
		File f = new File(dir, name);
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	String read(String name) throws Exception
	{
		return new String(Files.readAllBytes(new File(dir, name).toPath()), StandardCharsets.UTF_8);
	}

	void commit(String name, String text, String message) throws Exception
	{
		write(name, text);
		assertTrue(repo.add(Arrays.asList(name)).ok());
		GitResult r = repo.commit(message, "", "", false, Arrays.asList());
		assertTrue(r.message(), r.ok());
	}

	@Test
	public void findReturnsNullOutsideARepository() throws Exception
	{
		assertNull(GitRepo.find(Files.createTempDirectory("plain").toFile()));
		assertNotNull(repo);
	}

	@Test
	public void statusShowsUntrackedStagedAndModified() throws Exception
	{
		commit("a.txt", "one\n", "first");
		write("a.txt", "two\n");
		write("src/b.txt", "new\n");
		GitStatus s = repo.status(true);
		assertEquals("main", s.branch);
		assertEquals("modified", s.find("a.txt").label());
		assertTrue(s.find("src/b.txt").untracked());
		repo.add(Arrays.asList("a.txt", "src/b.txt"));
		s = repo.status(true);
		assertTrue(s.find("a.txt").staged());
		assertEquals("staged added", s.find("src/b.txt").label());
		repo.unstage(Arrays.asList("a.txt"));
		assertFalse(repo.status(true).find("a.txt").staged());
	}

	@Test
	public void untrackedFoldersCountForEverythingInThem() throws Exception
	{
		commit("a.txt", "one\n", "first");
		commit("src/q.txt", "q\n", "second");
		write("newdir/x/y.txt", "y\n");
		write("src/z.txt", "z\n");
		GitStatus s = repo.status(false);
		assertTrue(s.untracked("newdir"));
		assertTrue(s.untracked("newdir/x/y.txt"));
		assertTrue(s.untracked("src/z.txt"));
		assertFalse(s.untracked("a.txt"));
		assertFalse(s.untracked("src"));
	}

	@Test
	public void logCarriesParentsRefsAndDetails() throws Exception
	{
		commit("a.txt", "one\n", "first");
		commit("a.txt", "two\n", "second | with bar\n\nbody text");
		repo.createTag("v1", "HEAD");
		List<Commit> all = repo.log(null, 10, true);
		assertEquals(2, all.size());
		Commit top = all.get(0);
		assertEquals("second | with bar", top.subject);
		assertEquals("body text", top.body);
		assertEquals("Tester", top.authorName);
		assertEquals(Arrays.asList(all.get(1).hash), top.parents);
		assertEquals(2, top.refs.size());
		assertEquals("main", top.refs.get(0).name);
		assertTrue(top.refs.get(0).current);
		assertEquals(Commit.TAG, top.refs.get(1).kind);
		assertEquals("one\n", repo.show(all.get(1).hash, "a.txt"));
		assertNull(repo.show("HEAD", "missing.txt"));
		assertEquals(2, repo.log("a.txt", 10, false).size());
	}

	@Test
	public void changedFilesAndDiffs() throws Exception
	{
		commit("a.txt", "one\n", "first");
		write("a.txt", "two\n");
		write("b.txt", "b\n");
		assertTrue(repo.add(Arrays.asList("a.txt", "b.txt")).ok());
		assertTrue(repo.commit("second", "", "", false, Arrays.asList()).ok());
		String head = repo.log(null, 1, false).get(0).hash;
		List<ChangedFile> files = repo.changedFiles(head);
		assertEquals(2, files.size());
		assertEquals('M', files.get(0).status);
		assertEquals("a.txt", files.get(0).path);
		assertEquals('A', files.get(1).status);
		assertTrue(repo.commitDiff(head, "a.txt").contains("+two"));
		assertTrue(repo.diff(head + "^", head, "a.txt").contains("-one"));
		write("a.txt", "three\n");
		assertTrue(repo.diff(head, null, "a.txt").contains("+three"));
	}

	@Test
	public void parsesRenamesInNameStatus()
	{
		List<ChangedFile> list = GitRepo.parseNameStatus("R100\0old.txt\0new.txt\0D\0gone.txt\0");
		assertEquals(2, list.size());
		assertEquals('R', list.get(0).status);
		assertEquals("new.txt", list.get(0).path);
		assertEquals("old.txt", list.get(0).oldPath);
		assertEquals("gone.txt", list.get(1).path);
	}

	@Test
	public void commitOnlyTheChosenFilesWithAuthorAndCommitter() throws Exception
	{
		commit("a.txt", "one\n", "first");
		write("a.txt", "two\n");
		write("b.txt", "b\n");
		write("c.txt", "c\n");
		GitResult r = repo.commit("pick", "Ann <ann@x.org>", "Bob <bob@x.org>", false, Arrays.asList("a.txt", "b.txt"));
		assertTrue(r.message(), r.ok());
		Commit c = repo.log(null, 1, false).get(0);
		assertEquals("Ann", c.authorName);
		assertEquals("ann@x.org", c.authorEmail);
		assertEquals("Bob", c.committerName);
		assertEquals(1, repo.status(true).changes.size());
		assertTrue(repo.status(true).find("c.txt").untracked());
		assertEquals("Ann <ann@x.org>", repo.lastAuthor());
		assertEquals("pick", repo.lastMessage());
		assertTrue(repo.commit("better", "", "", true, Arrays.asList()).ok());
		assertEquals("better", repo.lastMessage());
	}

	@Test
	public void ignoreUntrackAndRestore() throws Exception
	{
		commit("a.txt", "one\n", "first");
		write("a.txt", "changed\n");
		assertTrue(repo.restore("HEAD", Arrays.asList("a.txt")).ok());
		assertEquals("one\n", read("a.txt"));
		write("out/x.class", "x");
		assertTrue(repo.ignore("out", true).ok());
		assertTrue(repo.ignore("a.txt", false).ok());
		assertEquals("/out/\n/a.txt\n", read(".gitignore"));
		assertTrue(repo.untrack(Arrays.asList("a.txt")).ok());
		assertEquals("one\n", read("a.txt"));
		assertEquals('D', repo.status(true).find("a.txt").index);
	}

	@Test
	public void branchesTagsResetAndMerge() throws Exception
	{
		commit("a.txt", "one\n", "first");
		assertTrue(repo.switchNew("feature", null).ok());
		commit("f.txt", "f\n", "feature work");
		assertTrue(repo.checkout("main").ok());
		assertEquals(Arrays.asList("feature", "main"), repo.branches());
		assertTrue(repo.merge("feature").ok());
		assertTrue(new File(dir, "f.txt").exists());
		assertTrue(repo.renameBranch("feature", "feat2").ok());
		assertTrue(repo.deleteBranch("feat2", false).ok());
		String first = repo.log(null, 10, false).get(1).hash;
		assertTrue(repo.reset("hard", first).ok());
		assertFalse(new File(dir, "f.txt").exists());
		assertEquals(1, repo.log(null, 10, false).size());
	}

	@Test
	public void conflictedMergeCanBeAborted() throws Exception
	{
		commit("a.txt", "one\n", "first");
		repo.switchNew("other", null);
		commit("a.txt", "other\n", "other change");
		repo.checkout("main");
		commit("a.txt", "main\n", "main change");
		assertFalse(repo.merge("other").ok());
		assertTrue(repo.merging());
		assertTrue(repo.status(true).hasConflicts());
		assertTrue(repo.abortMerge().ok());
		assertFalse(repo.merging());
	}

	@Test
	public void cherryPickAndRevert() throws Exception
	{
		commit("a.txt", "one\n", "first");
		repo.switchNew("other", null);
		commit("b.txt", "b\n", "add b");
		String pick = repo.log(null, 1, false).get(0).hash;
		repo.checkout("main");
		assertTrue(repo.cherryPick(pick).ok());
		assertTrue(new File(dir, "b.txt").exists());
		assertTrue(repo.revert("HEAD").ok());
		assertFalse(new File(dir, "b.txt").exists());
	}

	@Test
	public void stashSaveListAndPop() throws Exception
	{
		commit("a.txt", "one\n", "first");
		write("a.txt", "dirty\n");
		write("n.txt", "new\n");
		assertTrue(repo.stash("wip").ok());
		assertEquals(0, repo.status(true).changes.size());
		List<Stash> list = repo.stashes();
		assertEquals(1, list.size());
		assertEquals("stash@{0}", list.get(0).ref);
		assertTrue(list.get(0).description.contains("wip"));
		assertTrue(repo.stashPop("stash@{0}").ok());
		assertEquals("dirty\n", read("a.txt"));
		assertEquals(0, repo.stashes().size());
	}

	@Test
	public void splitsIdentities()
	{
		assertEquals("Ann Lee", GitRepo.split("Ann Lee <a@b.c>")[0]);
		assertEquals("a@b.c", GitRepo.split("Ann Lee <a@b.c>")[1]);
		assertNull(GitRepo.split("nobody"));
		assertNull(GitRepo.split(""));
	}

	@Test
	public void parsesAheadAndBehind()
	{
		GitStatus s = GitStatus.parse("# branch.oid abc\0# branch.head dev\0# branch.upstream origin/dev\0# branch.ab +3 -2\0");
		assertEquals(3, s.ahead);
		assertEquals(2, s.behind);
	}

	@Test
	public void parsesCannedStatus()
	{
		String text = "# branch.oid abc\0# branch.head dev\0" + "1 .M N... 100644 100644 100644 aaa bbb src/A.java\0" + "2 R. N... 100644 100644 100644 aaa bbb R100 new name.txt\0old name.txt\0"
			+ "u UU N... 1 2 3 4 aaa bbb ccc c.txt\0? loose.txt\0";
		GitStatus s = GitStatus.parse(text);
		assertEquals("dev", s.branch);
		assertEquals(4, s.changes.size());
		assertEquals(' ', s.changes.get(0).index);
		assertEquals("staged renamed", s.changes.get(1).label());
		assertTrue(s.changes.get(2).conflicted());
		assertTrue(s.changes.get(3).untracked());
	}

	@Test
	public void parsesDecorations()
	{
		List<Commit.Ref> refs = Commit.parseRefs("HEAD -> refs/heads/feature/x, tag: refs/tags/v1, refs/remotes/origin/main, refs/remotes/origin/HEAD, refs/heads/main");
		assertEquals(4, refs.size());
		assertEquals("feature/x", refs.get(0).name);
		assertTrue(refs.get(0).current);
		assertEquals(Commit.TAG, refs.get(1).kind);
		assertEquals(Commit.REMOTE, refs.get(2).kind);
		assertEquals("origin/main", refs.get(2).name);
		assertEquals(Commit.LOCAL, refs.get(3).kind);
		assertTrue(Commit.parseRefs("HEAD").get(0).current);
		assertEquals(0, Commit.parseRefs("").size());
		assertEquals("2026-01-02 03:04", Commit.shortDate("2026-01-02T03:04:05+00:00"));
	}

	@Test
	public void relativePathsIgnoreDotSegmentsAndSymbolicLinks() throws Exception
	{
		write("src/a.txt", "a\n");
		assertEquals("src/a.txt", repo.relative(new File(dir, "src/a.txt")));
		assertEquals("src/a.txt", repo.relative(new File(dir, "./src/../src/./a.txt")));
		assertEquals("", repo.relative(new File(dir, ".")));
		assertNull(repo.relative(new File(dir.getParentFile(), "elsewhere/x.txt")));
		File link = new File(Files.createTempDirectory("links").toFile(), "linked");
		Files.createSymbolicLink(link.toPath(), dir.toPath());
		assertEquals("src/a.txt", repo.relative(new File(link, "src/a.txt")));
		// a repository found through a path with a dot segment still works
		GitRepo viaDot = GitRepo.find(new File(dir, "src/."));
		assertEquals("src/a.txt", viaDot.relative(new File(dir, "src/./a.txt")));
	}

	@Test
	public void assumeUnchangedHidesLocalChangesAndCanBeUndone() throws Exception
	{
		commit("a.txt", "one\n", "first");
		commit("sub/b.txt", "one\n", "second");
		assertFalse(repo.assumedUnchanged("a.txt"));
		assertTrue(repo.assumeUnchanged("a.txt", true).ok());
		assertTrue(repo.assumedUnchanged("a.txt"));
		assertFalse(repo.assumedUnchanged("sub"));
		write("a.txt", "two\n");
		assertNull(repo.status(false).find("a.txt"));
		assertTrue(repo.assumeUnchanged("a.txt", false).ok());
		assertFalse(repo.assumedUnchanged("a.txt"));
		assertNotNull(repo.status(false).find("a.txt"));
		assertTrue(repo.assumeUnchanged("sub", true).ok());
		assertTrue(repo.assumedUnchanged("sub"));
		assertTrue(repo.assumedUnchanged("sub/b.txt"));
	}

	@Test
	public void conflictsAreFoundForFilesAndFolders() throws Exception
	{
		GitStatus s = new GitStatus("main", Arrays.asList(new FileChange("src/a.txt", 'U', 'U'), new FileChange("b.txt", ' ', 'M')));
		assertTrue(s.conflicted("src/a.txt"));
		assertTrue(s.conflicted("src"));
		assertTrue(s.conflicted(""));
		assertFalse(s.conflicted("b.txt"));
		assertFalse(s.conflicted("sr"));
	}
}
