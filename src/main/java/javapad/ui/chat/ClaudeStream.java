package javapad.ui.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Reads the lines of "claude -p --output-format stream-json --verbose" into the events the chat shows.
public class ClaudeStream {
	// the events of one line; a line that is not JSON is plain text from somewhere else, such as a shell, and is shown as it is
	static List<ChatEvent> parse(String line) {
		List<ChatEvent> events = new ArrayList<ChatEvent>();
		if(line.trim().isEmpty()) {
			return events;
		}
		Map<String, Object> message;
		try {
			message = Json.object(Json.parse(line));
		} catch(IllegalArgumentException e) {
			message = null;
		}
		if(message == null) {
			events.add(new ChatEvent(ChatEvent.PLAIN, line));
			return events;
		}
		String type = Json.string(message.get("type"));
		if("assistant".equals(type)) {
			assistant(Json.object(message.get("message")), events);
		} else if("result".equals(type)) {
			String result = Json.string(message.get("result"));
			boolean error = Boolean.TRUE.equals(message.get("is_error"));
			if(error) {
				events.add(new ChatEvent(ChatEvent.ERROR, result == null ? String.valueOf(message.get("subtype")) : result));
			} else if(result != null) {
				events.add(new ChatEvent(ChatEvent.RESULT, result));
			}
		}
		return events;
	}

	static void assistant(Map<String, Object> message, List<ChatEvent> events) {
		List<Object> content = message == null ? null : Json.array(message.get("content"));
		if(content == null) {
			return;
		}
		for(Object o : content) {
			Map<String, Object> block = Json.object(o);
			String type = block == null ? null : Json.string(block.get("type"));
			if("text".equals(type) && Json.string(block.get("text")) != null) {
				events.add(new ChatEvent(ChatEvent.ANSWER, Json.string(block.get("text"))));
			} else if("thinking".equals(type) && Json.string(block.get("thinking")) != null) {
				events.add(new ChatEvent(ChatEvent.THOUGHT, Json.string(block.get("thinking"))));
			} else if("tool_use".equals(type)) {
				events.add(new ChatEvent(ChatEvent.TOOL, ChatEvent.toolSummary(Json.string(block.get("name")), Json.object(block.get("input")))));
			}
		}
	}
}
