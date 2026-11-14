package javapad.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javapad.run.MainFinder;
import org.junit.Test;

public class MainFinderTest
{
	@Test
	public void detectsMainSignatures()
	{
		assertTrue(MainFinder.hasMain("public static void main(String[] args) {"));
		assertTrue(MainFinder.hasMain("public static void main(String... args)"));
		assertTrue(MainFinder.hasMain("public static void main( final String args[] )"));
		assertTrue(MainFinder.hasMain("static void main(String[]args)"));
	}

	@Test
	public void ignoresOtherMethods()
	{
		assertFalse(MainFinder.hasMain("public void main(int x)"));
		assertFalse(MainFinder.hasMain("public static void mainLoop(String[] args)"));
		assertFalse(MainFinder.hasMain("class A { }"));
	}
}
