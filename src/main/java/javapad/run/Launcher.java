package javapad.run;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javapad.model.ClasspathParser;
import javapad.model.EclipseProject;

public class Launcher
{
	private final EclipseProject project;
	private final OutputListener listener;
	private Process process;

	public Launcher(EclipseProject project, OutputListener listener)
	{
		this.project = project;
		this.listener = listener;
	}

	public static List<String> splitArgs(String text)
	{
		List<String> result = new ArrayList<String>();
		StringBuilder cur = new StringBuilder();
		boolean quoted = false;
		boolean any = false;
		for(int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if(c == '"')
			{
				quoted = !quoted;
				any = true;
			}
			else if(Character.isWhitespace(c) && !quoted)
			{
				if(any)
				{
					result.add(cur.toString());
					cur.setLength(0);
					any = false;
				}
			}
			else
			{
				cur.append(c);
				any = true;
			}
		}
		if(any)
		{
			result.add(cur.toString());
		}
		return result;
	}

	public List<String> mainCommand(String className, String vmArgs, String args)
	{
		List<String> cmd = new ArrayList<String>();
		cmd.add(javaExecutable());
		cmd.addAll(splitArgs(vmArgs));
		cmd.add("-cp");
		cmd.add(project.classpathString());
		cmd.add(className);
		cmd.addAll(splitArgs(args));
		return cmd;
	}

	public List<String> junitCommand(List<String> classNames, boolean jupiter, File reportDir)
	{
		List<String> cmd = new ArrayList<String>();
		cmd.add(javaExecutable());
		cmd.add("-cp");
		cmd.add(junitClasspath(jupiter));
		if(jupiter)
		{
			cmd.add("org.junit.platform.console.ConsoleLauncher");
			cmd.add("execute");
			cmd.add("--details=tree");
			cmd.add("--reports-dir=" + new File(reportDir, "jupiter").getAbsolutePath());
			for(String c : classNames)
			{
				cmd.add("--select-class=" + c);
			}
		}
		else
		{
			cmd.add("javapad.runner.JUnit4Runner");
			cmd.add(new File(reportDir, "junit4.xml").getAbsolutePath());
			cmd.addAll(classNames);
		}
		return cmd;
	}

	static File runnerLocation()
	{
		try
		{
			return new File(javapad.runner.JUnit4Runner.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		}
		catch(Exception e)
		{
			return new File("bin");
		}
	}

	String junitClasspath(boolean jupiter)
	{
		List<File> extra = new ArrayList<File>();
		boolean hasJunit4 = false;
		for(File lib : project.libraries)
		{
			if(lib.getName().startsWith("junit-4"))
			{
				hasJunit4 = true;
			}
		}
		if(jupiter)
		{
			extra.add(ClasspathParser.bundled("junit-platform-console-standalone-6.1.3.jar"));
		}
		else if(!hasJunit4)
		{
			extra.add(ClasspathParser.bundled("junit-4.13.2.jar"));
			extra.add(ClasspathParser.bundled("hamcrest-core-1.3.jar"));
		}
		if(!jupiter)
		{
			extra.add(runnerLocation());
		}
		StringBuilder sb = new StringBuilder(project.classpathString());
		for(File f : extra)
		{
			sb.append(File.pathSeparator);
			sb.append(f.getAbsolutePath());
		}
		return sb.toString();
	}

	public static String javaExecutable()
	{
		return new File(System.getProperty("java.home"), "bin/java").getPath();
	}

	public void start(List<String> command)
	{
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(project.root);
		try
		{
			process = pb.start();
		}
		catch(IOException e)
		{
			listener.output("Cannot start: " + e.getMessage() + "\n", true);
			listener.finished(-1);
			return;
		}
		listener.output("> " + String.join(" ", command) + "\n", false);
		pump(process.getInputStream(), false);
		Thread err = pump(process.getErrorStream(), true);
		Thread waiter = new Thread(() ->
		{
			int code = -1;
			try
			{
				code = process.waitFor();
				err.join(2000);
			}
			catch(InterruptedException e)
			{
			}
			listener.finished(code);
		}, "launcher-wait");
		waiter.setDaemon(true);
		waiter.start();
	}

	Thread pump(InputStream in, boolean error)
	{
		Thread t = new Thread(() ->
		{
			try
			{
				Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
				char[] buf = new char[4096];
				StringBuilder held = new StringBuilder();
				int n = r.read(buf);
				while(n >= 0)
				{
					held.append(buf, 0, n);
					int nl = held.lastIndexOf("\n");
					if(nl >= 0)
					{
						listener.output(held.substring(0, nl + 1), error);
						held.delete(0, nl + 1);
					}
					if(held.length() > 0 && !r.ready())
					{
						Thread.sleep(40);
						if(!r.ready())
						{
							listener.output(held.toString(), error);
							held.setLength(0);
						}
					}
					n = r.read(buf);
				}
				if(held.length() > 0)
				{
					listener.output(held.toString(), error);
				}
			}
			catch(IOException | InterruptedException e)
			{
			}
		}, "launcher-pump");
		t.setDaemon(true);
		t.start();
		return t;
	}

	public void sendInput(String text)
	{
		if(process == null || !process.isAlive())
		{
			return;
		}
		try
		{
			OutputStream out = process.getOutputStream();
			out.write(text.getBytes(StandardCharsets.UTF_8));
			out.flush();
		}
		catch(IOException e)
		{
		}
	}

	public boolean isRunning()
	{
		return process != null && process.isAlive();
	}

	public void stop()
	{
		if(process != null)
		{
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
		}
	}
}
