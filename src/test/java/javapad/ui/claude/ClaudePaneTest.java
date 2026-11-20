package javapad.ui.claude;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class ClaudePaneTest {
	@Test
	public void theCommandRunsClaudeInAutoModeInTheMachineWithThePath() {
		List<String> c = ClaudePane.command("/home/me/proj", "abc", false);
		assertEquals("multipass", c.get(0));
		assertEquals("exec", c.get(1));
		assertEquals("claude", c.get(2));
		assertTrue(c.contains("/home/me/proj"));
		int mode = c.indexOf("--permission-mode");
		assertEquals("auto", c.get(mode + 1));
		assertTrue(c.contains("-p"));
	}

	@Test
	public void theFirstPromptCreatesTheSessionAndLaterOnesResumeIt() {
		List<String> first = ClaudePane.command("/p", "abc", false);
		assertEquals("abc", first.get(first.indexOf("--session-id") + 1));
		assertFalse(first.contains("--resume"));
		List<String> later = ClaudePane.command("/p", "abc", true);
		assertEquals("abc", later.get(later.indexOf("--resume") + 1));
		assertFalse(later.contains("--session-id"));
	}

	@Test
	public void theMessageStartsWithTheProjectPath() {
		assertEquals("The project is at /p\n\nhello", ClaudePane.message("/p", "hello"));
	}

	@Test
	public void blankPromptsAreEmpty() {
		assertEquals("", ClaudePane.promptOf(""));
		assertEquals("", ClaudePane.promptOf("   \t\n  "));
		assertEquals("", ClaudePane.promptOf("\u00a0\u2003\r\n"));
		assertEquals("hi there", ClaudePane.promptOf("  hi there \n"));
	}
}
