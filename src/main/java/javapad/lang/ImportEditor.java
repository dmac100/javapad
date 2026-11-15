package javapad.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImportEditor
{
	static final Pattern IMPORT = Pattern.compile("(?m)^[ \\t]*import[ \\t]+(static[ \\t]+)?([\\w.]+(?:\\.\\*)?)[ \\t]*;[^\\n]*\\n?");
	static final Pattern PACKAGE = Pattern.compile("(?m)^[ \\t]*package[ \\t]+([\\w.]+)[ \\t]*;[^\\n]*\\n?");
	static final String[] ORDER = {"java", "javax", "org", "com"};

	public static class Edit
	{
		public final int offset;
		public final int length;
		public final String text;

		public Edit(int offset, int length, String text)
		{
			this.offset = offset;
			this.length = length;
			this.text = text;
		}
	}

	static class Item
	{
		int start;
		int end;
		String name;
		boolean statics;
	}

	public static String packageOf(String text)
	{
		Matcher m = PACKAGE.matcher(text);
		return m.find() ? m.group(1) : "";
	}

	static List<Item> items(String text)
	{
		List<Item> list = new ArrayList<Item>();
		Matcher m = IMPORT.matcher(text);
		while(m.find())
		{
			Item item = new Item();
			item.start = m.start();
			item.end = m.end();
			item.name = m.group(2);
			item.statics = m.group(1) != null;
			list.add(item);
		}
		return list;
	}

	public static List<String> imports(String text, boolean statics)
	{
		List<String> names = new ArrayList<String>();
		for(Item item : items(text))
		{
			if(item.statics == statics)
			{
				names.add(item.name);
			}
		}
		return names;
	}

	// Whether the simple name of a type can already be used in this file without an import.
	public static boolean isVisible(String text, String fqn, String pkg)
	{
		if(pkg.equals("java.lang") && fqn.equals("java.lang." + Names.simple(fqn)))
		{
			return true;
		}
		if(!pkg.isEmpty() && pkg.equals(packageOf(text)) && fqn.equals(pkg + "." + Names.simple(fqn)))
		{
			return true;
		}
		List<String> imports = imports(text, false);
		if(imports.contains(fqn))
		{
			return true;
		}
		int dot = fqn.lastIndexOf('.');
		return dot > 0 && imports.contains(fqn.substring(0, dot) + ".*");
	}

	static int group(String name)
	{
		int dot = name.indexOf('.');
		String first = dot < 0 ? name : name.substring(0, dot);
		for(int i = 0; i < ORDER.length; i++)
		{
			if(ORDER[i].equals(first))
			{
				return i;
			}
		}
		return ORDER.length;
	}

	static int compare(String a, String b)
	{
		int g = Integer.compare(group(a), group(b));
		return g != 0 ? g : a.compareTo(b);
	}

	// The text change that adds "import fqn;" in the right place, or null if it is already visible.
	public static Edit addImport(String text, String fqn, String pkg)
	{
		if(isVisible(text, fqn, pkg))
		{
			return null;
		}
		String line = "import " + fqn + ";\n";
		List<Item> regular = new ArrayList<Item>();
		Item lastStatic = null;
		for(Item item : items(text))
		{
			if(item.statics)
			{
				lastStatic = item;
			}
			else
			{
				regular.add(item);
			}
		}
		int g = group(fqn);
		if(!regular.isEmpty())
		{
			int k = 0;
			while(k < regular.size() && compare(regular.get(k).name, fqn) < 0)
			{
				k++;
			}
			Item prev = k > 0 ? regular.get(k - 1) : null;
			Item next = k < regular.size() ? regular.get(k) : null;
			int offset = next != null ? next.start : regular.get(regular.size() - 1).end;
			String prefix = "";
			String suffix = "";
			if(next == null && !text.substring(0, offset).endsWith("\n"))
			{
				prefix = "\n";
			}
			if(prev != null && group(prev.name) != g && (next == null || !text.substring(prev.end, next.start).contains("\n")))
			{
				prefix += "\n";
			}
			if(next != null && group(next.name) != g)
			{
				suffix = "\n";
			}
			return new Edit(offset, 0, prefix + line + suffix);
		}
		if(lastStatic != null)
		{
			return new Edit(lastStatic.end, 0, "\n" + line);
		}
		Matcher pk = PACKAGE.matcher(text);
		if(pk.find())
		{
			int offset = pk.end();
			String before = text.substring(0, offset).endsWith("\n") ? "" : "\n";
			String after = text.startsWith("\n", offset) || offset >= text.length() ? "" : "\n";
			return new Edit(offset, 0, before + "\n" + line + after);
		}
		return new Edit(0, 0, line + "\n");
	}

	public static String apply(String text, Edit edit)
	{
		return edit == null ? text : text.substring(0, edit.offset) + edit.text + text.substring(edit.offset + edit.length);
	}

	// Replaces all import statements with the given ones, sorted and grouped like Eclipse (statics first).
	public static String rewrite(String text, Collection<String> regular, Collection<String> statics)
	{
		StringBuilder block = new StringBuilder();
		TreeSet<String> staticSet = new TreeSet<String>(statics);
		for(String s : staticSet)
		{
			block.append("import static ").append(s).append(";\n");
		}
		TreeSet<String> sorted = new TreeSet<String>((a, b) -> compare(a, b));
		sorted.addAll(regular);
		int previous = -1;
		for(String name : sorted)
		{
			int g = group(name);
			if(block.length() > 0 && previous != g)
			{
				block.append('\n');
			}
			block.append("import ").append(name).append(";\n");
			previous = g;
		}
		List<Item> existing = items(text);
		if(existing.isEmpty())
		{
			if(block.length() == 0)
			{
				return text;
			}
			Matcher pk = PACKAGE.matcher(text);
			if(pk.find())
			{
				String before = text.substring(0, pk.end());
				return before + (before.endsWith("\n") ? "" : "\n") + "\n" + block + (text.startsWith("\n", pk.end()) ? "" : "\n") + text.substring(pk.end());
			}
			return block + "\n" + text;
		}
		int start = existing.get(0).start;
		int end = existing.get(existing.size() - 1).end;
		return text.substring(0, start) + block + text.substring(end);
	}
}
