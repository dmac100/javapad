package javapad.ui.dialog;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

public class CommandListTest
{
	@Test
	public void matchesAllTokensAndRanksNamesFirst()
	{
		CommandList list = new CommandList();
		list.add("File > Save", () -> {});
		list.add("Run > Build and Run", () -> {});
		list.add("Open: src/app/Builder.java", () -> {});
		list.add("Open: docs/build/notes.txt", () -> {});
		assertEquals(Arrays.asList("Run > Build and Run", "Open: src/app/Builder.java", "Open: docs/build/notes.txt"), list.find("build", 10));
		assertEquals(Arrays.asList("Open: src/app/Builder.java"), list.find("src builder", 10));
		assertEquals(Arrays.asList("File > Save"), list.find("SAVE", 10));
		assertEquals(2, list.find("", 2).size());
	}

	@Test
	public void starIsAWildcard()
	{
		CommandList list = new CommandList();
		list.add("src/app/FooTest.java", () -> {});
		list.add("src/app/Foo.java", () -> {});
		list.add("docs/notes.txt", () -> {});
		assertEquals(Arrays.asList("src/app/FooTest.java"), list.find("foo*test", 10));
		assertEquals(Arrays.asList("docs/notes.txt"), list.find("*.txt", 10));
		assertEquals(Arrays.asList("src/app/FooTest.java", "src/app/Foo.java"), list.find("src/*/foo*", 10));
		assertEquals(Arrays.asList("docs/notes.txt"), list.find("n?tes", 10));
	}

	@Test
	public void runsTheChosenCommand()
	{
		CommandList list = new CommandList();
		int[] count = {0};
		list.add("A > B", () -> count[0]++);
		list.run("A > B");
		list.run("missing");
		assertEquals(1, count[0]);
	}

	@Test
	public void filesShowTheirNameFirst()
	{
		CommandList list = new CommandList();
		list.add("Bar.java - src/foo", () -> {});
		list.add("Foo.java - src/bar", () -> {});
		assertEquals(Arrays.asList("Foo.java - src/bar", "Bar.java - src/foo"), list.find("foo", 10));
	}
}
