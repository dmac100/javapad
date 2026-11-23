package javapad.ui.chat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChatMarkdownTest {
	static String bold(ChatMarkdown.Result r) {
		StringBuilder sb = new StringBuilder();
		for(int[] run : r.bold) {
			sb.append('[').append(r.text, run[0], run[0] + run[1]).append(']');
		}
		return sb.toString();
	}

	@Test
	public void bulletsAndBold() {
		ChatMarkdown.Result r = ChatMarkdown.render("- **aaa**: first\n* second\n  - nested\nplain **b** and **c**");
		assertEquals("\u2022 aaa: first\n\u2022 second\n  \u25e6 nested\nplain b and c", r.text);
		assertEquals("[aaa][b][c]", bold(r));
	}

	@Test
	public void anUnfinishedBoldStaysAsItIs() {
		ChatMarkdown.Result r = ChatMarkdown.render("a **bol");
		assertEquals("a **bol", r.text);
		assertEquals("", bold(r));
	}

	@Test
	public void boldAtTheStartOfALineIsNotABullet() {
		ChatMarkdown.Result r = ChatMarkdown.render("**Note** this\n-5 is not a bullet");
		assertEquals("Note this\n-5 is not a bullet", r.text);
		assertEquals("[Note]", bold(r));
	}

	@Test
	public void emptyBoldMarksAreKept() {
		assertEquals("a **** b", ChatMarkdown.render("a **** b").text);
	}
}
