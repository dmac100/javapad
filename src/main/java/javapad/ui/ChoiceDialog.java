package javapad.ui;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.widgets.Shell;

// Pick one entry from a fixed list, filtering as you type. Returns null when cancelled.
public class ChoiceDialog
{
	public static String choose(Shell parent, String title, List<String> items)
	{
		return dialog(parent, title, items).open();
	}

	public static CommandDialog dialog(Shell parent, String title, List<String> items)
	{
		return new CommandDialog(parent, text -> filter(items, text), title);
	}

	static List<String> filter(List<String> items, String text)
	{
		String[] tokens = text.toLowerCase().trim().split("\\s+");
		List<String> result = new ArrayList<String>();
		for(String item : items)
		{
			String lower = item.toLowerCase();
			boolean all = true;
			for(String token : tokens)
			{
				if(!lower.contains(token))
				{
					all = false;
					break;
				}
			}
			if(all)
			{
				result.add(item);
			}
		}
		return result;
	}
}
