package javapad.run;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class TestFinder
{
	static final Pattern ABSTRACT = Pattern.compile("\\babstract\\s+class\\b");

	public static boolean isTestClass(String content)
	{
		if(!content.contains("org.junit") || !content.contains("@Test"))
		{
			return false;
		}
		return !ABSTRACT.matcher(content).find();
	}

	public static boolean isJupiter(String content)
	{
		return content.contains("org.junit.jupiter");
	}

	public static List<File> find(File target, List<File> sourceFolders)
	{
		List<File> result = new ArrayList<File>();
		if(target == null)
		{
			return result;
		}
		if(target.isFile())
		{
			if(target.getName().endsWith(".java") && isTestClass(read(target)))
			{
				result.add(target);
			}
			return result;
		}
		for(File src : sourceFolders)
		{
			if(inside(target, src))
			{
				walk(target, result);
			}
			else if(inside(src, target))
			{
				walk(src, result);
			}
		}
		return result;
	}

	public static List<File> findAll(List<File> sourceFolders)
	{
		List<File> result = new ArrayList<File>();
		for(File src : sourceFolders)
		{
			walk(src, result);
		}
		return result;
	}

	static boolean inside(File child, File parent)
	{
		String c = child.getAbsolutePath();
		String p = parent.getAbsolutePath();
		return c.equals(p) || c.startsWith(p + File.separator);
	}

	static void walk(File dir, List<File> out)
	{
		File[] children = dir.listFiles();
		if(children == null)
		{
			return;
		}
		Arrays.sort(children);
		for(File f : children)
		{
			if(f.isDirectory())
			{
				walk(f, out);
			}
			else if(f.getName().endsWith(".java") && isTestClass(read(f)))
			{
				if(!out.contains(f))
				{
					out.add(f);
				}
			}
		}
	}

	static String read(File f)
	{
		try
		{
			return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
		}
		catch(Exception e)
		{
			return "";
		}
	}
}
