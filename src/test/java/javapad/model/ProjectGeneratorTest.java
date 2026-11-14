package javapad.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Test;

public class ProjectGeneratorTest
{
	static File temp() throws Exception
	{
		return Files.createTempDirectory("gen").toFile();
	}

	@Test
	public void mavenLayoutWins() throws Exception
	{
		File d = temp();
		new File(d, "src/main/java").mkdirs();
		new File(d, "src/test/java").mkdirs();
		new File(d, "src/main/resources").mkdirs();
		new File(d, "src/other").mkdirs();
		new File(d, "test").mkdirs();
		assertEquals(Arrays.asList("src/main/java", "src/main/resources", "src/test/java"), ProjectGenerator.sourceFolders(d));
	}

	@Test
	public void plainSrcAndTestOnlyIfTheyExist() throws Exception
	{
		File d = temp();
		new File(d, "src").mkdirs();
		assertEquals(Arrays.asList("src"), ProjectGenerator.sourceFolders(d));
		new File(d, "test").mkdirs();
		assertEquals(Arrays.asList("src", "test"), ProjectGenerator.sourceFolders(d));
	}

	@Test
	public void createsAndParsesProject() throws Exception
	{
		File d = temp();
		new File(d, "src").mkdirs();
		new File(d, "lib").mkdirs();
		new File(d, "lib/b.jar").createNewFile();
		new File(d, "lib/a.jar").createNewFile();
		new File(d, "lib/readme.txt").createNewFile();
		EclipseProject p = EclipseProject.open(d);
		assertTrue(new File(d, ".project").exists());
		assertEquals(1, p.sourceFolders.size());
		assertEquals(Arrays.asList(new File(d, "lib/a.jar").getAbsoluteFile(), new File(d, "lib/b.jar").getAbsoluteFile()), new java.util.ArrayList<File>(p.libraries));
		assertEquals(2, p.notes.size());
	}

	@Test
	public void existingFilesAreKeptUnlessRegenerated() throws Exception
	{
		File d = temp();
		new File(d, "src").mkdirs();
		Files.write(new File(d, ".classpath").toPath(), "<classpath><classpathentry kind=\"src\" path=\"other\"/></classpath>".getBytes());
		EclipseProject kept = EclipseProject.open(d);
		assertEquals(new File(d, "other"), kept.sourceFolders.get(0));
		EclipseProject fresh = EclipseProject.regenerate(d);
		assertEquals(new File(d, "src"), fresh.sourceFolders.get(0));
	}
}
