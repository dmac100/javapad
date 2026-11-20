package javapad.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ClaudeStreamTest
{
	@Test
	public void jsonReadsObjectsArraysAndEscapes()
	{
		Map<String, Object> o = Json.object(Json.parse("{\"a\": [1, 2.5, true, null], \"b\": \"x\\n\\u0041\\\"\", \"c\": {}}"));
		assertEquals(4, Json.array(o.get("a")).size());
		assertEquals("x\nA\"", o.get("b"));
		assertTrue(Json.object(o.get("c")).isEmpty());
	}

	@Test(expected = IllegalArgumentException.class)
	public void jsonRejectsText()
	{
		Json.parse("not json");
	}

	@Test
	public void assistantMessagesGiveThoughtsToolsAndText()
	{
		String line = "{\"type\":\"assistant\",\"message\":{\"content\":["
			+ "{\"type\":\"thinking\",\"thinking\":\"Let me look.\"},"
			+ "{\"type\":\"tool_use\",\"name\":\"Read\",\"input\":{\"file_path\":\"src/A.java\"}},"
			+ "{\"type\":\"text\",\"text\":\"Done.\"}]}}";
		List<ClaudeStream.Event> events = ClaudeStream.parse(line);
		assertEquals(3, events.size());
		assertEquals(ClaudeStream.THOUGHT, events.get(0).kind);
		assertEquals("Let me look.", events.get(0).text);
		assertEquals(ClaudeStream.TOOL, events.get(1).kind);
		assertEquals("Read: src/A.java", events.get(1).text);
		assertEquals(ClaudeStream.ANSWER, events.get(2).kind);
	}

	@Test
	public void resultsAndErrors()
	{
		assertEquals(ClaudeStream.RESULT, ClaudeStream.parse("{\"type\":\"result\",\"is_error\":false,\"result\":\"ok\"}").get(0).kind);
		ClaudeStream.Event error = ClaudeStream.parse("{\"type\":\"result\",\"is_error\":true,\"result\":\"bad\"}").get(0);
		assertEquals(ClaudeStream.ERROR, error.kind);
		assertEquals("bad", error.text);
	}

	@Test
	public void otherLinesAreIgnoredOrShownAsText()
	{
		assertTrue(ClaudeStream.parse("{\"type\":\"system\",\"subtype\":\"init\"}").isEmpty());
		assertTrue(ClaudeStream.parse("   ").isEmpty());
		ClaudeStream.Event plain = ClaudeStream.parse("warning from the shell").get(0);
		assertEquals(ClaudeStream.PLAIN, plain.kind);
		assertEquals("warning from the shell", plain.text);
	}

	@Test
	public void longToolDetailsAreCut()
	{
		String text = ClaudeStream.toolSummary("Bash", Json.object(Json.parse("{\"command\":\"" + "x".repeat(300) + "\"}")));
		assertTrue(text.length() < 120);
		assertTrue(text.endsWith("..."));
	}
}
