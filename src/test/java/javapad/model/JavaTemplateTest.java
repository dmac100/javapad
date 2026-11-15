package javapad.model;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class JavaTemplateTest
{
	List<File> sources = Arrays.asList(new File("/p/src"), new File("/p/test"));

	@Test
	public void classInTheSourceRootHasNoPackage()
	{
		assertEquals("public class Foo {\n}", JavaTemplate.of(new File("/p/src/Foo.java"), sources));
	}

	@Test
	public void packageFollowsTheFolderUnderASourceFolder()
	{
		assertEquals("package a.b;\n\npublic class Foo {\n}", JavaTemplate.of(new File("/p/test/a/b/Foo.java"), sources));
	}

	@Test
	public void invalidClassNamesGetNoTemplate()
	{
		assertEquals("", JavaTemplate.of(new File("/p/src/my-file.java"), sources));
		assertEquals("", JavaTemplate.of(new File("/p/src/9lives.java"), sources));
	}

	@Test
	public void outsideAnySourceFolderHasNoPackage()
	{
		assertEquals("public class Foo {\n}", JavaTemplate.of(new File("/elsewhere/x/Foo.java"), Collections.<File>emptyList()));
	}
}
