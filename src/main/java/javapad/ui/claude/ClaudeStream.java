package javapad.ui.claude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Reads the lines of "claude -p --output-format stream-json --verbose" and turns them into what the chat shows: Claude's thinking, the tools it uses, its text, and errors.
public class ClaudeStream
{
	static final int THOUGHT = 0;
	static final int TOOL = 1;
	static final int ANSWER = 2;
	static final int ERROR = 3;
	// the final text of a run, which repeats the answer
	static final int RESULT = 4;
	// a line that was not JSON
	static final int PLAIN = 5;

	static class Event
	{
		final int kind;
		final String text;

		Event(int kind, String text)
		{
			this.kind = kind;
			this.text = text;
		}
	}

	// the keys of a tool's input that tell best what it works on, most telling first
	static final String[] DETAILS = {"file_path", "path", "command", "pattern", "url", "query", "description", "prompt"};
	static final int DETAIL_LENGTH = 100;

	// the events of one line; a line that is not JSON is plain text from somewhere else, such as a shell, and is shown as it is
	static List<Event> parse(String line)
	{
		List<Event> events = new ArrayList<Event>();
		if(line.trim().isEmpty())
		{
			return events;
		}
		Map<String, Object> message;
		try
		{
			message = Json.object(Json.parse(line));
		}
		catch(IllegalArgumentException e)
		{
			message = null;
		}
		if(message == null)
		{
			events.add(new Event(PLAIN, line));
			return events;
		}
		String type = Json.string(message.get("type"));
		if("assistant".equals(type))
		{
			assistant(Json.object(message.get("message")), events);
		}
		else if("result".equals(type))
		{
			String result = Json.string(message.get("result"));
			boolean error = Boolean.TRUE.equals(message.get("is_error"));
			if(error)
			{
				events.add(new Event(ERROR, result == null ? String.valueOf(message.get("subtype")) : result));
			}
			else if(result != null)
			{
				events.add(new Event(RESULT, result));
			}
		}
		return events;
	}

	static void assistant(Map<String, Object> message, List<Event> events)
	{
		List<Object> content = message == null ? null : Json.array(message.get("content"));
		if(content == null)
		{
			return;
		}
		for(Object o : content)
		{
			Map<String, Object> block = Json.object(o);
			String type = block == null ? null : Json.string(block.get("type"));
			if("text".equals(type) && Json.string(block.get("text")) != null)
			{
				events.add(new Event(ANSWER, Json.string(block.get("text"))));
			}
			else if("thinking".equals(type) && Json.string(block.get("thinking")) != null)
			{
				events.add(new Event(THOUGHT, Json.string(block.get("thinking"))));
			}
			else if("tool_use".equals(type))
			{
				events.add(new Event(TOOL, toolSummary(Json.string(block.get("name")), Json.object(block.get("input")))));
			}
		}
	}

	// "Read: src/A.java": the tool and what it is used on
	static String toolSummary(String name, Map<String, Object> input)
	{
		String label = name == null ? "tool" : name;
		if(input != null)
		{
			for(String key : DETAILS)
			{
				String value = Json.string(input.get(key));
				if(value != null && !value.trim().isEmpty())
				{
					String one = value.trim().replaceAll("\\s+", " ");
					return label + ": " + (one.length() > DETAIL_LENGTH ? one.substring(0, DETAIL_LENGTH) + "..." : one);
				}
			}
		}
		return label;
	}
}
