package javapad.run;

import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

// Finds the classes the java launcher can start, by parsing the sources (strings and comments cannot fool it).
// Follows the launch protocol: a non-private void main that takes String[] or nothing, static or (with a usable
// constructor) an instance method; java 25 allows all of them, java 21 to 24 only as preview features.
public class MainFinder
{
	private static final Map<String, Cached> cache = new HashMap<String, Cached>();

	public static class Main
	{
		public final String className;
		public final File file;
		public final int line;
		public final String signature;
		public final boolean preview;
		public final boolean classic;

		Main(String className, File file, int line, String signature, boolean preview, boolean classic)
		{
			this.className = className;
			this.file = file;
			this.line = line;
			this.signature = signature;
			this.preview = preview;
			this.classic = classic;
		}

		public String label()
		{
			return className + "   (" + file.getName() + ":" + line + ")" + (classic ? "" : "   " + signature);
		}
	}

	static class Cached
	{
		long modified;
		long length;
		List<Main> mains;
	}

	static int feature()
	{
		return Runtime.version().feature();
	}

	public static boolean hasMain(File file)
	{
		return !inFile(file).isEmpty();
	}

	public static boolean needsPreview(List<Main> mains)
	{
		for(Main m : mains)
		{
			if(m.preview)
			{
				return true;
			}
		}
		return false;
	}

	public static List<Main> inSource(File file, String text)
	{
		Map<File, String> sources = new LinkedHashMap<File, String>();
		sources.put(file, text);
		List<Main> result = parse(sources).get(file);
		return result == null ? new ArrayList<Main>() : result;
	}

	public static synchronized List<Main> inFile(File file)
	{
		if(!file.getName().endsWith(".java") || !file.isFile())
		{
			return new ArrayList<Main>();
		}
		List<Main> cached = cached(file);
		if(cached != null)
		{
			return cached;
		}
		Map<File, String> sources = new LinkedHashMap<File, String>();
		sources.put(file, read(file));
		return store(file, parse(sources).get(file));
	}

	// Every main in the source folders. Files with unsaved changes can be passed in overrides to use their text instead.
	public static synchronized List<Main> inProject(List<File> sourceFolders, Map<File, String> overrides)
	{
		List<File> files = new ArrayList<File>();
		for(File src : sourceFolders)
		{
			walk(src, files);
		}
		List<Main> result = new ArrayList<Main>();
		Map<File, String> stale = new LinkedHashMap<File, String>();
		for(File f : files)
		{
			if(overrides != null && overrides.containsKey(f))
			{
				stale.put(f, overrides.get(f));
			}
			else if(cached(f) == null)
			{
				stale.put(f, read(f));
			}
		}
		Map<File, List<Main>> parsed = parse(stale);
		for(File f : files)
		{
			List<Main> mains;
			if(stale.containsKey(f))
			{
				mains = parsed.get(f);
				if(overrides == null || !overrides.containsKey(f))
				{
					mains = store(f, mains);
				}
			}
			else
			{
				mains = cached(f);
			}
			if(mains != null)
			{
				result.addAll(mains);
			}
		}
		return result;
	}

	static List<Main> cached(File f)
	{
		Cached c = cache.get(f.getAbsolutePath());
		return c != null && c.modified == f.lastModified() && c.length == f.length() ? c.mains : null;
	}

	static List<Main> store(File f, List<Main> mains)
	{
		Cached c = new Cached();
		c.modified = f.lastModified();
		c.length = f.length();
		c.mains = mains == null ? new ArrayList<Main>() : mains;
		cache.put(f.getAbsolutePath(), c);
		return c.mains;
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
				if(!f.getName().startsWith("."))
				{
					walk(f, out);
				}
			}
			else if(f.getName().endsWith(".java"))
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

	// One parse (no attribution) for all the given sources.
	static Map<File, List<Main>> parse(Map<File, String> sources)
	{
		Map<File, List<Main>> result = new LinkedHashMap<File, List<Main>>();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if(compiler == null || sources.isEmpty())
		{
			return result;
		}
		List<JavaFileObject> units = new ArrayList<JavaFileObject>();
		for(Map.Entry<File, String> e : sources.entrySet())
		{
			String text = e.getValue();
			units.add(new SimpleJavaFileObject(e.getKey().toURI(), JavaFileObject.Kind.SOURCE)
			{
				public CharSequence getCharContent(boolean ignoreEncodingErrors)
				{
					return text;
				}
			});
		}
		List<String> options = new ArrayList<String>(Arrays.asList("-proc:none", "-Xlint:none"));
		int feature = feature();
		if(feature >= 21 && feature < 25)
		{
			// compact source files and instance mains parse only with preview features on
			options.addAll(Arrays.asList("--enable-preview", "-source", String.valueOf(feature)));
		}
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		try(StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null))
		{
			JavacTask task = (JavacTask)compiler.getTask(null, fm, diagnostics, options, null, units);
			Trees trees = Trees.instance(task);
			for(CompilationUnitTree unit : task.parse())
			{
				File file = new File(URI.create(unit.getSourceFile().toUri().toString()));
				List<Main> mains = new ArrayList<Main>();
				String pkg = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
				for(Tree t : unit.getTypeDecls())
				{
					if(t instanceof ClassTree)
					{
						visit(unit, trees.getSourcePositions(), file, (ClassTree)t, pkg.isEmpty() ? "" : pkg + ".", true, mains);
					}
				}
				result.put(file, mains);
			}
		}
		catch(Throwable e)
		{
			// a source that cannot be parsed has no mains
		}
		return result;
	}

	static void visit(CompilationUnitTree unit, SourcePositions positions, File file, ClassTree type, String prefix, boolean instantiableScope, List<Main> out)
	{
		Tree.Kind kind = type.getKind();
		if(kind == Tree.Kind.ANNOTATION_TYPE)
		{
			return;
		}
		String binary = prefix + type.getSimpleName();
		boolean isInterface = kind == Tree.Kind.INTERFACE;
		boolean canInstantiate = instantiableScope && kind == Tree.Kind.CLASS && !type.getModifiers().getFlags().contains(Modifier.ABSTRACT) && hasUsableConstructor(type);
		MethodTree best = null;
		int bestRank = Integer.MAX_VALUE;
		for(Tree member : type.getMembers())
		{
			if(member instanceof MethodTree)
			{
				MethodTree m = (MethodTree)member;
				int rank = rank(m, isInterface, canInstantiate);
				if(rank >= 0 && rank < bestRank)
				{
					best = m;
					bestRank = rank;
				}
			}
		}
		if(best != null)
		{
			boolean isStatic = best.getModifiers().getFlags().contains(Modifier.STATIC);
			boolean takesArgs = !best.getParameters().isEmpty();
			boolean isPublic = isInterface || best.getModifiers().getFlags().contains(Modifier.PUBLIC);
			boolean classic = isStatic && takesArgs && isPublic;
			int feature = feature();
			if(classic || feature >= 21)
			{
				long start = positions.getStartPosition(unit, best);
				int line = (int)unit.getLineMap().getLineNumber(Math.max(0, start));
				out.add(new Main(binary, file, line, signature(best, isInterface), !classic && feature < 25, classic));
			}
		}
		for(Tree member : type.getMembers())
		{
			if(member instanceof ClassTree)
			{
				ClassTree nested = (ClassTree)member;
				boolean staticNested = isInterface || nested.getModifiers().getFlags().contains(Modifier.STATIC) || nested.getKind() != Tree.Kind.CLASS;
				visit(unit, positions, file, nested, binary + "$", staticNested && instantiableScope, out);
			}
		}
	}

	static boolean hasUsableConstructor(ClassTree type)
	{
		boolean any = false;
		for(Tree member : type.getMembers())
		{
			if(member instanceof MethodTree && ((MethodTree)member).getName().contentEquals("<init>"))
			{
				MethodTree c = (MethodTree)member;
				// javac adds a default constructor to the tree for classes that declare none; it has no source position
				any = true;
				if(c.getParameters().isEmpty() && !c.getModifiers().getFlags().contains(Modifier.PRIVATE))
				{
					return true;
				}
			}
		}
		return !any;
	}

	// 0 static main(String[]), 1 static main(), 2 instance main(String[]), 3 instance main(); -1 if it cannot be launched.
	static int rank(MethodTree m, boolean inInterface, boolean canInstantiate)
	{
		if(!m.getName().contentEquals("main") || m.getBody() == null)
		{
			return -1;
		}
		Set<Modifier> flags = m.getModifiers().getFlags();
		if(flags.contains(Modifier.PRIVATE) || flags.contains(Modifier.ABSTRACT))
		{
			return -1;
		}
		if(!(m.getReturnType() instanceof PrimitiveTypeTree) || ((PrimitiveTypeTree)m.getReturnType()).getPrimitiveTypeKind() != TypeKind.VOID)
		{
			return -1;
		}
		boolean isStatic = flags.contains(Modifier.STATIC);
		if(inInterface && !isStatic)
		{
			return -1;
		}
		int params = m.getParameters().size();
		if(params > 1 || (params == 1 && !isStringArray(m.getParameters().get(0))))
		{
			return -1;
		}
		if(!isStatic && !canInstantiate)
		{
			return -1;
		}
		return (isStatic ? 0 : 2) + (params == 1 ? 0 : 1);
	}

	static boolean isStringArray(VariableTree p)
	{
		Tree type = p.getType();
		if(!(type instanceof ArrayTypeTree))
		{
			return false;
		}
		Tree element = ((ArrayTypeTree)type).getType();
		if(element instanceof IdentifierTree)
		{
			return ((IdentifierTree)element).getName().contentEquals("String");
		}
		return element instanceof MemberSelectTree && element.toString().equals("java.lang.String");
	}

	static String signature(MethodTree m, boolean inInterface)
	{
		Set<Modifier> flags = m.getModifiers().getFlags();
		StringBuilder sb = new StringBuilder();
		if(flags.contains(Modifier.PUBLIC) || inInterface)
		{
			sb.append("public ");
		}
		else if(flags.contains(Modifier.PROTECTED))
		{
			sb.append("protected ");
		}
		if(flags.contains(Modifier.STATIC))
		{
			sb.append("static ");
		}
		return sb.append("void main(").append(m.getParameters().isEmpty() ? "" : "String[]").append(")").toString();
	}
}
