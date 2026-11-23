package javapad.ui.chat;

import java.util.Map;

// One thing the chat shows: a thought, a tool the AI used, an answer, an error, the final result, or plain shell output.
// Both AIs' streams are read into these events, so the pane does not need to know which one answered.
public class ChatEvent {
	static final int THOUGHT = 0;
	static final int TOOL = 1;
	static final int ANSWER = 2;
	static final int ERROR = 3;
	// the final text of a run, which repeats the answer
	static final int RESULT = 4;
	// a line that was not JSON
	static final int PLAIN = 5;

	final int kind;
	final String text;

	ChatEvent(int kind, String text) {
		this.kind = kind;
		this.text = text;
	}

	// the keys of a tool's input that tell best what it works on, most telling first
	static final String[] DETAILS = {"file_path", "filePath", "path", "command", "pattern", "url", "query", "description", "prompt", "title"};
	static final int DETAIL_LENGTH = 100;

	// "Read: src/A.java": the tool and what it is used on
	static String toolSummary(String name, Map<String, Object> input) {
		String label = name == null ? "tool" : name;
		if(input != null) {
			for(String key : DETAILS) {
				String value = Json.string(input.get(key));
				if(value != null && !value.trim().isEmpty()) {
					String one = value.trim().replaceAll("\\s+", " ");
					return label + ": " + (one.length() > DETAIL_LENGTH ? one.substring(0, DETAIL_LENGTH) + "..." : one);
				}
			}
		}
		return label;
	}
}
