package javapad.lang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

public class ImportFixer
{
	static final Pattern SYMBOL = Pattern.compile("symbol:\\s+(class|variable)\\s+([\\w$]+)");
	static final String[] FAMILIAR = {"java.util", "java.io", "java.nio.file", "java.time", "java.util.function", "java.util.concurrent", "java.util.stream", "java.net", "java.text", "java.math"};

	public static class Missing
	{
		public final String name;
		public final int position;
		public final int line;

		Missing(String name, int position, int line)
		{
			this.name = name;
			this.position = position;
			this.line = line;
		}
	}

	public static class Plan
	{
		public final List<String> keep = new ArrayList<String>();
		public final List<String> statics = new ArrayList<String>();
		public final List<String> removed = new ArrayList<String>();
		public final List<String> added = new ArrayList<String>();
		public final Map<String, List<TypeIndex.Entry>> choices = new LinkedHashMap<String, List<TypeIndex.Entry>>();
		public final List<String> notFound = new ArrayList<String>();
	}

	// Simple type names javac could not resolve, in order of first appearance.
	public static List<Missing> unresolved(JavaAnalyzer.Result r)
	{
		List<Missing> list = new ArrayList<Missing>();
		LinkedHashSet<String> seen = new LinkedHashSet<String>();
		for(Diagnostic<? extends JavaFileObject> d : r.diagnostics)
		{
			String code = d.getCode();
			if(d.getKind() != Diagnostic.Kind.ERROR || code == null || !code.startsWith("compiler.err.cant.resolve"))
			{
				continue;
			}
			Matcher m = SYMBOL.matcher(d.getMessage(Locale.ENGLISH));
			if(!m.find())
			{
				continue;
			}
			String name = m.group(2);
			if(m.group(1).equals("variable") && !Character.isUpperCase(name.charAt(0)))
			{
				continue;
			}
			if(seen.add(name))
			{
				list.add(new Missing(name, (int)d.getPosition(), (int)d.getLineNumber()));
			}
		}
		return list;
	}

	static int rank(TypeIndex.Entry e)
	{
		if(e.origin == TypeIndex.PROJECT)
		{
			return 0;
		}
		for(int i = 0; i < FAMILIAR.length; i++)
		{
			if(e.pkg.equals(FAMILIAR[i]))
			{
				return 1 + i;
			}
		}
		if(e.origin == TypeIndex.LIBRARY)
		{
			return 40;
		}
		return e.fqn.startsWith("java.") ? 20 : 30;
	}

	// Types a simple name could be imported from, best guess first; types already visible in this file are left out.
	public static List<TypeIndex.Entry> candidates(TypeIndex index, String name, String text)
	{
		List<TypeIndex.Entry> list = new ArrayList<TypeIndex.Entry>();
		for(TypeIndex.Entry e : index.exact(name))
		{
			if(!ImportEditor.isVisible(text, e.fqn, e.pkg))
			{
				list.add(e);
			}
		}
		list.sort((a, b) ->
		{
			int c = Integer.compare(rank(a), rank(b));
			return c != 0 ? c : a.fqn.compareTo(b.fqn);
		});
		return list;
	}

	public static Plan plan(String text, JavaAnalyzer.Result r, TypeIndex index)
	{
		Plan plan = new Plan();
		String body = ImportEditor.IMPORT.matcher(text).replaceAll("");
		for(String name : ImportEditor.imports(text, false))
		{
			if(name.endsWith(".*") || used(body, Names.simple(name)))
			{
				plan.keep.add(name);
			}
			else
			{
				plan.removed.add(name);
			}
		}
		for(String name : ImportEditor.imports(text, true))
		{
			if(name.endsWith(".*") || used(body, Names.simple(name)))
			{
				plan.statics.add(name);
			}
			else
			{
				plan.removed.add("static " + name);
			}
		}
		for(Missing missing : unresolved(r))
		{
			List<TypeIndex.Entry> found = candidates(index, missing.name, text);
			if(found.isEmpty())
			{
				plan.notFound.add(missing.name);
			}
			else if(found.size() == 1)
			{
				plan.keep.add(found.get(0).fqn);
				plan.added.add(found.get(0).fqn);
			}
			else
			{
				plan.choices.put(missing.name, found);
			}
		}
		return plan;
	}

	static boolean used(String body, String simple)
	{
		return Pattern.compile("(?<![\\w$])" + Pattern.quote(simple) + "(?![\\w$])").matcher(body).find();
	}
}
