package javapad.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TestFinderTest {
	@Test
	public void detectsJunit4AndJunit5() {
		assertTrue(TestFinder.isTestClass("import org.junit.Test;\nclass A { @Test public void a() {} }"));
		assertTrue(TestFinder.isTestClass("import org.junit.jupiter.api.Test;\nclass A { @Test void a() {} }"));
		assertTrue(TestFinder.isJupiter("import org.junit.jupiter.api.Test;"));
		assertFalse(TestFinder.isJupiter("import org.junit.Test;"));
	}

	@Test
	public void ignoresHelpersAndAbstractClasses() {
		assertFalse(TestFinder.isTestClass("import org.junit.Assert;\nclass Helper {}"));
		assertFalse(TestFinder.isTestClass("import org.junit.Test;\nabstract class Base { @Test public void a() {} }"));
		assertFalse(TestFinder.isTestClass("class Plain { }"));
	}

	@Test
	public void stringsAndCommentsDoNotMakeATestClass() {
		assertFalse(TestFinder.isTestClass("class Finder {\n// org.junit.Test @Test\nString s = \"@org.junit.Test\";\n}"));
		assertTrue(TestFinder.isTestClass("import org.junit.Test;\nclass FinderTest {\n\t@Test public void x() {\n\t\tString s = \"abstract class A\";\n\t}\n}"));
		assertFalse(TestFinder.isJupiter("class A { String s = \"org.junit.jupiter\"; }"));
	}

	@Test
	public void findsImplementationNamesFromTest() {
		List<String> names = TestFinder.testedClasses("p.FooTest", "package p;\nclass FooTest { }");
		assertEquals(Arrays.asList("p.Foo"), names);
		names = TestFinder.testedClasses("p.FooTests", "package p;\nclass FooTests extends Bar { }");
		assertTrue(names.contains("p.Foo"));
		assertTrue(names.contains("p.Bar"));
	}

	@Test
	public void findsTestsForImplementation() throws Exception {
		File src = Files.createTempDirectory("testfinder-src").toFile();
		File test = Files.createTempDirectory("testfinder-test").toFile();
		try {
			File impl = new File(src, "p/Foo.java");
			impl.getParentFile().mkdirs();
			Files.write(impl.toPath(), "package p; class Foo {}".getBytes());
			File t1 = new File(test, "p/FooTest.java");
			t1.getParentFile().mkdirs();
			Files.write(t1.toPath(), "package p; import org.junit.Test; class FooTest { @Test public void x(){} }".getBytes());
			File t2 = new File(src, "p/FooTests.java");
			Files.write(t2.toPath(), "package p; import org.junit.Test; class FooTests { @Test public void x(){} }".getBytes());
			List<File> found = TestFinder.testsFor("p.Foo", Arrays.asList(src, test));
			assertEquals(Arrays.asList(t2, t1), found);
		} finally {
			delete(src);
			delete(test);
		}
	}

	@Test
	public void findsTestsDespiteStringsMentioningAbstractClasses() throws Exception {
		File src = Files.createTempDirectory("testfinder-src").toFile();
		File test = Files.createTempDirectory("testfinder-test").toFile();
		try {
			File impl = new File(src, "p/MainFinder.java");
			impl.getParentFile().mkdirs();
			Files.write(impl.toPath(), "package p; class MainFinder {}".getBytes());
			File t = new File(test, "p/MainFinderTest.java");
			t.getParentFile().mkdirs();
			Files.write(t.toPath(), ("package p;\nimport org.junit.Test;\nclass MainFinderTest {\n"
				+ "\t@Test public void x() { assertTrue(names(\"abstract class A\").isEmpty()); }\n}\n").getBytes());
			List<File> folders = Arrays.asList(src, test);
			assertEquals(Arrays.asList(t), TestFinder.testsFor("p.MainFinder", folders));
			assertEquals(Arrays.asList(t), TestFinder.find(new File(test, "p"), folders));
			assertEquals(Arrays.asList("p.MainFinder"), TestFinder.testedClasses("p.MainFinderTest", new String(Files.readAllBytes(t.toPath()))));
		} finally {
			delete(src);
			delete(test);
		}
	}

	static void delete(File f) {
		File[] children = f.listFiles();
		if(children != null) {
			for(File c : children) {
				delete(c);
			}
		}
		f.delete();
	}
}
