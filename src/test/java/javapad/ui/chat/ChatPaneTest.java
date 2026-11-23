package javapad.ui.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class ChatPaneTest {
	@Test
	public void theCommandRunsClaudeInAutoModeInTheMachineWithThePath() {
		List<String> c = ChatPane.command("/home/me/proj", "abc", false);
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
		List<String> first = ChatPane.command("/p", "abc", false);
		assertEquals("abc", first.get(first.indexOf("--session-id") + 1));
		assertFalse(first.contains("--resume"));
		List<String> later = ChatPane.command("/p", "abc", true);
		assertEquals("abc", later.get(later.indexOf("--resume") + 1));
		assertFalse(later.contains("--session-id"));
	}

	@Test
	public void theMessageStartsWithTheProjectPath() {
		assertEquals("The project is at /p\n\nhello", ChatPane.message("/p", "hello"));
	}

	@Test
	public void theHomeDirectoryIsWrittenAsTilde() {
		String home = System.getProperty("user.home");
		assertEquals("~", ChatPane.projectPath(new java.io.File(home)));
		assertEquals("~/proj", ChatPane.projectPath(new java.io.File(home, "proj")));
		assertEquals("/tmp/proj", ChatPane.projectPath(new java.io.File("/tmp/proj")));
	}

	@Test
	public void openCodeRunsInTheSameMachineWithJsonOutput() {
		List<String> c = ChatPane.command("/home/me/proj", "abc", false, Assistant.OPENCODE);
		assertEquals("multipass", c.get(0));
		assertEquals("claude", c.get(2));
		assertTrue(c.stream().anyMatch(s -> s.contains("exec \"$HOME/.opencode/bin/opencode\" run")));
		assertTrue(c.stream().anyMatch(s -> s.contains("$HOME/.opencode/bin/opencode")));
		assertFalse(c.stream().anyMatch(s -> s.contains("command -v opencode")));
		assertFalse(c.stream().anyMatch(s -> s.contains(".bashrc")));
		assertTrue(c.contains("/home/me/proj"));
		assertTrue(c.contains("--auto"));
		assertEquals("json", c.get(c.indexOf("--format") + 1));
		assertFalse(c.contains("-p"));
		assertFalse(c.contains("--session"));
	}

	@Test
	public void aLaterOpenCodePromptContinuesItsSession() {
		List<String> c = ChatPane.command("/p", "ses_12", true, Assistant.OPENCODE);
		assertEquals("ses_12", c.get(c.indexOf("--session") + 1));
	}

	@Test
	public void theDropdownLabelsChooseTheAI() {
		assertEquals(Assistant.CLAUDE, Assistant.withLabel("Claude Code"));
		assertEquals(Assistant.OPENCODE, Assistant.withLabel("OpenCode"));
		assertEquals(Assistant.CLAUDE, Assistant.withLabel("unknown"));
	}

	@Test
	public void blankPromptsAreEmpty() {
		assertEquals("", ChatPane.promptOf(""));
		assertEquals("", ChatPane.promptOf("   \t\n  "));
		assertEquals("", ChatPane.promptOf("\u00a0\u2003\r\n"));
		assertEquals("hi there", ChatPane.promptOf("  hi there \n"));
	}
}
