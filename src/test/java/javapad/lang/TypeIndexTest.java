package javapad.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

public class TypeIndexTest
{
	static TypeIndex index;
	static File dir;

	@BeforeClass
	public static void build() throws Exception
	{
		dir = Files.createTempDirectory("idx").toFile();
		File pkg = new File(dir, "src/my/pkg");
		pkg.mkdirs();
		Files.write(new File(pkg, "Widget.java").toPath(), "package my.pkg;\npublic class Widget {}\n".getBytes());
		Files.write(new File(pkg, "Shape.java").toPath(), "package my.pkg;\n\npublic interface Shape {}\n".getBytes());
		Files.write(new File(pkg, "Hidden.java").toPath(), "package my.pkg;\nclass Hidden {}\n".getBytes());
		File junit = new File("lib/junit-4.13.2.jar");
		index = TypeIndex.build(Arrays.asList(new File(dir, "src")), junit.exists() ? Arrays.asList(junit) : Collections.<File>emptyList());
	}

	@Test
	public void findsJdkTypesWithKinds()
	{
		TypeIndex.Entry list = index.find("java.util.List");
		assertNotNull(list);
		assertEquals(TypeIndex.INTERFACE, list.kind);
		assertEquals(TypeIndex.JDK, list.origin);
		assertEquals(TypeIndex.ANNOTATION, index.find("java.lang.Override").kind);
		assertEquals(TypeIndex.ENUM, index.find("java.util.concurrent.TimeUnit").kind);
		assertNotNull(index.find("java.util.Map.Entry"));
	}

	@Test
	public void leavesOutNonPublicTypes()
	{
		assertNull(index.find("java.util.ArrayPrefixHelpers"));
		assertNull(index.find("my.pkg.Hidden"));
	}

	@Test
	public void indexesProjectSourcesAndJars()
	{
		assertEquals(TypeIndex.PROJECT, index.find("my.pkg.Widget").origin);
		assertEquals(TypeIndex.INTERFACE, index.find("my.pkg.Shape").kind);
		if(new File("lib/junit-4.13.2.jar").exists())
		{
			assertEquals(TypeIndex.LIBRARY, index.find("org.junit.Test").origin);
			assertEquals(TypeIndex.ANNOTATION, index.find("org.junit.Test").kind);
		}
	}

	@Test
	public void ambiguousSimpleNames()
	{
		List<TypeIndex.Entry> lists = index.exact("List");
		boolean util = false;
		boolean awt = false;
		for(TypeIndex.Entry e : lists)
		{
			util |= e.fqn.equals("java.util.List");
			awt |= e.fqn.equals("java.awt.List");
		}
		assertTrue(util);
		assertTrue(awt);
	}

	@Test
	public void prefixAndCamelHumpSearch()
	{
		boolean found = false;
		for(TypeIndex.Entry e : index.withPrefix("ArrayLi", 50))
		{
			found |= e.fqn.equals("java.util.ArrayList");
		}
		assertTrue(found);
		found = false;
		for(TypeIndex.Entry e : index.withPrefix("NPE", 50))
		{
			found |= e.fqn.equals("java.lang.NullPointerException");
		}
		assertTrue(found);
		assertTrue(index.withPrefix("zzzzqq", 10).isEmpty());
	}

	@Test
	public void packages()
	{
		assertTrue(index.hasPackage("java.util"));
		assertTrue(index.hasPackage("my.pkg"));
		assertFalse(index.hasPackage("java.utilx"));
		assertTrue(index.childPackages("").contains("java"));
		assertTrue(index.childPackages("java").contains("util"));
		assertTrue(index.childPackages("my").contains("pkg"));
		boolean found = false;
		for(TypeIndex.Entry e : index.typesIn("my.pkg"))
		{
			found |= e.simple.equals("Widget");
		}
		assertTrue(found);
	}
}
