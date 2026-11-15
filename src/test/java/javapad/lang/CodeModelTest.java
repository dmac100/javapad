package javapad.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javapad.model.EclipseProject;
import org.junit.BeforeClass;
import org.junit.Test;

public class CodeModelTest
{
	static CodeModel model;

	static void write(File dir, String name, String text) throws Exception
	{
		File f = new File(dir, "src/p/" + name);
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), text.getBytes());
	}

	@BeforeClass
	public static void build() throws Exception
	{
		File dir = Files.createTempDirectory("cm").toFile();
		write(dir, "Shape.java", "package p;\npublic interface Shape {\n\tdouble area();\n\tdefault String describe() {\n\t\treturn \"shape \" + area();\n\t}\n}\n");
		write(dir, "Circle.java", "package p;\npublic class Circle implements Shape {\n\tpublic double area() {\n\t\treturn 3.0;\n\t}\n}\n");
		write(dir, "Square.java", "package p;\npublic class Square implements Shape {\n\tpublic double area() {\n\t\treturn 4.0;\n\t}\n\tpublic String toString() {\n\t\treturn describe();\n\t}\n}\n");
		write(dir, "Animal.java", "package p;\npublic abstract class Animal {\n\tabstract void speak();\n\tvoid greet() {\n\t\tspeak();\n\t}\n}\n");
		write(dir, "Dog.java", "package p;\npublic class Dog extends Animal {\n\tvoid speak() {\n\t}\n}\n");
		write(dir, "Puppy.java", "package p;\npublic class Puppy extends Dog {\n\tvoid speak() {\n\t\tsuper.speak();\n\t}\n}\n");
		write(dir, "Main.java", "package p;\npublic class Main {\n\tstatic int counter = helper();\n\tstatic int helper() {\n\t\treturn 1;\n\t}\n\tvoid run() {\n\t\tShape s = new Circle();\n\t\ts.area();\n\t\tnew Square().describe();\n\t\tShape anon = new Shape() {\n\t\t\tpublic double area() {\n\t\t\t\treturn 1;\n\t\t\t}\n\t\t};\n\t\tnew Dog().greet();\n\t}\n}\n");
		EclipseProject project = EclipseProject.open(dir);
		model = CodeModel.build(project);
	}

	static List<String> names(List<CodeModel.TypeInfo> list)
	{
		List<String> names = new ArrayList<String>();
		for(CodeModel.TypeInfo t : list)
		{
			names.add(t.display);
		}
		return names;
	}

	@Test
	public void subtypesAtAnyDepth()
	{
		List<String> shapes = names(model.allSubtypes("p.Shape"));
		assertTrue(shapes.toString(), shapes.contains("p.Circle"));
		assertTrue(shapes.contains("p.Square"));
		assertTrue(shapes.contains("<anonymous Shape>"));
		List<String> animals = names(model.allSubtypes("p.Animal"));
		assertTrue(animals.contains("p.Dog"));
		assertTrue(animals.contains("p.Puppy"));
	}

	@Test
	public void implementersOfInterfaceAndAbstractMethods()
	{
		List<String> area = new ArrayList<String>();
		for(CodeModel.MethodInfo m : model.implementers("p.Shape#area()"))
		{
			area.add(m.display);
		}
		assertEquals(3, area.size());
		assertTrue(area.toString(), area.contains("Circle.area()"));
		assertTrue(area.contains("Square.area()"));
		assertTrue(area.contains("<anonymous Shape>.area()"));
		List<String> speak = new ArrayList<String>();
		for(CodeModel.MethodInfo m : model.implementers("p.Animal#speak()"))
		{
			speak.add(m.display);
		}
		assertTrue(speak.contains("Dog.speak()"));
		assertTrue(speak.contains("Puppy.speak()"));
	}

	@Test
	public void objectMethodOverridesAreTracked()
	{
		CodeModel.MethodInfo toString = model.method("p.Square#toString()");
		assertNotNull(toString);
		assertTrue(toString.overrides.contains("java.lang.Object#toString()"));
	}

	@Test
	public void callersIncludeCallsThroughTheInterface()
	{
		List<String> callers = new ArrayList<String>();
		for(CodeModel.Call c : model.callersOf("p.Circle#area()"))
		{
			callers.add(c.callerKey);
		}
		assertTrue(callers.toString(), callers.contains("p.Main#run()"));
		assertTrue(callers.contains("p.Shape#describe()"));
	}

	@Test
	public void callersOfStaticHelperComeFromTheFieldInitializer()
	{
		List<CodeModel.Call> calls = model.callersOf("p.Main#helper()");
		assertEquals(1, calls.size());
		assertEquals("p.Main#<init>", calls.get(0).callerKey);
		assertEquals(3, calls.get(0).place.line);
	}

	@Test
	public void calleesOfAMethod()
	{
		List<String> targets = new ArrayList<String>();
		for(CodeModel.Call c : model.calleesOf("p.Main#run()"))
		{
			targets.add(c.targetKey);
		}
		assertTrue(targets.toString(), targets.contains("p.Circle#<init>()"));
		assertTrue(targets.contains("p.Shape#area()"));
		assertTrue(targets.contains("p.Shape#describe()"));
		assertTrue(targets.contains("p.Animal#greet()"));
	}

	@Test
	public void superCallsAreFound()
	{
		boolean found = false;
		for(CodeModel.Call c : model.callersOf("p.Dog#speak()"))
		{
			found |= c.callerKey.equals("p.Puppy#speak()");
		}
		assertTrue(found);
	}

	@Test
	public void constructorsReadAsNewExpressions()
	{
		CodeModel.MethodInfo any = null;
		for(CodeModel.Call c : model.calleesOf("p.Main#run()"))
		{
			CodeModel.MethodInfo m = model.method(c.targetKey);
			if(m != null && m.display.startsWith("new "))
			{
				any = m;
			}
		}
		assertTrue(any == null || any.display.matches("new [\\w<> ]+\\(.*\\)"));
	}

	@Test
	public void placesPointAtDeclarationNames()
	{
		CodeModel.MethodInfo area = model.method("p.Circle#area()");
		assertEquals(3, area.place.line);
		assertEquals("\tpublic double ".length() + 1, area.place.column);
		CodeModel.TypeInfo circle = model.type("p.Circle");
		assertEquals(2, circle.place.line);
	}
}
