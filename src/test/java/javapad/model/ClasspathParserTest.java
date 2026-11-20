package javapad.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Test;

public class ClasspathParserTest {
	static final File CLAUDE = new File(System.getProperty("user.home"), "claude");

	@Test
	public void parsesDoom() throws Exception {
		File dir = new File(CLAUDE, "doom");
		if(!dir.exists()) {
			return;
		}
		EclipseProject p = ClasspathParser.parse(dir);
		assertEquals("Doom", p.name);
		assertEquals(2, p.sourceFolders.size());
		assertEquals(new File(dir, "bin"), p.outputFolder);
		assertEquals(8, p.release);
		assertTrue(p.junit4);
		assertTrue(p.classpathString().contains("guava-18.0.jar"));
		assertTrue(p.classpathString().contains("junit-4.13.2.jar"));
	}

	@Test
	public void parsesDarkbasic() throws Exception {
		File dir = new File(CLAUDE, "darkbasic");
		if(!dir.exists()) {
			return;
		}
		EclipseProject p = ClasspathParser.parse(dir);
		assertEquals(2, p.sourceFolders.size());
		assertTrue(p.libraries.contains(new File(dir, "lib/junit-4.13.2.jar")));
		assertEquals("dbi.interpreter.LanguageTest", p.classNameOf(new File(dir, "test/dbi/interpreter/LanguageTest.java")));
		assertNull(p.classNameOf(new File(dir, "README.md")));
	}

	@Test
	public void acceptsJunitContainers() throws Exception {
		for(String version : new String[] {"5", "6"}) {
			EclipseProject p = new EclipseProject(new File("."));
			ClasspathParser.addContainer(p, "org.eclipse.jdt.junit.JUNIT_CONTAINER/" + version);
			assertTrue(p.junit5);
			assertTrue(p.warnings.isEmpty());
		}
	}
}
