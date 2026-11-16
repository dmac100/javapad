package javapad.model;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// The rules of the .gitignore files that apply to a folder: those of its parent folders up to the repository root, and its own.
// Walk down a tree with child(dir), which adds the .gitignore found there.
public class GitIgnore
{
	static class Rule
	{
		String base;
		Pattern pattern;
		boolean negate;
		boolean dirOnly;
	}

	private final List<Rule> rules;

	GitIgnore(List<Rule> rules)
	{
		this.rules = rules;
	}

	// The rules for dir, including the .gitignore files in the folders above it up to the one that holds .git.
	public static GitIgnore of(File dir)
	{
		List<File> chain = new ArrayList<File>();
		File f = dir.getAbsoluteFile();
		File top = null;
		for(File p = f; p != null; p = p.getParentFile())
		{
			chain.add(0, p);
			if(new File(p, ".git").exists())
			{
				top = p;
				break;
			}
		}
		if(top == null)
		{
			chain.clear();
			chain.add(f);
		}
		GitIgnore result = new GitIgnore(new ArrayList<Rule>());
		for(File p : chain)
		{
			result = result.child(p);
		}
		return result;
	}

	// These rules plus the ones in dir/.gitignore.
	public GitIgnore child(File dir)
	{
		File file = new File(dir, ".gitignore");
		if(!file.isFile())
		{
			return this;
		}
		List<String> lines;
		try
		{
			lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
		}
		catch(Exception e)
		{
			return this;
		}
		return child(dir.getAbsolutePath(), lines);
	}

	GitIgnore child(String base, List<String> lines)
	{
		List<Rule> list = new ArrayList<Rule>(rules);
		for(String line : lines)
		{
			Rule r = parse(base, line);
			if(r != null)
			{
				list.add(r);
			}
		}
		return new GitIgnore(list);
	}

	static Rule parse(String base, String line)
	{
		String s = line.replaceAll("(?<!\\\\)\\s+$", "");
		if(s.isEmpty() || s.startsWith("#"))
		{
			return null;
		}
		Rule r = new Rule();
		r.base = base.replace(File.separatorChar, '/');
		if(s.startsWith("!"))
		{
			r.negate = true;
			s = s.substring(1);
		}
		else if(s.startsWith("\\!") || s.startsWith("\\#"))
		{
			s = s.substring(1);
		}
		if(s.endsWith("/"))
		{
			r.dirOnly = true;
			s = s.substring(0, s.length() - 1);
		}
		if(s.isEmpty())
		{
			return null;
		}
		boolean anchored = s.indexOf('/') >= 0;
		if(s.startsWith("/"))
		{
			s = s.substring(1);
		}
		String regex = glob(s);
		r.pattern = Pattern.compile(anchored ? regex : "(?:.*/)?" + regex);
		return r;
	}

	static String glob(String s)
	{
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while(i < s.length())
		{
			char c = s.charAt(i);
			if(c == '*')
			{
				if(i + 1 < s.length() && s.charAt(i + 1) == '*')
				{
					boolean slashAfter = i + 2 < s.length() && s.charAt(i + 2) == '/';
					if(slashAfter)
					{
						sb.append("(?:.*/)?");
						i += 3;
					}
					else
					{
						sb.append(".*");
						i += 2;
					}
					continue;
				}
				sb.append("[^/]*");
			}
			else if(c == '?')
			{
				sb.append("[^/]");
			}
			else if(c == '[')
			{
				int end = s.indexOf(']', i + 2);
				if(end < 0)
				{
					sb.append("\\[");
				}
				else
				{
					String set = s.substring(i + 1, end);
					if(set.startsWith("!"))
					{
						set = "^" + set.substring(1);
					}
					sb.append('[').append(set.replace("\\", "\\\\")).append(']');
					i = end;
				}
			}
			else if(c == '\\' && i + 1 < s.length())
			{
				i++;
				sb.append(Pattern.quote(String.valueOf(s.charAt(i))));
			}
			else
			{
				sb.append(Pattern.quote(String.valueOf(c)));
			}
			i++;
		}
		return sb.toString();
	}

	// Whether git would ignore the file or folder (the last matching rule wins).
	public boolean ignored(File f, boolean directory)
	{
		String path = f.getAbsolutePath().replace(File.separatorChar, '/');
		boolean result = false;
		for(Rule r : rules)
		{
			if(r.dirOnly && !directory)
			{
				continue;
			}
			if(!path.startsWith(r.base + "/"))
			{
				continue;
			}
			if(r.pattern.matcher(path.substring(r.base.length() + 1)).matches())
			{
				result = !r.negate;
			}
		}
		return result;
	}
}
