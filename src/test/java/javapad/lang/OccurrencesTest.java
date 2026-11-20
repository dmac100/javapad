package javapad.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javapad.model.EclipseProject;
import org.junit.BeforeClass;
import org.junit.Test;

public class OccurrencesTest {
	static JavaAnalyzer analyzer;
	static File file;

	static final String SRC = "package p;\n"
		+ "public class Main {\n"
		+ "\tint count;\n"
		+ "\tvoid a() {\n"
		+ "\t\tint count = 1;\n"
		+ "\t\tcount++;\n"
		+ "\t\tthis.count = count + 2;\n"
		+ "\t\tSystem.out.println(\"count \" + count); // count\n"
		+ "\t}\n"
		+ "\tvoid b() {\n"
		+ "\t\tcount = 5;\n"
		+ "\t\ta();\n"
		+ "\t\tthis.a();\n"
		+ "\t}\n"
		+ "\tMain() {\n"
		+ "\t}\n"
		+ "\tstatic Main make() {\n"
		+ "\t\treturn new Main();\n"
		+ "\t}\n"
		+ "}\n";

	@BeforeClass
	public static void setUp() throws Exception {
		File dir = Files.createTempDirectory("occ").toFile();
		file = new File(dir, "src/p/Main.java");
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), SRC.getBytes());
		analyzer = new JavaAnalyzer(EclipseProject.open(dir));
	}

	// the offset of the n-th (1-based) occurrence of a string
	static int at(String s, int n) {
		int i = -1;
		for(int k = 0; k < n; k++) {
			i = SRC.indexOf(s, i + 1);
		}
		return i;
	}

	static List<Integer> starts(List<int[]> ranges) {
		List<Integer> list = new ArrayList<Integer>();
		for(int[] r : ranges) {
			list.add(r[0]);
		}
		return list;
	}

	@Test
	public void aLocalVariableIsNotTheFieldWithTheSameName() {
		// the declaration "int count = 1" and its uses in a(), but not the field or the strings and comments
		int decl = at("count = 1", 1);
		List<Integer> starts = starts(Occurrences.find(analyzer, file, SRC, decl, 5));
		assertEquals(4, starts.size());
		assertTrue(starts.contains(decl));
		assertTrue(starts.contains(at("count++", 1)));
		assertTrue(starts.contains(at("count + 2", 1)));
		assertTrue(starts.contains(at("count);", 1)));
		assertTrue(!starts.contains(at("int count;", 1) + 4));
	}

	@Test
	public void aFieldIsFoundThroughThisAndPlainUse() {
		int field = at("count;", 1);
		List<Integer> starts = starts(Occurrences.find(analyzer, file, SRC, field, 5));
		assertEquals(3, starts.size());
		assertTrue(starts.contains(field));
		assertTrue(starts.contains(at("this.count", 1) + 5));
		assertTrue(starts.contains(at("count = 5", 1)));
	}

	@Test
	public void aMethodIsFoundByCallsWithAndWithoutThis() {
		int decl = at("void a()", 1) + 5;
		List<Integer> starts = starts(Occurrences.find(analyzer, file, SRC, decl, 1));
		assertEquals(3, starts.size());
		assertTrue(starts.contains(decl));
		assertTrue(starts.contains(at("a();", 2)));
		assertTrue(starts.contains(at("this.a()", 1) + 5));
	}

	@Test
	public void aClassIncludesItsConstructorAndUses() {
		int decl = at("class Main", 1) + 6;
		List<Integer> starts = starts(Occurrences.find(analyzer, file, SRC, decl, 4));
		assertTrue(starts.toString(), starts.contains(decl));
		assertTrue(starts.contains(at("Main() {", 1)));
		assertTrue(starts.contains(at("static Main", 1) + 7));
		assertTrue(starts.contains(at("new Main", 1) + 4));
	}

	@Test
	public void unresolvedNamesFallBackToWholeWordsInCode() {
		String text = "class A {\n\tvoid f() {\n\t\tmissing(1);\n\t\tmissingly(2); // missing\n\t\tString s = \"missing\";\n\t\tmissing(3);\n\t}\n}\n";
		int first = text.indexOf("missing");
		List<int[]> ranges = Occurrences.find(analyzer, file, text, first, 7);
		assertEquals(2, ranges.size());
		assertEquals(first, ranges.get(0)[0]);
		assertEquals(first + 7, ranges.get(0)[1]);
	}
}
