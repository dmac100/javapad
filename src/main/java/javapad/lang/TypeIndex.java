package javapad.lang;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TypeIndex
{
	public static final int CLASS = 0;
	public static final int INTERFACE = 1;
	public static final int ENUM = 2;
	public static final int ANNOTATION = 3;

	public static final int PROJECT = 0;
	public static final int LIBRARY = 1;
	public static final int JDK = 2;

	public static class Entry
	{
		public final String fqn;
		public final String simple;
		public final String pkg;
		public final int kind;
		public final int origin;

		public Entry(String pkg, String simple, String fqn, int kind, int origin)
		{
			this.pkg = pkg;
			this.simple = simple;
			this.fqn = fqn;
			this.kind = kind;
			this.origin = origin;
		}

		public String toString()
		{
			return fqn;
		}
	}

	private static List<Entry> jdk;
	private static final Map<String, List<Entry>> jarCache = new LinkedHashMap<String, List<Entry>>();

	private final Entry[] sorted;
	private final Map<String, List<Entry>> bySimple = new LinkedHashMap<String, List<Entry>>();
	private final Map<String, List<Entry>> byPackage = new LinkedHashMap<String, List<Entry>>();
	private final TreeSet<String> packages = new TreeSet<String>();

	public TypeIndex(Collection<Entry> entries)
	{
		Map<String, Entry> unique = new LinkedHashMap<String, Entry>();
		for(Entry e : entries)
		{
			Entry old = unique.get(e.fqn);
			if(old == null || e.origin < old.origin)
			{
				unique.put(e.fqn, e);
			}
		}
		sorted = unique.values().toArray(new Entry[0]);
		Arrays.sort(sorted, Comparator.comparing((Entry e) -> e.simple.toLowerCase()).thenComparing(e -> e.fqn));
		for(Entry e : sorted)
		{
			bySimple.computeIfAbsent(e.simple, k -> new ArrayList<Entry>()).add(e);
			byPackage.computeIfAbsent(e.pkg, k -> new ArrayList<Entry>()).add(e);
			String p = e.pkg;
			while(!p.isEmpty() && packages.add(p))
			{
				int dot = p.lastIndexOf('.');
				p = dot < 0 ? "" : p.substring(0, dot);
			}
		}
	}

	public static TypeIndex build(Collection<File> sourceFolders, Collection<File> libraries)
	{
		List<Entry> all = new ArrayList<Entry>(jdkEntries());
		for(File lib : libraries)
		{
			if(lib.isFile() && lib.getName().toLowerCase().endsWith(".jar"))
			{
				all.addAll(jarEntries(lib));
			}
			else if(lib.isDirectory())
			{
				readClassFolder(lib, lib, all);
			}
		}
		for(File src : sourceFolders)
		{
			readSources(src, src, all);
		}
		return new TypeIndex(all);
	}

	public int size()
	{
		return sorted.length;
	}

	public List<Entry> exact(String simpleName)
	{
		List<Entry> list = bySimple.get(simpleName);
		return list == null ? new ArrayList<Entry>() : new ArrayList<Entry>(list);
	}

	public Entry find(String fqn)
	{
		List<Entry> list = bySimple.get(Names.simple(fqn));
		if(list != null)
		{
			for(Entry e : list)
			{
				if(e.fqn.equals(fqn))
				{
					return e;
				}
			}
		}
		return null;
	}

	public List<Entry> withPrefix(String prefix, int limit)
	{
		List<Entry> result = new ArrayList<Entry>();
		String lower = prefix.toLowerCase();
		int lo = 0;
		int hi = sorted.length;
		while(lo < hi)
		{
			int mid = (lo + hi) >>> 1;
			if(sorted[mid].simple.toLowerCase().compareTo(lower) < 0)
			{
				lo = mid + 1;
			}
			else
			{
				hi = mid;
			}
		}
		for(int i = lo; i < sorted.length && result.size() < limit; i++)
		{
			if(!sorted[i].simple.toLowerCase().startsWith(lower))
			{
				break;
			}
			result.add(sorted[i]);
		}
		if(prefix.length() >= 2 && result.size() < limit)
		{
			for(Entry e : sorted)
			{
				if(result.size() >= limit)
				{
					break;
				}
				if(!e.simple.toLowerCase().startsWith(lower) && Names.matches(prefix, e.simple))
				{
					result.add(e);
				}
			}
		}
		return result;
	}

	public boolean hasPackage(String pkg)
	{
		return packages.contains(pkg);
	}

	// The next name segment of every package below parent ("" for the top level), without duplicates.
	public List<String> childPackages(String parent)
	{
		TreeSet<String> names = new TreeSet<String>();
		String start = parent.isEmpty() ? "" : parent + ".";
		for(String p : packages.tailSet(start))
		{
			if(!p.startsWith(start))
			{
				break;
			}
			String rest = p.substring(start.length());
			int dot = rest.indexOf('.');
			names.add(dot < 0 ? rest : rest.substring(0, dot));
		}
		return new ArrayList<String>(names);
	}

	public List<Entry> typesIn(String pkg)
	{
		List<Entry> list = byPackage.get(pkg);
		return list == null ? new ArrayList<Entry>() : new ArrayList<Entry>(list);
	}

	static synchronized List<Entry> jdkEntries()
	{
		if(jdk != null)
		{
			return jdk;
		}
		List<Entry> list = new ArrayList<Entry>();
		try
		{
			FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
			for(ModuleReference ref : ModuleFinder.ofSystem().findAll())
			{
				ModuleDescriptor d = ref.descriptor();
				for(ModuleDescriptor.Exports ex : d.exports())
				{
					if(ex.isQualified())
					{
						continue;
					}
					Path dir = fs.getPath("/modules", d.name(), ex.source().replace('.', '/'));
					if(!Files.isDirectory(dir))
					{
						continue;
					}
					try(DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.class"))
					{
						for(Path p : ds)
						{
							try(InputStream in = Files.newInputStream(p))
							{
								addClass(list, ex.source(), p.getFileName().toString(), in, JDK);
							}
							catch(IOException e)
							{
							}
						}
					}
				}
			}
		}
		catch(Exception e)
		{
		}
		jdk = list;
		return list;
	}

	static void addClass(List<Entry> out, String pkg, String fileName, InputStream in, int origin) throws IOException
	{
		String base = fileName.substring(0, fileName.length() - ".class".length());
		if(base.equals("module-info") || base.equals("package-info") || base.matches(".*\\$\\d.*"))
		{
			return;
		}
		int flags = ClassFlags.read(in);
		if((flags & ClassFlags.PUBLIC) == 0 || (flags & ClassFlags.SYNTHETIC) != 0)
		{
			return;
		}
		int kind = (flags & ClassFlags.ANNOTATION) != 0 ? ANNOTATION : (flags & ClassFlags.ENUM) != 0 ? ENUM : (flags & ClassFlags.INTERFACE) != 0 ? INTERFACE : CLASS;
		String nested = base.replace('$', '.');
		String simple = nested.substring(nested.lastIndexOf('.') + 1);
		out.add(new Entry(pkg, simple, pkg.isEmpty() ? nested : pkg + "." + nested, kind, origin));
	}

	// Jar contents rarely change, so scans are kept per file (path, size and date) across index rebuilds.
	static synchronized List<Entry> jarEntries(File jar)
	{
		String key = jar.getAbsolutePath() + "|" + jar.length() + "|" + jar.lastModified();
		List<Entry> cached = jarCache.get(key);
		if(cached == null)
		{
			cached = new ArrayList<Entry>();
			readJar(jar, cached);
			jarCache.put(key, cached);
		}
		return cached;
	}

	static void readJar(File jar, List<Entry> out)
	{
		try(ZipFile zip = new ZipFile(jar))
		{
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while(entries.hasMoreElements())
			{
				ZipEntry e = entries.nextElement();
				String name = e.getName();
				if(e.isDirectory() || !name.endsWith(".class") || name.startsWith("META-INF/"))
				{
					continue;
				}
				int slash = name.lastIndexOf('/');
				String pkg = slash < 0 ? "" : name.substring(0, slash).replace('/', '.');
				try(InputStream in = zip.getInputStream(e))
				{
					addClass(out, pkg, name.substring(slash + 1), in, LIBRARY);
				}
				catch(IOException ex)
				{
				}
			}
		}
		catch(IOException e)
		{
		}
	}

	static void readClassFolder(File root, File dir, List<Entry> out)
	{
		File[] children = dir.listFiles();
		if(children == null)
		{
			return;
		}
		for(File f : children)
		{
			if(f.isDirectory())
			{
				readClassFolder(root, f, out);
			}
			else if(f.getName().endsWith(".class"))
			{
				String rel = root.toPath().relativize(dir.toPath()).toString();
				try(InputStream in = Files.newInputStream(f.toPath()))
				{
					addClass(out, rel.replace(File.separatorChar, '.'), f.getName(), in, LIBRARY);
				}
				catch(IOException e)
				{
				}
			}
		}
	}

	static void readSources(File root, File dir, List<Entry> out)
	{
		File[] children = dir.listFiles();
		if(children == null)
		{
			return;
		}
		for(File f : children)
		{
			if(f.isDirectory())
			{
				if(!f.getName().startsWith("."))
				{
					readSources(root, f, out);
				}
			}
			else if(f.getName().endsWith(".java") && f.length() < 2000000)
			{
				String base = f.getName().substring(0, f.getName().length() - 5);
				String pkg = root.toPath().relativize(dir.toPath()).toString().replace(File.separatorChar, '.');
				try
				{
					String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
					Matcher m = Pattern.compile("(?m)^\\s*public\\s+(?:(?:abstract|final|sealed|non-sealed|strictfp)\\s+)*(class|interface|enum|@interface|record)\\s+" + Pattern.quote(base) + "\\b").matcher(text);
					if(m.find())
					{
						String kw = m.group(1);
						int kind = kw.equals("interface") ? INTERFACE : kw.equals("enum") ? ENUM : kw.equals("@interface") ? ANNOTATION : CLASS;
						out.add(new Entry(pkg, base, pkg.isEmpty() ? base : pkg + "." + base, kind, PROJECT));
					}
				}
				catch(IOException e)
				{
				}
			}
		}
	}
}
