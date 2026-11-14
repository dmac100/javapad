package javapad.run;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class MainFinder
{
	static final Pattern MAIN = Pattern.compile("static\\s+void\\s+main\\s*\\(\\s*(final\\s+)?String\\s*(\\[\\s*\\]\\s*\\w+|\\.\\.\\.\\s*\\w+|\\w+\\s*\\[\\s*\\])\\s*\\)");

	public static boolean hasMain(String content)
	{
		return MAIN.matcher(content).find();
	}

	public static boolean hasMain(File file)
	{
		return file.getName().endsWith(".java") && hasMain(read(file));
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
			else if(hasMain(f) && !out.contains(f))
			{
				out.add(f);
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
