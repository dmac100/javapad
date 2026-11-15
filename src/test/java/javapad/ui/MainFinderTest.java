package javapad.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javapad.run.MainFinder;
import org.junit.Assume;
import org.junit.Test;

public class MainFinderTest
{
	static final File FILE = new File("/tmp/Sample.java");
	static final int FEATURE = Runtime.version().feature();

	static List<String> names(String source)
	{
		List<String> names = new ArrayList<String>();
		for(MainFinder.Main m : MainFinder.inSource(FILE, source))
		{
			names.add(m.className);
		}
		return names;
	}

	@Test
	public void classicSignatures()
	{
		assertEquals(Arrays.asList("A"), names("public class A { public static void main(String[] args) {} }"));
		assertEquals(Arrays.asList("A"), names("public class A { public static void main(String... args) {} }"));
		assertEquals(Arrays.asList("A"), names("public class A { public static void main(final String args[]) {} }"));
		assertEquals(Arrays.asList("A"), names("class A { public static void main(java.lang.String[] a) {} }"));
		MainFinder.Main m = MainFinder.inSource(FILE, "public class A { public static void main(String[] args) {} }").get(0);
		assertTrue(m.classic);
		assertFalse(m.preview);
	}

	@Test
	public void notMains()
	{
		assertTrue(names("class A { public void main(int x) {} }").isEmpty());
		assertTrue(names("class A { public static void mainLoop(String[] args) {} }").isEmpty());
		assertTrue(names("class A { private static void main(String[] args) {} }").isEmpty());
		assertTrue(names("class A { public static int main(String[] args) { return 0; } }").isEmpty());
		assertTrue(names("class A { public static void main(String a, String b) {} }").isEmpty());
		assertTrue(names("class A { public static void main(Object[] a) {} }").isEmpty());
		assertTrue(names("class A { }").isEmpty());
		assertTrue(names("abstract class A { abstract void main(); }").isEmpty());
	}

	@Test
	public void stringsAndCommentsAreIgnored()
	{
		assertTrue(names("class A { String s = \"public static void main(String[] args)\"; }").isEmpty());
		assertTrue(names("class A {\n// public static void main(String[] args) {}\n/* static void main(String[] a) {} */\n}").isEmpty());
		assertTrue(names("class A { /** static void main(String[] args) */ void f() {} }").isEmpty());
	}

	@Test
	public void packagesAndNestedClasses()
	{
		String source = "package a.b;\npublic class Outer {\n\tpublic static void main(String[] args) {}\n\tstatic class Inner {\n\t\tpublic static void main(String[] args) {}\n\t}\n\tinterface Tool {\n\t\tstatic void main(String[] args) {}\n\t}\n}\nclass Other {\n\tpublic static void main(String[] args) {}\n}\n";
		assertEquals(Arrays.asList("a.b.Outer", "a.b.Outer$Inner", "a.b.Outer$Tool", "a.b.Other"), names(source));
	}

	@Test
	public void linesPointAtTheMethod()
	{
		List<MainFinder.Main> found = MainFinder.inSource(FILE, "class A {\n\n\tpublic static void main(String[] args) {\n\t}\n}\n");
		assertEquals(3, found.get(0).line);
	}

	@Test
	public void newerStylesNeedAtLeastJava21()
	{
		String instance = "class A { void main() {} }";
		String staticNoArgs = "class A { static void main() {} }";
		String packagePrivate = "class A { static void main(String[] args) {} }";
		String instanceArgs = "class A { public void main(String[] args) {} }";
		if(FEATURE < 21)
		{
			assertTrue(names(instance).isEmpty());
			assertTrue(names(staticNoArgs).isEmpty());
			assertTrue(names(packagePrivate).isEmpty());
			return;
		}
		assertEquals(Arrays.asList("A"), names(instance));
		assertEquals(Arrays.asList("A"), names(staticNoArgs));
		assertEquals(Arrays.asList("A"), names(packagePrivate));
		assertEquals(Arrays.asList("A"), names(instanceArgs));
		for(String source : Arrays.asList(instance, staticNoArgs, packagePrivate, instanceArgs))
		{
			MainFinder.Main m = MainFinder.inSource(FILE, source).get(0);
			assertFalse(m.classic);
			assertEquals("preview features before java 25", FEATURE < 25, m.preview);
		}
	}

	@Test
	public void oneMainPerClassFollowingTheLaunchOrder()
	{
		Assume.assumeTrue(FEATURE >= 21);
		List<MainFinder.Main> found = MainFinder.inSource(FILE, "class A {\n\tvoid main() {}\n\tstatic void main(String[] args) {}\n\tstatic void main() {}\n}\n");
		assertEquals(1, found.size());
		assertEquals("static void main(String[])", found.get(0).signature);
		found = MainFinder.inSource(FILE, "class A {\n\tvoid main() {}\n\tstatic void main() {}\n}\n");
		assertEquals("static void main()", found.get(0).signature);
		found = MainFinder.inSource(FILE, "class A {\n\tvoid main() {}\n\tvoid main(String[] args) {}\n}\n");
		assertEquals("void main(String[])", found.get(0).signature);
	}

	@Test
	public void instanceMainsNeedAnUsableConstructor()
	{
		Assume.assumeTrue(FEATURE >= 21);
		assertTrue(names("class A { A(int x) {} void main() {} }").isEmpty());
		assertTrue(names("class A { private A() {} void main() {} }").isEmpty());
		assertTrue(names("abstract class A { void main() {} }").isEmpty());
		assertEquals(Arrays.asList("A"), names("class A { A() {} void main() {} }"));
		assertEquals(Arrays.asList("A"), names("class A { A(int x) {} A() {} void main() {} }"));
		assertEquals(Arrays.asList("A$B"), names("class A { static class B { void main() {} } }"));
		assertTrue(names("class A { class B { void main() {} } }").isEmpty());
		assertEquals(Arrays.asList("A$B"), names("class A { class B { static void main(String[] args) {} } }"));
		assertTrue(names("interface A { default void main() {} }").isEmpty());
	}

	@Test
	public void compactSourceFiles() throws Exception
	{
		Assume.assumeTrue(FEATURE >= 21);
		File dir = Files.createTempDirectory("main").toFile();
		File hello = new File(dir, "Hello.java");
		String source = "String greeting = \"hi\";\n\nvoid main() {\n\tSystem.out.println(greeting);\n}\n";
		Files.write(hello.toPath(), source.getBytes());
		List<MainFinder.Main> found = MainFinder.inSource(hello, source);
		assertEquals(1, found.size());
		assertEquals("Hello", found.get(0).className);
		assertEquals(3, found.get(0).line);
	}

	@Test
	public void projectScanUsesCacheAndUnsavedText() throws Exception
	{
		File dir = Files.createTempDirectory("scan").toFile();
		File src = new File(dir, "src/p");
		src.mkdirs();
		Files.write(new File(src, "One.java").toPath(), "package p;\npublic class One { public static void main(String[] a) {} }\n".getBytes());
		Files.write(new File(src, "Two.java").toPath(), "package p;\npublic class Two { public static void main(String[] a) {} }\n".getBytes());
		Files.write(new File(src, "None.java").toPath(), "package p;\npublic class None {}\n".getBytes());
		List<File> folders = Arrays.asList(new File(dir, "src"));
		List<String> names = new ArrayList<String>();
		for(MainFinder.Main m : MainFinder.inProject(folders, null))
		{
			names.add(m.className);
		}
		assertEquals(Arrays.asList("p.One", "p.Two"), names);
		Map<File, String> unsaved = new HashMap<File, String>();
		unsaved.put(new File(src, "None.java"), "package p;\npublic class None { public static void main(String[] a) {} }\n");
		unsaved.put(new File(src, "Two.java"), "package p;\npublic class Two {}\n");
		names.clear();
		for(MainFinder.Main m : MainFinder.inProject(folders, unsaved))
		{
			names.add(m.className);
		}
		assertEquals(Arrays.asList("p.None", "p.One"), names);
		// unsaved text must not leak into the cache
		names.clear();
		for(MainFinder.Main m : MainFinder.inProject(folders, null))
		{
			names.add(m.className);
		}
		assertEquals(Arrays.asList("p.One", "p.Two"), names);
		assertTrue(MainFinder.hasMain(new File(src, "One.java")));
		assertFalse(MainFinder.hasMain(new File(src, "None.java")));
	}
}
