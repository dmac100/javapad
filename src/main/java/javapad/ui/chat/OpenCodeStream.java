package javapad.ui.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Reads the newline-delimited JSON of "opencode run --format json" into the events the chat shows.
public class OpenCodeStream {
	// the id of the session that opencode chose for the first prompt; later prompts continue it with --session
	static String session(String line) {
		try {
			return Json.string(Json.object(Json.parse(line)).get("sessionID"));
		} catch(IllegalArgumentException e) {
			return null;
		}
	}

	// the events of one line; anything that is not one of opencode's JSON events is shell output and is shown as it is
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
		Map<String, Object> part = Json.object(message.get("part"));
		if("text".equals(type) || "reasoning".equals(type)) {
			String text = part == null ? null : Json.string(part.get("text"));
			if(text != null && !text.isEmpty()) {
				events.add(new ChatEvent("text".equals(type) ? ChatEvent.ANSWER : ChatEvent.THOUGHT, text));
			}
		} else if("tool_use".equals(type)) {
			events.add(new ChatEvent(ChatEvent.TOOL, tool(part)));
		} else if("error".equals(type)) {
			String text = error(message);
			events.add(new ChatEvent(ChatEvent.ERROR, text == null ? "opencode error" : text));
		}
		return events;
	}

	// "read: src/A.java": the tool and the file, command or title it worked on
	static String tool(Map<String, Object> part) {
		String name = part == null ? null : Json.string(part.get("tool"));
		Map<String, Object> state = part == null ? null : Json.object(part.get("state"));
		Map<String, Object> input = state == null ? null : Json.object(state.get("input"));
		Map<String, Object> details = input == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(input);
		String title = state == null ? null : Json.string(state.get("title"));
		if(title != null && !details.containsKey("title")) {
			details.put("title", title);
		}
		return ChatEvent.toolSummary(name, details);
	}

	static String error(Map<String, Object> message) {
		Map<String, Object> error = Json.object(message.get("error"));
		if(error == null) {
			return null;
		}
		Map<String, Object> data = Json.object(error.get("data"));
		String text = data == null ? null : Json.string(data.get("message"));
		if(text == null) {
			text = Json.string(error.get("message"));
		}
		return text == null ? Json.string(error.get("name")) : text;
	}
}
