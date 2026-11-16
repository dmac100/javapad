package javapad.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CommandList
{
	private final Map<String, Runnable> commands = new LinkedHashMap<String, Runnable>();

	public void add(String label, Runnable action)
	{
		commands.put(label, action);
	}

	public void addAll(CommandList other)
	{
		commands.putAll(other.commands);
	}

	public void run(String label)
	{
		Runnable action = commands.get(label);
		if(action != null)
		{
			action.run();
		}
	}

	public List<String> find(String text, int limit)
	{
		String[] words = text.toLowerCase().trim().split("\\s+");
		Pattern[] tokens = new Pattern[words.length];
		for(int i = 0; i < words.length; i++)
		{
			tokens[i] = token(words[i]);
		}
		List<String> best = new ArrayList<String>();
		List<String> good = new ArrayList<String>();
		List<String> rest = new ArrayList<String>();
		for(String label : commands.keySet())
		{
			String lower = label.toLowerCase();
			boolean all = true;
			for(Pattern token : tokens)
			{
				if(!token.matcher(lower).find())
				{
					all = false;
					break;
				}
			}
			if(!all)
			{
				continue;
			}
			String name = namePart(lower);
			if(tokens[0].matcher(name).lookingAt())
			{
				best.add(label);
			}
			else if(tokens[0].matcher(name).find())
			{
				good.add(label);
			}
			else
			{
				rest.add(label);
			}
		}
		List<String> result = new ArrayList<String>(best);
		result.addAll(good);
		result.addAll(rest);
		return result.size() > limit ? new ArrayList<String>(result.subList(0, limit)) : result;
	}

	// a typed word as a pattern: '*' stands for any characters, '?' for one, everything else is literal
	static Pattern token(String word)
	{
		StringBuilder sb = new StringBuilder();
		for(char c : word.toCharArray())
		{
			if(c == '*')
			{
				sb.append(".*");
			}
			else if(c == '?')
			{
				sb.append('.');
			}
			else
			{
				sb.append(Pattern.quote(String.valueOf(c)));
			}
		}
		return Pattern.compile(sb.toString());
	}

	static String namePart(String label)
	{
		int cut = Math.max(label.lastIndexOf(" > ") + 2, Math.max(label.lastIndexOf('/'), label.lastIndexOf(": ") + 1));
		return cut < 0 ? label : label.substring(cut + 1);
	}
}
