package javapad.ui.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class OpenCodeStreamTest {
	@Test
	public void textAndReasoningBecomeAnswersAndThoughts() {
		List<ChatEvent> text = OpenCodeStream.parse(
			"{\"type\":\"text\",\"sessionID\":\"ses_1\",\"part\":{\"type\":\"text\",\"text\":\"Hello **there**\"}}");
		assertEquals(1, text.size());
		assertEquals(ChatEvent.ANSWER, text.get(0).kind);
		assertEquals("Hello **there**", text.get(0).text);
		List<ChatEvent> thought = OpenCodeStream.parse(
			"{\"type\":\"reasoning\",\"part\":{\"type\":\"reasoning\",\"text\":\"Let me look\"}}");
		assertEquals(ChatEvent.THOUGHT, thought.get(0).kind);
		assertEquals("Let me look", thought.get(0).text);
	}

	@Test
	public void toolsShowWhatTheyWorkedOn() {
		ChatEvent event = OpenCodeStream.parse(
			"{\"type\":\"tool_use\",\"part\":{\"type\":\"tool\",\"tool\":\"read\",\"state\":{\"status\":\"completed\",\"input\":{\"filePath\":\"src/A.java\"}}}}").get(0);
		assertEquals(ChatEvent.TOOL, event.kind);
		assertEquals("read: src/A.java", event.text);
		ChatEvent titled = OpenCodeStream.parse(
			"{\"type\":\"tool_use\",\"part\":{\"tool\":\"bash\",\"state\":{\"title\":\"List the files\",\"input\":{}}}}").get(0);
		assertEquals("bash: List the files", titled.text);
	}

	@Test
	public void errorsAreShown() {
		ChatEvent event = OpenCodeStream.parse(
			"{\"type\":\"error\",\"error\":{\"name\":\"ProviderAuthError\",\"data\":{\"message\":\"No credentials\"}}}").get(0);
		assertEquals(ChatEvent.ERROR, event.kind);
		assertEquals("No credentials", event.text);
	}

	@Test
	public void stepsAreIgnoredAndOtherLinesAreShownAsText() {
		assertTrue(OpenCodeStream.parse("{\"type\":\"step_start\",\"sessionID\":\"ses_1\"}").isEmpty());
		assertTrue(OpenCodeStream.parse("   ").isEmpty());
		ChatEvent plain = OpenCodeStream.parse("warning from the shell").get(0);
		assertEquals(ChatEvent.PLAIN, plain.kind);
		assertEquals("warning from the shell", plain.text);
	}

	@Test
	public void theSessionIdComesFromTheStream() {
		assertEquals("ses_1", OpenCodeStream.session("{\"type\":\"step_start\",\"sessionID\":\"ses_1\"}"));
		assertEquals("ses_2", OpenCodeStream.session("{\"type\":\"text\",\"sessionID\":\"ses_2\",\"part\":{}}"));
		assertEquals(null, OpenCodeStream.session("warning from the shell"));
	}
}
