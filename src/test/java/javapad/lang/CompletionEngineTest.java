package javapad.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javapad.model.EclipseProject;
import org.junit.BeforeClass;
import org.junit.Test;

public class CompletionEngineTest
{
	static File dir;
	static EclipseProject project;
	static CompletionEngine engine;
	static File file;

	@BeforeClass
	public static void setUp() throws Exception
	{
		dir = Files.createTempDirectory("ce").toFile();
		new File(dir, "src/p").mkdirs();
		Files.write(new File(dir, "src/p/Base.java").toPath(), ("package p;\npublic class Base {\n\tprotected int baseField;\n\tpublic void baseMethod(String s) {}\n\tpublic static int util() { return 1; }\n}\n").getBytes());
		Files.write(new File(dir, "src/p/Other.java").toPath(), ("package p;\npublic class Other {\n\tpublic String name;\n\tpublic int count(int a, String b) { return 0; }\n\tpublic static final int MAX = 3;\n}\n").getBytes());
		file = new File(dir, "src/p/A.java");
		project = EclipseProject.open(dir);
		TypeIndex index = TypeIndex.build(project.sourceFolders, project.libraries);
		engine = new CompletionEngine(new JavaAnalyzer(project), () -> index);
	}

	// source with | marking the caret
	static List<String> names(String source)
	{
		int caret = source.indexOf('|');
		String text = source.substring(0, caret) + source.substring(caret + 1);
		CompletionEngine.Completions c = engine.propose(file, text, caret);
		assertNotNull(c);
		List<String> names = new ArrayList<String>();
		for(Proposal p : c.proposals)
		{
			names.add(p.name);
		}
		return names;
	}

	static String wrap(String body)
	{
		return "package p;\nimport java.util.List;\npublic class A extends Base {\n\tint own;\n\tvoid run(String param, List<String> list) {\n\t\tOther other = new Other();\n\t\t" + body + "\n\t}\n}\n";
	}

	@Test
	public void membersOfJdkTypeAfterDot()
	{
		List<String> n = names(wrap("param.|"));
		assertTrue(n.contains("length"));
		assertTrue(n.contains("substring"));
		assertTrue(n.contains("toUpperCase"));
		assertFalse(n.contains("own"));
	}

	@Test
	public void membersFilteredByPrefix()
	{
		List<String> n = names(wrap("param.sub|"));
		assertTrue(n.contains("substring"));
		assertTrue(n.contains("subSequence"));
		assertFalse(n.contains("length"));
	}

	@Test
	public void genericTypeMembers()
	{
		List<String> n = names(wrap("list.|"));
		assertTrue(n.contains("add"));
		assertTrue(n.contains("stream"));
	}

	@Test
	public void projectTypeMembersInstanceVersusStatic()
	{
		List<String> instance = names(wrap("other.|"));
		assertTrue(instance.contains("name"));
		assertTrue(instance.contains("count"));
		List<String> statics = names(wrap("Other.|"));
		assertTrue(statics.contains("MAX"));
		assertFalse(statics.contains("count"));
		assertFalse(statics.contains("name"));
	}

	@Test
	public void methodLabelsShowSignatures()
	{
		int caret = wrap("other.cou|").indexOf('|');
		String src = wrap("other.cou|");
		CompletionEngine.Completions c = engine.propose(file, src.replace("|", ""), caret);
		Proposal count = null;
		for(Proposal p : c.proposals)
		{
			if(p.name.equals("count"))
			{
				count = p;
			}
		}
		assertNotNull(count);
		assertEquals("count(int a, String b) : int", count.label);
		assertTrue(count.hasParams);
	}

	@Test
	public void localsParametersAndInheritedMembers()
	{
		List<String> n = names(wrap("par|"));
		assertTrue(n.contains("param"));
		List<String> all = names(wrap("b|"));
		assertTrue(all.contains("baseField"));
		assertTrue(all.contains("baseMethod"));
		assertTrue(all.contains("boolean"));
		assertTrue(names(wrap("ow|")).contains("own"));
		assertTrue(names(wrap("oth|")).contains("other"));
		List<String> first = names(wrap("o|"));
		assertTrue(first.indexOf("other") < first.indexOf("Object"));
		assertTrue(first.indexOf("own") < first.indexOf("Object"));
	}

	@Test
	public void typesComeFromTheIndexWithImportInfo()
	{
		String src = wrap("ArrayLi|");
		CompletionEngine.Completions c = engine.propose(file, src.replace("|", ""), src.indexOf('|'));
		Proposal list = null;
		for(Proposal p : c.proposals)
		{
			if(p.name.equals("ArrayList"))
			{
				list = p;
			}
		}
		assertNotNull(list);
		assertEquals("java.util.ArrayList", list.importType);
		String visible = wrap("Lis|");
		c = engine.propose(file, visible.replace("|", ""), visible.indexOf('|'));
		for(Proposal p : c.proposals)
		{
			if(p.name.equals("List") && p.importType == null)
			{
				return;
			}
		}
		throw new AssertionError("List is already imported and should need no import");
	}

	@Test
	public void importLinesCompletePackagesAndClasses()
	{
		String src = "package p;\nimport java.ut|\n";
		CompletionEngine.Completions c = engine.propose(file, src.replace("|", ""), src.indexOf('|'));
		boolean util = false;
		for(Proposal p : c.proposals)
		{
			util |= p.name.equals("util") && p.kind == Proposal.PACKAGE;
		}
		assertTrue(util);
		src = "package p;\nimport java.util.Arr|\n";
		c = engine.propose(file, src.replace("|", ""), src.indexOf('|'));
		boolean arrays = false;
		for(Proposal p : c.proposals)
		{
			arrays |= p.name.equals("Arrays") && p.kind == Proposal.TYPE;
		}
		assertTrue(arrays);
	}

	@Test
	public void afterNewOnlyTypesAndAnnotationsOnlyAnnotations()
	{
		List<String> n = names(wrap("Object o = new Arr|"));
		assertTrue(n.contains("ArrayList"));
		assertFalse(n.contains("param"));
		List<String> a = names(wrap("@Overr|\n\t\tint x;"));
		assertTrue(a.contains("Override"));
		assertFalse(a.contains("Object"));
	}

	@Test
	public void qualifiedPackageCompletion()
	{
		List<String> n = names(wrap("java.util.Arr|"));
		assertTrue(n.contains("ArrayList"));
		List<String> pk = names(wrap("java.ut|"));
		assertTrue(pk.contains("util"));
	}

	@Test
	public void arrayMembers()
	{
		List<String> n = names(wrap("String[] arr = null;\n\t\tarr.|"));
		assertTrue(n.contains("length"));
		assertTrue(n.contains("clone") || n.contains("getClass"));
	}

	@Test
	public void incompleteCodeAfterCaretStillWorks()
	{
		List<String> n = names(wrap("param.|\n\t\tint x = 1;"));
		assertTrue(n.contains("length"));
	}
}
