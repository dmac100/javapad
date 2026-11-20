package javapad.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import javapad.model.EclipseProject;
import org.junit.BeforeClass;
import org.junit.Test;

public class ImportFixerTest {
	static File file;
	static JavaAnalyzer analyzer;
	static TypeIndex index;

	@BeforeClass
	public static void setUp() throws Exception {
		File dir = Files.createTempDirectory("fix").toFile();
		new File(dir, "src/p").mkdirs();
		new File(dir, "src/q").mkdirs();
		Files.write(new File(dir, "src/q/Helper.java").toPath(), "package q;\npublic class Helper {}\n".getBytes());
		file = new File(dir, "src/p/A.java");
		EclipseProject project = EclipseProject.open(dir);
		analyzer = new JavaAnalyzer(project);
		index = TypeIndex.build(project.sourceFolders, project.libraries);
	}

	static ImportFixer.Plan plan(String text) {
		return ImportFixer.plan(text, analyzer.analyze(file, text), index);
	}

	@Test
	public void findsUnresolvedTypeNames() {
		String text = "package p;\nclass A {\n\tvoid f() {\n\t\tList<String> a = new ArrayList<>();\n\t\tCollections.sort(a);\n\t\tfoo.bar();\n\t}\n}\n";
		List<ImportFixer.Missing> missing = ImportFixer.unresolved(analyzer.analyze(file, text));
		StringBuilder names = new StringBuilder();
		for(ImportFixer.Missing m : missing) {
			names.append(m.name).append(' ');
		}
		assertEquals("List ArrayList Collections ", names.toString());
		assertEquals(4, missing.get(0).line);
	}

	@Test
	public void ranksCandidatesAndAutoAddsTheUnambiguousOnes() {
		String text = "package p;\nimport java.io.File;\nclass A {\n\tvoid f() {\n\t\tList<String> a = new ArrayList<>();\n\t\tCollections.sort(a);\n\t\tHelper h = null;\n\t}\n}\n";
		ImportFixer.Plan plan = plan(text);
		assertTrue(plan.added.contains("java.util.ArrayList"));
		assertTrue(plan.added.contains("java.util.Collections"));
		assertTrue(plan.added.contains("q.Helper"));
		assertEquals(Arrays.asList("java.io.File"), plan.removed);
		List<TypeIndex.Entry> lists = plan.choices.get("List");
		assertEquals("java.util.List", lists.get(0).fqn);
		boolean awt = false;
		for(TypeIndex.Entry e : lists) {
			awt |= e.fqn.equals("java.awt.List");
		}
		assertTrue(awt);
	}

	@Test
	public void keepsUsedImportsAndWildcards() {
		String text = "package p;\nimport java.util.*;\nimport java.io.File;\nclass A {\n\tFile f;\n\tList<String> l;\n}\n";
		ImportFixer.Plan plan = plan(text);
		assertEquals(Arrays.asList("java.util.*", "java.io.File"), plan.keep);
		assertTrue(plan.removed.isEmpty());
		assertTrue(plan.choices.isEmpty());
	}

	@Test
	public void unknownNamesAreReported() {
		ImportFixer.Plan plan = plan("package p;\nclass A {\n\tNoSuchTypeAnywhere x;\n}\n");
		assertEquals(Arrays.asList("NoSuchTypeAnywhere"), plan.notFound);
	}

	@Test
	public void namesMentionedOnlyInJavadocKeepTheirImport() {
		String text = "package p;\nimport java.io.File;\n/** See {@link File}. */\nclass A {}\n";
		assertTrue(plan(text).removed.isEmpty());
	}

	@Test
	public void organizeRewritesTheBlock() {
		String text = "package p;\nimport java.io.File;\nimport java.util.Map;\nclass A {\n\tMap<String, String> m;\n\tList<String> l;\n}\n";
		ImportFixer.Plan plan = plan(text);
		plan.keep.add("java.util.List");
		String result = ImportEditor.rewrite(text, plan.keep, plan.statics);
		assertEquals("package p;\nimport java.util.List;\nimport java.util.Map;\nclass A {\n\tMap<String, String> m;\n\tList<String> l;\n}\n", result);
	}
}
