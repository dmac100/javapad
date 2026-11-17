package javapad.git;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Runs the git command line in a folder and captures what it prints.
public class GitRunner
{
	public static GitResult run(File dir, String... args)
	{
		return run(dir, null, args);
	}

	// env: extra environment variables (may be null)
	public static GitResult run(File dir, Map<String, String> env, String... args)
	{
		List<String> command = new ArrayList<String>();
		command.add("git");
		for(String a : args)
		{
			command.add(a);
		}
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(dir);
		pb.environment().put("GIT_TERMINAL_PROMPT", "0");
		pb.environment().put("GIT_EDITOR", "true");
		pb.environment().put("LC_ALL", "C");
		if(env != null)
		{
			pb.environment().putAll(env);
		}
		pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
		try
		{
			Process p = pb.start();
			ByteArrayOutputStream err = new ByteArrayOutputStream();
			Thread errThread = new Thread(() -> copy(p.getErrorStream(), err), "git-err");
			errThread.start();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			copy(p.getInputStream(), out);
			int exit = p.waitFor();
			errThread.join();
			return new GitResult(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
		}
		catch(IOException e)
		{
			e.printStackTrace();
			return new GitResult(127, "", "git could not be started: " + e.getMessage());
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return new GitResult(130, "", "interrupted");
		}
	}

	static void copy(InputStream in, ByteArrayOutputStream to)
	{
		try
		{
			in.transferTo(to);
		}
		catch(IOException e)
		{
			// the process ended; whatever arrived is kept
		}
	}
}
