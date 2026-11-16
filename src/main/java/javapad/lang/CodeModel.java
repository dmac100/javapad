package javapad.lang;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.File;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javapad.build.ProjectCompiler;
import javapad.model.EclipseProject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

// Everything in the project's sources that navigation needs: who extends or implements what, which methods override
// which, and where every method is called. Built by one javac attribution pass over all source files.
public class CodeModel
{
	private final Map<String, TypeInfo> types = new LinkedHashMap<String, TypeInfo>();
	private final Map<String, MethodInfo> methods = new LinkedHashMap<String, MethodInfo>();
	private final Map<String, List<Call>> byTarget = new LinkedHashMap<String, List<Call>>();
	private final Map<String, List<Call>> byCaller = new LinkedHashMap<String, List<Call>>();
	private final Map<String, List<MethodInfo>> overriders = new LinkedHashMap<String, List<MethodInfo>>();
	private final Map<String, List<TypeInfo>> subtypes = new LinkedHashMap<String, List<TypeInfo>>();

	public static class Place
	{
		public final File file;
		public final int line;
		public final int column;
		public final String label;

		public Place(File file, int line, int column, String label)
		{
			this.file = file;
			this.line = line;
			this.column = column;
			this.label = label;
		}
	}

	public static class MethodInfo
	{
		public String key;
		public String owner;
		public String name;
		public String display;
		public boolean isAbstract;
		public Place place;
		public final List<String> overrides = new ArrayList<String>();
	}

	public static class TypeInfo
	{
		public String name;
		public String display;
		public boolean anonymous;
		public Place place;
		public final List<String> supertypes = new ArrayList<String>();
	}

	public static class Call
	{
		public String targetKey;
		public String callerKey;
		public Place place;
	}

	public static String methodKey(ExecutableElement m, Elements elements, Types types)
	{
		StringBuilder sb = new StringBuilder(ownerName((TypeElement)m.getEnclosingElement(), elements));
		sb.append('#').append(m.getKind() == ElementKind.CONSTRUCTOR ? "<init>" : m.getSimpleName().toString()).append('(');
		boolean first = true;
		for(VariableElement p : m.getParameters())
		{
			if(!first)
			{
				sb.append(',');
			}
			first = false;
			sb.append(types.erasure(p.asType()));
		}
		return sb.append(')').toString();
	}

	public static String ownerName(TypeElement type, Elements elements)
	{
		String q = type.getQualifiedName().toString();
		return q.isEmpty() ? elements.getBinaryName(type).toString() : q;
	}

	public static String methodDisplay(ExecutableElement m, Elements elements, Types types)
	{
		TypeElement owner = (TypeElement)m.getEnclosingElement();
		String ownerName = owner.getSimpleName().length() == 0 ? "<anonymous " + superName(owner) + ">" : owner.getSimpleName().toString();
		StringBuilder sb = m.getKind() == ElementKind.CONSTRUCTOR ? new StringBuilder("new ").append(ownerName) : new StringBuilder(ownerName).append('.').append(m.getSimpleName());
		sb.append('(');
		boolean first = true;
		for(VariableElement p : m.getParameters())
		{
			if(!first)
			{
				sb.append(", ");
			}
			first = false;
			sb.append(Names.shorten(types.erasure(p.asType()).toString()));
		}
		return sb.append(')').toString();
	}

	static String superName(TypeElement anonymous)
	{
		if(!anonymous.getInterfaces().isEmpty())
		{
			return Names.shorten(anonymous.getInterfaces().get(0).toString());
		}
		return Names.shorten(anonymous.getSuperclass().toString());
	}

	public static CodeModel build(EclipseProject project)
	{
		CodeModel model = new CodeModel();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if(compiler == null)
		{
			return model;
		}
		List<File> files = new ArrayList<File>();
		for(File src : project.sourceFolders)
		{
			collect(src, files);
		}
		if(files.isEmpty())
		{
			return model;
		}
		List<String> options = new ArrayList<String>(Arrays.asList("-proc:none", "-Xlint:none", "-XDshould-stop.at=FLOW", "-XDshould-stop.ifError=FLOW", "-cp", project.classpathString()));
		int release = ProjectCompiler.effectiveRelease(project.release, compiler);
		if(release > 0)
		{
			options.addAll(Arrays.asList("-source", String.valueOf(release), "-target", String.valueOf(release)));
		}
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		try(StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null))
		{
			JavacTask task = (JavacTask)compiler.getTask(null, fm, diagnostics, options, null, fm.getJavaFileObjectsFromFiles(files));
			Iterable<? extends CompilationUnitTree> units = task.parse();
			task.analyze();
			Trees trees = Trees.instance(task);
			for(CompilationUnitTree unit : units)
			{
				new Visitor(model, trees, task.getElements(), task.getTypes(), unit).scan(new TreePath(unit), null);
			}
		}
		catch(Throwable e)
		{
			return model;
		}
		model.link();
		return model;
	}

	static void collect(File dir, List<File> out)
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
					collect(f, out);
				}
			}
			else if(f.getName().endsWith(".java"))
			{
				out.add(f);
			}
		}
	}

	void link()
	{
		for(MethodInfo m : methods.values())
		{
			for(String key : m.overrides)
			{
				overriders.computeIfAbsent(key, k -> new ArrayList<MethodInfo>()).add(m);
			}
		}
		for(TypeInfo t : types.values())
		{
			for(String s : t.supertypes)
			{
				subtypes.computeIfAbsent(s, k -> new ArrayList<TypeInfo>()).add(t);
			}
		}
	}

	public boolean isEmpty()
	{
		return types.isEmpty();
	}

	public MethodInfo method(String key)
	{
		return methods.get(key);
	}

	public TypeInfo type(String name)
	{
		return types.get(name);
	}

	// Methods in the project that override or implement the given method, at any depth.
	public List<MethodInfo> implementers(String methodKey)
	{
		List<MethodInfo> list = overriders.get(methodKey);
		return list == null ? new ArrayList<MethodInfo>() : new ArrayList<MethodInfo>(list);
	}

	// Every project type below the given one, nearest first.
	public List<TypeInfo> allSubtypes(String typeName)
	{
		List<TypeInfo> result = new ArrayList<TypeInfo>();
		Set<String> seen = new HashSet<String>();
		Deque<String> queue = new ArrayDeque<String>();
		queue.add(typeName);
		seen.add(typeName);
		while(!queue.isEmpty())
		{
			List<TypeInfo> direct = subtypes.get(queue.poll());
			if(direct == null)
			{
				continue;
			}
			for(TypeInfo t : direct)
			{
				String id = t.anonymous ? t.name : t.name;
				if(seen.add(id))
				{
					result.add(t);
					queue.add(id);
				}
			}
		}
		return result;
	}

	// Calls to the method, plus calls to the methods it overrides (for example through an interface).
	public List<Call> callersOf(String methodKey)
	{
		List<Call> result = new ArrayList<Call>();
		Set<String> keys = new LinkedHashSet<String>();
		keys.add(methodKey);
		MethodInfo m = methods.get(methodKey);
		if(m != null)
		{
			keys.addAll(m.overrides);
		}
		for(String k : keys)
		{
			List<Call> calls = byTarget.get(k);
			if(calls != null)
			{
				result.addAll(calls);
			}
		}
		return result;
	}

	public List<Call> calleesOf(String methodKey)
	{
		List<Call> calls = byCaller.get(methodKey);
		return calls == null ? new ArrayList<Call>() : new ArrayList<Call>(calls);
	}

	static class Visitor extends TreePathScanner<Void, Void>
	{
		private final CodeModel model;
		private final Trees trees;
		private final Elements elements;
		private final Types types;
		private final CompilationUnitTree unit;
		private final File file;
		private final SourcePositions positions;
		private final Deque<String> callers = new ArrayDeque<String>();
		private String source;

		Visitor(CodeModel model, Trees trees, Elements elements, Types types, CompilationUnitTree unit)
		{
			this.model = model;
			this.trees = trees;
			this.elements = elements;
			this.types = types;
			this.unit = unit;
			this.file = new File(URI.create(unit.getSourceFile().toUri().toString()));
			this.positions = trees.getSourcePositions();
			try
			{
				this.source = unit.getSourceFile().getCharContent(true).toString();
			}
			catch(Exception e)
			{
				this.source = "";
			}
		}

		Place place(long offset, String label)
		{
			LineMap map = unit.getLineMap();
			long line = map.getLineNumber(offset);
			// LineMap columns expand tabs, so measure from the start of the line instead
			return new Place(file, (int)line, (int)(offset - map.getStartPosition(line)) + 1, label);
		}

		// The position of a declaration's name, found after its modifiers and annotations.
		long namePosition(Tree tree, String name)
		{
			long start = positions.getStartPosition(unit, tree);
			if(start < 0)
			{
				return 0;
			}
			int from = (int)start;
			int idx = source.indexOf(name, from);
			while(idx >= 0)
			{
				boolean before = idx == 0 || !Character.isJavaIdentifierPart(source.charAt(idx - 1));
				boolean after = idx + name.length() >= source.length() || !Character.isJavaIdentifierPart(source.charAt(idx + name.length()));
				if(before && after)
				{
					return idx;
				}
				idx = source.indexOf(name, idx + 1);
			}
			return start;
		}

		public Void visitClass(ClassTree node, Void p)
		{
			Element e = trees.getElement(getCurrentPath());
			if(e instanceof TypeElement)
			{
				TypeElement te = (TypeElement)e;
				TypeInfo info = new TypeInfo();
				info.name = ownerName(te, elements);
				info.anonymous = te.getSimpleName().length() == 0;
				info.display = info.anonymous ? "<anonymous " + superName(te) + ">" : te.getQualifiedName().toString();
				long pos = info.anonymous ? positions.getStartPosition(unit, node) : namePosition(node, te.getSimpleName().toString());
				info.place = place(pos, info.display);
				TypeMirror sc = te.getSuperclass();
				if(sc.getKind() == TypeKind.DECLARED)
				{
					info.supertypes.add(ownerName((TypeElement)((DeclaredType)sc).asElement(), elements));
				}
				for(TypeMirror i : te.getInterfaces())
				{
					if(i.getKind() == TypeKind.DECLARED)
					{
						info.supertypes.add(ownerName((TypeElement)((DeclaredType)i).asElement(), elements));
					}
				}
				model.types.put(info.name, info);
				callers.push(info.name + "#<init>");
			}
			else
			{
				callers.push("?");
			}
			try
			{
				return super.visitClass(node, p);
			}
			finally
			{
				callers.pop();
			}
		}

		public Void visitMethod(MethodTree node, Void p)
		{
			Element e = trees.getElement(getCurrentPath());
			if(e instanceof ExecutableElement)
			{
				ExecutableElement m = (ExecutableElement)e;
				MethodInfo info = new MethodInfo();
				info.key = methodKey(m, elements, types);
				info.owner = ownerName((TypeElement)m.getEnclosingElement(), elements);
				info.name = m.getSimpleName().toString();
				info.display = methodDisplay(m, elements, types);
				info.isAbstract = m.getModifiers().contains(Modifier.ABSTRACT);
				String declared = m.getKind() == ElementKind.CONSTRUCTOR ? m.getEnclosingElement().getSimpleName().toString() : m.getSimpleName().toString();
				info.place = place(namePosition(node, declared), info.display);
				if(m.getKind() == ElementKind.METHOD)
				{
					addOverrides(info, m);
				}
				model.methods.put(info.key, info);
				callers.push(info.key);
			}
			else
			{
				callers.push("?");
			}
			try
			{
				return super.visitMethod(node, p);
			}
			finally
			{
				callers.pop();
			}
		}

		void addOverrides(MethodInfo info, ExecutableElement m)
		{
			TypeElement owner = (TypeElement)m.getEnclosingElement();
			Set<TypeElement> ancestors = new LinkedHashSet<TypeElement>();
			collectAncestors(owner, ancestors);
			for(TypeElement a : ancestors)
			{
				for(ExecutableElement other : ElementFilter.methodsIn(a.getEnclosedElements()))
				{
					if(other.getSimpleName().equals(m.getSimpleName()) && elements.overrides(m, other, owner))
					{
						info.overrides.add(methodKey(other, elements, types));
					}
				}
			}
		}

		void collectAncestors(TypeElement type, Set<TypeElement> out)
		{
			for(TypeMirror s : types.directSupertypes(type.asType()))
			{
				if(s.getKind() == TypeKind.DECLARED)
				{
					TypeElement t = (TypeElement)((DeclaredType)s).asElement();
					if(out.add(t))
					{
						collectAncestors(t, out);
					}
				}
			}
		}

		void call(Element target, long offset)
		{
			if(!(target instanceof ExecutableElement) || callers.isEmpty())
			{
				return;
			}
			Call c = new Call();
			c.targetKey = methodKey((ExecutableElement)target, elements, types);
			c.callerKey = callers.peek();
			c.place = place(offset, "");
			model.byTarget.computeIfAbsent(c.targetKey, k -> new ArrayList<Call>()).add(c);
			model.byCaller.computeIfAbsent(c.callerKey, k -> new ArrayList<Call>()).add(c);
		}

		public Void visitMethodInvocation(MethodInvocationTree node, Void p)
		{
			Tree select = node.getMethodSelect();
			Element target = trees.getElement(new TreePath(getCurrentPath(), select));
			long offset;
			if(select instanceof MemberSelectTree)
			{
				offset = positions.getEndPosition(unit, select) - ((MemberSelectTree)select).getIdentifier().length();
			}
			else
			{
				offset = positions.getStartPosition(unit, select);
			}
			call(target, offset);
			return super.visitMethodInvocation(node, p);
		}

		public Void visitNewClass(NewClassTree node, Void p)
		{
			call(trees.getElement(getCurrentPath()), positions.getStartPosition(unit, node));
			return super.visitNewClass(node, p);
		}

		public Void visitMemberReference(MemberReferenceTree node, Void p)
		{
			call(trees.getElement(getCurrentPath()), positions.getStartPosition(unit, node));
			return super.visitMemberReference(node, p);
		}
	}
}
