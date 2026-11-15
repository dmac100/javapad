package javapad.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class ImportEditorTest
{
	static String add(String text, String fqn)
	{
		int dot = fqn.lastIndexOf('.');
		return ImportEditor.apply(text, ImportEditor.addImport(text, fqn, fqn.substring(0, dot)));
	}

	@Test
	public void addsAfterPackageWhenThereAreNoImports()
	{
		assertEquals("package p;\n\nimport java.util.List;\n\nclass A {}\n", add("package p;\n\nclass A {}\n", "java.util.List"));
		assertEquals("package p;\n\nimport java.util.List;\n\nclass A {}\n", add("package p;\nclass A {}\n", "java.util.List"));
		assertEquals("import java.util.List;\n\nclass A {}\n", add("class A {}\n", "java.util.List"));
	}

	@Test
	public void insertsInSortedPositionWithinAGroup()
	{
		String text = "package p;\n\nimport java.io.File;\nimport java.util.Map;\n\nclass A {}\n";
		assertEquals("package p;\n\nimport java.io.File;\nimport java.util.List;\nimport java.util.Map;\n\nclass A {}\n", add(text, "java.util.List"));
		assertEquals("package p;\n\nimport java.io.File;\nimport java.util.Map;\nimport java.util.Set;\n\nclass A {}\n", add(text, "java.util.Set"));
		assertEquals("package p;\n\nimport java.io.File;\nimport java.io.IOException;\nimport java.util.Map;\n\nclass A {}\n", add(text.replace("java.io.File", "java.io.File"), "java.io.IOException"));
	}

	@Test
	public void startsANewGroupWithABlankLine()
	{
		String text = "package p;\n\nimport java.util.List;\n\nclass A {}\n";
		assertEquals("package p;\n\nimport java.util.List;\n\nimport org.junit.Test;\n\nclass A {}\n", add(text, "org.junit.Test"));
		String mixed = "package p;\n\nimport org.junit.Test;\n\nclass A {}\n";
		assertEquals("package p;\n\nimport java.util.List;\n\nimport org.junit.Test;\n\nclass A {}\n", add(mixed, "java.util.List"));
	}

	@Test
	public void skipsWhatIsAlreadyVisible()
	{
		String text = "package p;\n\nimport java.util.*;\nimport java.io.File;\n\nclass A {}\n";
		assertNull(ImportEditor.addImport(text, "java.util.List", "java.util"));
		assertNull(ImportEditor.addImport(text, "java.io.File", "java.io"));
		assertNull(ImportEditor.addImport(text, "java.lang.String", "java.lang"));
		assertNull(ImportEditor.addImport(text, "p.Other", "p"));
		assertTrue(ImportEditor.isVisible(text, "java.util.Map", "java.util"));
		assertFalse(ImportEditor.isVisible(text, "java.awt.List", "java.awt"));
		assertFalse(ImportEditor.isVisible(text, "q.Other", "q"));
	}

	@Test
	public void nestedTypesAreImportedByTheirQualifiedName()
	{
		String text = "package p;\n\nclass A {}\n";
		assertEquals("package p;\n\nimport java.util.Map.Entry;\n\nclass A {}\n", ImportEditor.apply(text, ImportEditor.addImport(text, "java.util.Map.Entry", "java.util")));
	}

	@Test
	public void parsesPackageAndImports()
	{
		String text = "package a.b;\nimport java.util.List;\nimport static org.junit.Assert.*;\n";
		assertEquals("a.b", ImportEditor.packageOf(text));
		assertEquals(Arrays.asList("java.util.List"), ImportEditor.imports(text, false));
		assertEquals(Arrays.asList("org.junit.Assert.*"), ImportEditor.imports(text, true));
		assertEquals("", ImportEditor.packageOf("class A {}"));
	}

	@Test
	public void rewriteSortsGroupsAndPutsStaticsFirst()
	{
		String text = "package p;\n\nimport org.junit.Test;\nimport java.util.List;\nimport static org.junit.Assert.assertTrue;\nimport com.acme.Thing;\nimport java.io.File;\nimport zed.Z;\n\nclass A {}\n";
		String expected = "package p;\n\nimport static org.junit.Assert.assertTrue;\n\nimport java.io.File;\nimport java.util.List;\n\nimport org.junit.Test;\n\nimport com.acme.Thing;\n\nimport zed.Z;\n\nclass A {}\n";
		assertEquals(expected, ImportEditor.rewrite(text, ImportEditor.imports(text, false), ImportEditor.imports(text, true)));
	}

	@Test
	public void rewriteCanRemoveEverything()
	{
		String text = "package p;\n\nimport java.util.List;\n\nclass A {}\n";
		assertEquals("package p;\n\n\nclass A {}\n", ImportEditor.rewrite(text, Collections.<String>emptyList(), Collections.<String>emptyList()));
	}
}
