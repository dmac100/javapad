package javapad.run;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
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

	static final Pattern TEST_METHOD = Pattern.compile("@(?:org\\.junit\\.(?:jupiter\\.api\\.)?)?(?:Test|ParameterizedTest|RepeatedTest|TestFactory)\\b"
		+ "(?:\\s*\\([^)]*\\))?(?:\\s*@[\\w.]+(?:\\s*\\([^)]*\\))?)*\\s+(?:(?:public|protected|private|static|final|synchronized)\\s+)*(?:<[^>]*>\\s+)?"
		+ "[\\w<>\\[\\],.?]+\\s+(\\w+)\\s*\\(");

	// The name of the @Test method whose declaration or body contains the offset, or null.
	public static String methodAt(String content, int offset)
	{
		Matcher m = TEST_METHOD.matcher(content);
		String found = null;
		int bodyEnd = -1;
		while(m.find() && m.start() <= offset)
		{
			found = m.group(1);
			bodyEnd = bodyEnd(content, m.end());
		}
		return found != null && offset <= bodyEnd ? found : null;
	}

	// The 1-based line of the declaration of the named @Test method, or -1.
	public static int methodLine(String content, String method)
	{
		Matcher m = TEST_METHOD.matcher(content);
		while(m.find())
		{
			if(m.group(1).equals(method))
			{
				int line = 1;
				for(int i = 0; i < m.start(1); i++)
				{
					if(content.charAt(i) == '\n')
					{
						line++;
					}
				}
				return line;
			}
		}
		return -1;
	}

	static int bodyEnd(String content, int from)
	{
		int open = content.indexOf('{', from);
		int semi = content.indexOf(';', from);
		if(open < 0 || (semi >= 0 && semi < open))
		{
			return semi < 0 ? content.length() : semi;
		}
		int depth = 0;
		for(int i = open; i < content.length(); i++)
		{
			char c = content.charAt(i);
			if(c == '{')
			{
				depth++;
			}
			else if(c == '}')
			{
				depth--;
				if(depth == 0)
				{
					return i;
				}
			}
		}
		return content.length();
	}

	// A test name from a result ("adds", "adds()") that can be selected as a method; null for parameterized/dynamic names.
	public static String selectableMethod(String name)
	{
		String n = name.endsWith("()") ? name.substring(0, name.length() - 2) : name;
		return n.matches("[\\w$]+") && !n.equals("<class>") ? n : null;
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
