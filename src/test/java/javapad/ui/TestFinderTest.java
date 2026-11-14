package javapad.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javapad.run.TestFinder;
import org.junit.Test;

public class TestFinderTest
{
	@Test
	public void detectsJunit4AndJunit5()
	{
		assertTrue(TestFinder.isTestClass("import org.junit.Test;\nclass A { @Test public void a() {} }"));
		assertTrue(TestFinder.isTestClass("import org.junit.jupiter.api.Test;\nclass A { @Test void a() {} }"));
		assertTrue(TestFinder.isJupiter("import org.junit.jupiter.api.Test;"));
		assertFalse(TestFinder.isJupiter("import org.junit.Test;"));
	}

	@Test
	public void ignoresHelpersAndAbstractClasses()
	{
		assertFalse(TestFinder.isTestClass("import org.junit.Assert;\nclass Helper {}"));
		assertFalse(TestFinder.isTestClass("import org.junit.Test;\nabstract class Base { @Test public void a() {} }"));
		assertFalse(TestFinder.isTestClass("class Plain { }"));
	}
}
