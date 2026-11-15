package javapad.runner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.internal.TextListener;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class JUnit4Runner
{
	static class Case
	{
		String className;
		String name;
		long start;
		long nanos;
		Failure failure;
		boolean skipped;
		boolean started;
	}

	private static final List<Case> cases = new ArrayList<Case>();
	private static final Map<String, Case> byKey = new java.util.HashMap<String, Case>();

	public static void main(String[] args) throws Exception
	{
		File report = new File(args[0]);
		List<Request> requests = new ArrayList<Request>();
		for(int i = 1; i < args.length; i++)
		{
			int hash = args[i].indexOf('#');
			if(hash < 0)
			{
				requests.add(Request.aClass(Class.forName(args[i])));
			}
			else
			{
				requests.add(Request.method(Class.forName(args[i].substring(0, hash)), args[i].substring(hash + 1)));
			}
		}
		JUnitCore core = new JUnitCore();
		core.addListener(new TextListener(System.out));
		core.addListener(new RunListener()
		{
			public void testStarted(Description d)
			{
				Case c = caseFor(d);
				c.start = System.nanoTime();
				c.started = true;
				LiveLog.event("RUN", c.className, c.name);
			}

			public void testFinished(Description d)
			{
				Case c = caseFor(d);
				c.nanos = System.nanoTime() - c.start;
				live(c);
			}

			public void testFailure(Failure f)
			{
				Case c = caseFor(f.getDescription());
				c.failure = f;
				if(!c.started)
				{
					// e.g. a class that cannot be constructed: there is no start/finish pair
					live(c);
				}
			}

			public void testAssumptionFailure(Failure f)
			{
				caseFor(f.getDescription()).skipped = true;
			}

			public void testIgnored(Description d)
			{
				Case c = caseFor(d);
				c.skipped = true;
				live(c);
			}
		});
		boolean ok = true;
		for(Request request : requests)
		{
			ok &= core.run(request).wasSuccessful();
		}
		write(report);
		System.out.flush();
		System.exit(ok ? 0 : 1);
	}

	static void live(Case c)
	{
		String status = c.failure != null ? (c.failure.getException() instanceof AssertionError ? "F" : "E") : c.skipped ? "K" : "P";
		String message = c.failure == null || c.failure.getMessage() == null ? "" : c.failure.getMessage();
		LiveLog.event("END", c.className, c.name, status, String.valueOf(c.nanos / 1e9), message);
	}

	static Case caseFor(Description d)
	{
		String cls = d.getClassName();
		String name = d.getMethodName() == null ? "<class>" : d.getMethodName();
		String key = cls + "#" + name;
		Case c = byKey.get(key);
		if(c == null)
		{
			c = new Case();
			c.className = cls;
			c.name = name;
			byKey.put(key, c);
			cases.add(c);
		}
		return c;
	}

	static void write(File report) throws Exception
	{
		report.getParentFile().mkdirs();
		Writer w = new OutputStreamWriter(new FileOutputStream(report), StandardCharsets.UTF_8);
		w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuite name=\"junit4\" tests=\"" + cases.size() + "\">\n");
		for(Case c : cases)
		{
			w.write("<testcase classname=\"" + esc(c.className) + "\" name=\"" + esc(c.name) + "\" time=\"" + (c.nanos / 1e9) + "\">");
			if(c.failure != null)
			{
				boolean assertion = c.failure.getException() instanceof AssertionError;
				String tag = assertion ? "failure" : "error";
				String message = c.failure.getMessage() == null ? "" : c.failure.getMessage();
				w.write("<" + tag + " message=\"" + esc(message) + "\" type=\"" + esc(c.failure.getException().getClass().getName()) + "\">");
				w.write(esc(c.failure.getTrace()));
				w.write("</" + tag + ">");
			}
			else if(c.skipped)
			{
				w.write("<skipped/>");
			}
			w.write("</testcase>\n");
		}
		w.write("</testsuite>\n");
		w.close();
	}

	static String esc(String s)
	{
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < s.length(); i++)
		{
			char ch = s.charAt(i);
			if(ch == '&')
			{
				sb.append("&amp;");
			}
			else if(ch == '<')
			{
				sb.append("&lt;");
			}
			else if(ch == '>')
			{
				sb.append("&gt;");
			}
			else if(ch == '"')
			{
				sb.append("&quot;");
			}
			else if(ch >= 32 || ch == '\n' || ch == '\t')
			{
				sb.append(ch);
			}
		}
		return sb.toString();
	}
}
