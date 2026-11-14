package javapad.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
		String[] tokens = text.toLowerCase().trim().split("\\s+");
		List<String> best = new ArrayList<String>();
		List<String> good = new ArrayList<String>();
		List<String> rest = new ArrayList<String>();
		for(String label : commands.keySet())
		{
			String lower = label.toLowerCase();
			boolean all = true;
			for(String token : tokens)
			{
				if(!lower.contains(token))
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
			if(name.startsWith(tokens[0]))
			{
				best.add(label);
			}
			else if(name.contains(tokens[0]))
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

	static String namePart(String label)
	{
		int cut = Math.max(label.lastIndexOf(" > ") + 2, Math.max(label.lastIndexOf('/'), label.lastIndexOf(": ") + 1));
		return cut < 0 ? label : label.substring(cut + 1);
	}
}
