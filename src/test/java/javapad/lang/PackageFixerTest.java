package javapad.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.Arrays;
import org.junit.Test;

public class PackageFixerTest {
	@Test
	public void expectedFollowsFolder() {
		File src = new File("/p/src");
		assertEquals("a.b", PackageFixer.expected(new File("/p/src/a/b/X.java"), Arrays.asList(src)));
		assertEquals("", PackageFixer.expected(new File("/p/src/X.java"), Arrays.asList(src)));
		assertNull(PackageFixer.expected(new File("/q/X.java"), Arrays.asList(src)));
	}

	@Test
	public void declaredReadsPackage() {
		assertEquals("a.b", PackageFixer.declared("// c\npackage a . b ;\nclass X {}"));
		assertEquals("", PackageFixer.declared("class X {}"));
	}

	@Test
	public void replacesWrongPackage() {
		assertEquals("package x.y;\n\nclass X {}", PackageFixer.fix("package a;\n\nclass X {}", "x.y"));
		assertEquals("/* h */\npackage x;\nclass X {}", PackageFixer.fix("/* h */\npackage a.b;\nclass X {}", "x"));
	}

	@Test
	public void addsMissingPackage() {
		assertEquals("package x;\n\nclass X {}", PackageFixer.fix("class X {}", "x"));
		assertEquals("// c\npackage x;\n\nclass X {}", PackageFixer.fix("// c\nclass X {}", "x"));
	}

	@Test
	public void removesPackageForDefault() {
		assertEquals("class X {}", PackageFixer.fix("package a;\n\nclass X {}", ""));
	}

	@Test
	public void unchangedWhenCorrect() {
		String t = "package a;\nclass X {}";
		assertEquals(t, PackageFixer.fix(t, "a"));
	}
}
