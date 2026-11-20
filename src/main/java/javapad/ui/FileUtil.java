package javapad.ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FileUtil
{
	// the file's text, or "" when it cannot be read
	public static String contentOf(File f)
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

	public static void deleteRecursive(File f)
	{
		File[] children = f.listFiles();
		if(children != null)
		{
			for(File c : children)
			{
				deleteRecursive(c);
			}
		}
		f.delete();
	}
}
