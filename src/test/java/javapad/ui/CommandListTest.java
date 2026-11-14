package javapad.ui;

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
	public void runsTheChosenCommand()
	{
		CommandList list = new CommandList();
		int[] count = {0};
		list.add("A > B", () -> count[0]++);
		list.run("A > B");
		list.run("missing");
		assertEquals(1, count[0]);
	}
}
