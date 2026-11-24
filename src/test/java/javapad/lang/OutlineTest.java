package javapad.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class OutlineTest {
	static final String SOURCE = "package p;\n"
		+ "\n"
		+ "import java.util.List;\n"
		+ "\n"
		+ "public class Foo<T> {\n"
		+ "\tint count = 1;\n"
		+ "\tString name;\n"
		+ "\n"
		+ "\tpublic Foo() {\n"
		+ "\t}\n"
		+ "\n"
		+ "\tpublic Foo(int count) {\n"
		+ "\t\tthis.count = count;\n"
		+ "\t}\n"
		+ "\n"
		+ "\tpublic <U> List<U> map(U value) {\n"
		+ "\t\treturn null;\n"
		+ "\t}\n"
		+ "\n"
		+ "\tstatic class Inner {\n"
		+ "\t\tvoid run() {\n"
		+ "\t\t}\n"
		+ "\t}\n"
		+ "\n"
		+ "\tenum Colour {\n"
		+ "\t\tRED, GREEN;\n"
		+ "\t\tint code;\n"
		+ "\t}\n"
		+ "\n"
		+ "\trecord Point(int x, int y) {\n"
		+ "\t\tint sum() {\n"
		+ "\t\t\treturn x + y;\n"
		+ "\t\t}\n"
		+ "\t}\n"
		+ "}\n";

	@Test
	public void theTopLevelTypeComesFirstWithItsMembersInSourceOrder() {
		List<Outline.Node> roots = Outline.parse(SOURCE);
		assertEquals(1, roots.size());
		Outline.Node type = roots.get(0);
		assertEquals(Outline.Kind.TYPE, type.kind);
		assertEquals("Foo", type.name);
		assertEquals("Foo<T>  (class)", type.label);
		assertEquals(5, type.line);
		assertEquals(14, type.column);
		assertEquals(0, type.depth);
		assertEquals("count", type.children.get(0).name);
		assertEquals("name", type.children.get(1).name);
		assertEquals("Foo", type.children.get(2).name);
		assertEquals(Outline.Kind.CONSTRUCTOR, type.children.get(2).kind);
		assertEquals("map", type.children.get(4).name);
		assertEquals(Outline.Kind.METHOD, type.children.get(4).kind);
		assertEquals("Inner", type.children.get(5).name);
		assertEquals("Colour", type.children.get(6).name);
		assertEquals("Point", type.children.get(7).name);
	}

	@Test
	public void methodsShowTheirParametersAndReturnType() {
		List<Outline.Node> methods = Outline.methods(Outline.parse(SOURCE));
		assertEquals(5, methods.size());
		Outline.Node map = methods.get(2);
		assertEquals("map", map.name);
		assertEquals("<U>(U) : List<U>", map.label.substring(map.name.length()));
		assertEquals("List<U>", map.type);
		assertEquals(16, map.line);
		assertEquals(21, map.column);
	}

	@Test
	public void constructorsAreNamedAfterTheType() {
		List<Outline.Node> methods = Outline.methods(Outline.parse(SOURCE));
		assertEquals(Outline.Kind.CONSTRUCTOR, methods.get(0).kind);
		assertEquals("Foo()", methods.get(0).label);
		assertEquals("Foo(int)", methods.get(1).label);
		assertEquals(9, methods.get(0).line);
		assertEquals(12, methods.get(1).line);
	}

	@Test
	public void fieldsAndEnumConstantsHaveLabelsOfTheirOwn() {
		Outline.Node type = Outline.parse(SOURCE).get(0);
		assertEquals("count : int", type.children.get(0).label);
		Outline.Node colour = type.children.get(6);
		assertEquals(Outline.Kind.ENUM_CONSTANT, colour.children.get(0).kind);
		assertEquals("RED", colour.children.get(0).label);
		assertEquals("code : int", colour.children.get(2).label);
	}

	@Test
	public void recordsShowTheirComponentsAndConstructor() {
		Outline.Node point = Outline.parse(SOURCE).get(0).children.get(7);
		assertEquals("record", point.type);
		assertEquals("x : int", point.children.get(0).label);
		assertEquals("y : int", point.children.get(1).label);
		assertEquals("sum() : int", point.children.get(2).label);
	}

	@Test
	public void theInnermostDeclarationAroundALineIsFound() {
		List<Outline.Node> roots = Outline.parse(SOURCE);
		assertEquals("count", Outline.atLine(roots, 6).name);
		assertEquals("map", Outline.atLine(roots, 18).name);
		assertEquals("run", Outline.atLine(roots, 21).name);
		assertEquals("Foo", Outline.atLine(roots, 5).name);
		assertNull(Outline.atLine(roots, 3));
	}

	@Test
	public void searchRanksNamesThatStartWithTheTypedWordFirst() {
		List<Outline.Node> methods = Outline.methods(Outline.parse(SOURCE));
		List<Outline.Node> found = Outline.search(methods, "ma", 10);
		assertEquals("map", found.get(0).name);
		assertFalse(Outline.search(methods, "nothing", 10).iterator().hasNext());
		assertEquals(methods.size(), Outline.search(methods, "", 100).size());
	}

	@Test
	public void membersOfNestedTypesAreQualifiedInThePopup() {
		List<Outline.Node> methods = Outline.methods(Outline.parse(SOURCE));
		Outline.Node run = null;
		for(Outline.Node node : methods) {
			if(node.name.equals("run")) {
				run = node;
			}
		}
		assertNotNull(run);
		assertEquals("Inner.run() : void", Outline.label(run, true));
		assertEquals("run() : void", Outline.label(run, false));
	}

	@Test
	public void brokenSourcesHaveNoOutline() {
		assertTrue(Outline.parse("class {").isEmpty());
		assertTrue(Outline.parse("").isEmpty());
	}
}
