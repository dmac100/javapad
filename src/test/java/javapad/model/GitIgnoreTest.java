package javapad.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

public class GitIgnoreTest
{
	GitIgnore rules(String... lines)
	{
		return new GitIgnore(new ArrayList<GitIgnore.Rule>()).child("/p", Arrays.asList(lines));
	}

	boolean ignored(GitIgnore g, String path, boolean dir)
	{
		return g.ignored(new File("/p/" + path), dir);
	}

	@Test
	public void namesMatchAtAnyDepth()
	{
		GitIgnore g = rules("*.class", "bin/");
		assertTrue(ignored(g, "A.class", false));
		assertTrue(ignored(g, "src/x/A.class", false));
		assertTrue(ignored(g, "bin", true));
		assertTrue(ignored(g, "src/bin", true));
		assertFalse(ignored(g, "bin", false));
		assertFalse(ignored(g, "A.java", false));
	}

	@Test
	public void slashAnchorsToTheFolder()
	{
		GitIgnore g = rules("/out", "doc/*.txt");
		assertTrue(ignored(g, "out", true));
		assertFalse(ignored(g, "src/out", true));
		assertTrue(ignored(g, "doc/a.txt", false));
		assertFalse(ignored(g, "doc/x/a.txt", false));
	}

	@Test
	public void negationAndComments()
	{
		GitIgnore g = rules("# comment", "", "*.log", "!keep.log");
		assertTrue(ignored(g, "a.log", false));
		assertFalse(ignored(g, "keep.log", false));
	}

	@Test
	public void doubleStar()
	{
		GitIgnore g = rules("a/**/z", "logs/**");
		assertTrue(ignored(g, "a/z", false));
		assertTrue(ignored(g, "a/b/c/z", false));
		assertTrue(ignored(g, "logs/x/y", false));
		assertEquals(false, ignored(g, "b/z", false));
	}

	@Test
	public void dotSegmentsInThePathDoNotHideAnchoredRules()
	{
		GitIgnore g = rules("/out");
		assertTrue(g.ignored(new File("/p/./sub/../out"), true));
	}
}
