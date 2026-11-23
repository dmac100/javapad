package javapad.run;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

// Finds JUnit test classes and methods by parsing the sources with javac; strings and comments cannot fool it.
public class TestFinder {
	private static final Set<String> TEST_ANNOTATIONS = new HashSet<String>(Arrays.asList("Test", "ParameterizedTest", "RepeatedTest", "TestFactory"));

	static class TestMethod {
		final String name;
		final int start;
		final int end;
		final int line;
		final boolean junit;
		final boolean concrete;

		TestMethod(String name, int start, int end, int line, boolean junit, boolean concrete) {
			this.name = name;
			this.start = start;
			this.end = end;
			this.line = line;
			this.junit = junit;
			this.concrete = concrete;
		}
	}

	static class ClassInfo {
		final ClassTree type;
		final boolean concrete;

		ClassInfo(ClassTree type, boolean concrete) {
			this.type = type;
			this.concrete = concrete;
		}
	}

	// One parsed source, with the imports and the test methods collected from its tree.
	static class Parsed {
		final CompilationUnitTree unit;
		final Map<String, String> imports = new HashMap<String, String>();
		final List<String> wildcards = new ArrayList<String>();
		final List<TestMethod> methods = new ArrayList<TestMethod>();
		final List<ClassInfo> classes = new ArrayList<ClassInfo>();
		boolean jupiter;

		Parsed(CompilationUnitTree unit, SourcePositions positions) {
			this.unit = unit;
			for(ImportTree imp : unit.getImports()) {
				String name = imp.getQualifiedIdentifier().toString();
				if(name.startsWith("org.junit.jupiter")) {
					jupiter = true;
				}
				if(imp.isStatic()) {
					continue;
				}
				if(name.endsWith(".*")) {
					wildcards.add(name.substring(0, name.length() - 2));
				} else {
					imports.put(simpleOf(name), name);
				}
			}
			for(Tree type : unit.getTypeDecls()) {
				if(type instanceof ClassTree) {
					visit((ClassTree)type, positions, true);
				}
			}
		}

		boolean isTestClass() {
			for(TestMethod m : methods) {
				if(m.junit && m.concrete) {
					return true;
				}
			}
			return false;
		}

		// 0: not a test annotation, 1: a test annotation without a known junit import, 2: a junit test annotation.
		int annotationKind(AnnotationTree annotation) {
			String name = annotation.getAnnotationType().toString();
			String simple = simpleOf(name);
			if(!TEST_ANNOTATIONS.contains(simple)) {
				return 0;
			}
			if(name.indexOf('.') >= 0) {
				return name.startsWith("org.junit.") ? 2 : 0;
			}
			String imported = imports.get(simple);
			if(imported != null) {
				return imported.startsWith("org.junit.") ? 2 : 0;
			}
			for(String wildcard : wildcards) {
				if(wildcard.equals("org.junit") || wildcard.startsWith("org.junit.")) {
					return 2;
				}
			}
			return 1;
		}

		void visit(ClassTree type, SourcePositions positions, boolean outerConcrete) {
			boolean concrete = outerConcrete && !type.getModifiers().getFlags().contains(Modifier.ABSTRACT);
			classes.add(new ClassInfo(type, concrete));
			for(Tree member : type.getMembers()) {
				if(member instanceof MethodTree) {
					MethodTree method = (MethodTree)member;
					AnnotationTree first = null;
					boolean junit = false;
					for(AnnotationTree a : method.getModifiers().getAnnotations()) {
						int kind = annotationKind(a);
						if(kind > 0) {
							if(first == null) {
								first = a;
							}
							if(kind == 2) {
								junit = true;
							}
							if(a.getAnnotationType().toString().startsWith("org.junit.jupiter")) {
								jupiter = true;
							}
						}
					}
					if(first == null) {
						continue;
					}
					long start = positions.getStartPosition(unit, first);
					if(start < 0) {
						start = positions.getStartPosition(unit, method);
					}
					long declaration = method.getReturnType() == null ? -1 : positions.getStartPosition(unit, method.getReturnType());
					if(declaration < 0) {
						declaration = positions.getStartPosition(unit, method);
					}
					long end = positions.getEndPosition(unit, method);
					if(method.getBody() != null) {
						long bodyEnd = positions.getEndPosition(unit, method.getBody());
						if(bodyEnd > end) {
							end = bodyEnd;
						}
					}
					methods.add(new TestMethod(method.getName().toString(), (int)start, (int)end,
						(int)unit.getLineMap().getLineNumber(Math.max(0, declaration)), junit, concrete));
				} else if(member instanceof ClassTree) {
					visit((ClassTree)member, positions, concrete);
				}
			}
		}
	}

	public static boolean isTestClass(String content) {
		Parsed parsed = parse(synthetic(), content);
		return parsed != null && parsed.isTestClass();
	}

	// The name of the @Test method whose declaration or body contains the offset, or null.
	public static String methodAt(String content, int offset) {
		Parsed parsed = parse(synthetic(), content);
		if(parsed == null) {
			return null;
		}
		TestMethod found = null;
		for(TestMethod m : parsed.methods) {
			if(m.start <= offset && offset <= m.end && (found == null || m.start > found.start)) {
				found = m;
			}
		}
		return found == null ? null : found.name;
	}

	// The 1-based line of the declaration of the named @Test method, or -1.
	public static int methodLine(String content, String method) {
		Parsed parsed = parse(synthetic(), content);
		if(parsed != null) {
			for(TestMethod m : parsed.methods) {
				if(m.name.equals(method)) {
					return m.line;
				}
			}
		}
		return -1;
	}

	// A test name from a result ("adds", "adds()") that can be selected as a method; null for parameterized/dynamic names.
	public static String selectableMethod(String name) {
		String n = name.endsWith("()") ? name.substring(0, name.length() - 2) : name;
		if(n.isEmpty() || n.equals("<class>")) {
			return null;
		}
		for(int i = 0; i < n.length(); i++) {
			if(!Character.isJavaIdentifierPart(n.charAt(i))) {
				return null;
			}
		}
		return n;
	}

	// the names of implementation classes a test class is likely to be testing
	public static List<String> testedClasses(String className, String content) {
		List<String> names = new ArrayList<String>();
		String simple = simpleName(className);
		String pkg = packageOf(className);
		for(String s : baseNames(simple)) {
			addName(names, pkg, s);
		}
		Parsed parsed = parse(synthetic(), content);
		if(parsed != null) {
			for(ClassInfo info : parsed.classes) {
				if(info.type.getSimpleName().contentEquals(simple)) {
					Tree parent = info.type.getExtendsClause();
					if(parent != null) {
						if(parent instanceof ParameterizedTypeTree) {
							parent = ((ParameterizedTypeTree)parent).getType();
						}
						String ext = parent.toString();
						if(!"TestCase".equals(ext) && !ext.endsWith(".TestCase")) {
							addName(names, pkg, ext);
						}
					}
					break;
				}
			}
		}
		return names;
	}

	// test classes in the project whose name matches an implementation class
	public static List<File> testsFor(String className, List<File> sourceFolders) {
		List<File> result = new ArrayList<File>();
		String simple = simpleName(className);
		String pkg = packageOf(className);
		Map<File, Parsed> parsed = parsedFiles(javaFiles(sourceFolders));
		for(File f : parsed.keySet()) {
			Parsed p = parsed.get(f);
			if(p == null || !p.isTestClass()) {
				continue;
			}
			String other = classNameOf(f, sourceFolders);
			if(other == null) {
				continue;
			}
			String otherSimple = simpleName(other);
			if(!matchesTest(simple, otherSimple)) {
				continue;
			}
			if(samePackage(pkg, packageOf(other)) || matchesTest(simple, otherSimple) && packageOf(other).endsWith(pkg.isEmpty() ? "" : pkg)) {
				// prefer same package, but keep same-named tests elsewhere
			}
			result.add(f);
		}
		// put same-package matches first
		result.sort((a, b) -> {
			String pa = packageOf(classNameOf(a, sourceFolders));
			String pb = packageOf(classNameOf(b, sourceFolders));
			boolean sa = pa.equals(pkg);
			boolean sb = pb.equals(pkg);
			return sa == sb ? 0 : sa ? -1 : 1;
		});
		return result;
	}

	static boolean matchesTest(String simple, String testSimple) {
		for(String base : baseNames(testSimple)) {
			if(base.equals(simple)) {
				return true;
			}
		}
		for(String base : baseNames(simple)) {
			if(testSimple.equals(base + "Test") || testSimple.equals(base + "Tests") || testSimple.equals(base + "TestCase") || testSimple.equals("Test" + base)) {
				return true;
			}
		}
		return false;
	}

	static List<String> baseNames(String simple) {
		List<String> names = new ArrayList<String>();
		if(simple.endsWith("TestCase") && simple.length() > 8) {
			names.add(simple.substring(0, simple.length() - 8));
		}
		if(simple.endsWith("Tests") && simple.length() > 5) {
			names.add(simple.substring(0, simple.length() - 5));
		}
		if(simple.endsWith("Test") && simple.length() > 4) {
			names.add(simple.substring(0, simple.length() - 4));
		}
		if(simple.startsWith("Test") && simple.length() > 4) {
			names.add(simple.substring(4));
		}
		if(names.isEmpty()) {
			names.add(simple);
		}
		return names;
	}

	static void addName(List<String> names, String pkg, String simpleOrQualified) {
		String name = simpleOrQualified.indexOf('.') >= 0 ? simpleOrQualified : (pkg.isEmpty() ? "" : pkg + ".") + simpleOrQualified;
		if(!names.contains(name)) {
			names.add(name);
		}
	}

	static String simpleName(String className) {
		String n = className == null ? "" : className;
		int dot = n.lastIndexOf('.');
		return dot < 0 ? n : n.substring(dot + 1);
	}

	static String simpleOf(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? name : name.substring(dot + 1);
	}

	static String packageOf(String className) {
		if(className == null) {
			return "";
		}
		int dot = className.lastIndexOf('.');
		return dot < 0 ? "" : className.substring(0, dot);
	}

	static boolean samePackage(String a, String b) {
		return a.equals(b);
	}

	static String classNameOf(File file, List<File> sourceFolders) {
		String path = file.getAbsolutePath();
		for(File src : sourceFolders) {
			String base = src.getAbsolutePath() + File.separator;
			if(path.startsWith(base) && path.endsWith(".java")) {
				String rel = path.substring(base.length(), path.length() - 5);
				return rel.replace(File.separatorChar, '.');
			}
		}
		return null;
	}

	public static boolean isJupiter(String content) {
		Parsed parsed = parse(synthetic(), content);
		return parsed != null && parsed.jupiter;
	}

	public static List<File> find(File target, List<File> sourceFolders) {
		List<File> result = new ArrayList<File>();
		if(target == null) {
			return result;
		}
		if(target.isFile()) {
			if(target.getName().endsWith(".java") && isTestClass(read(target))) {
				result.add(target);
			}
			return result;
		}
		List<File> files = new ArrayList<File>();
		for(File src : sourceFolders) {
			if(inside(target, src)) {
				walk(target, files);
			} else if(inside(src, target)) {
				walk(src, files);
			}
		}
		for(Map.Entry<File, Parsed> e : parsedFiles(files).entrySet()) {
			if(e.getValue() != null && e.getValue().isTestClass() && !result.contains(e.getKey())) {
				result.add(e.getKey());
			}
		}
		return result;
	}

	public static List<File> findAll(List<File> sourceFolders) {
		List<File> result = new ArrayList<File>();
		for(Map.Entry<File, Parsed> e : parsedFiles(javaFiles(sourceFolders)).entrySet()) {
			if(e.getValue() != null && e.getValue().isTestClass()) {
				result.add(e.getKey());
			}
		}
		return result;
	}

	static List<File> javaFiles(List<File> dirs) {
		List<File> files = new ArrayList<File>();
		for(File dir : dirs) {
			walk(dir, files);
		}
		return files;
	}

	static Map<File, Parsed> parsedFiles(List<File> files) {
		Map<File, String> sources = new LinkedHashMap<File, String>();
		for(File f : files) {
			sources.put(f, read(f));
		}
		return parse(sources);
	}

	static boolean inside(File child, File parent) {
		String c = child.getAbsolutePath();
		String p = parent.getAbsolutePath();
		return c.equals(p) || c.startsWith(p + File.separator);
	}

	static void walk(File dir, List<File> out) {
		File[] children = dir.listFiles();
		if(children == null) {
			return;
		}
		Arrays.sort(children);
		for(File f : children) {
			if(f.isDirectory()) {
				walk(f, out);
			} else if(f.getName().endsWith(".java")) {
				out.add(f);
			}
		}
	}

	static File synthetic() {
		return new File("/tmp/TestFinder.java");
	}

	static Parsed parse(File file, String text) {
		Map<File, String> sources = new LinkedHashMap<File, String>();
		sources.put(file, text);
		return parse(sources).get(file);
	}

	// One parse (no attribution) for all the given sources, as in MainFinder.
	static Map<File, Parsed> parse(Map<File, String> sources) {
		Map<File, Parsed> result = new LinkedHashMap<File, Parsed>();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if(compiler == null || sources.isEmpty()) {
			return result;
		}
		List<JavaFileObject> units = new ArrayList<JavaFileObject>();
		for(Map.Entry<File, String> e : sources.entrySet()) {
			String text = e.getValue();
			units.add(new SimpleJavaFileObject(e.getKey().toURI(), JavaFileObject.Kind.SOURCE) {
				public CharSequence getCharContent(boolean ignoreEncodingErrors) {
					return text;
				}
			});
		}
		List<String> options = new ArrayList<String>(Arrays.asList("-proc:none", "-Xlint:none"));
		int feature = Runtime.version().feature();
		if(feature >= 21 && feature < 25) {
			options.addAll(Arrays.asList("--enable-preview", "-source", String.valueOf(feature)));
		}
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		try(StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null)) {
			JavacTask task = (JavacTask)compiler.getTask(null, fm, diagnostics, options, null, units);
			Trees trees = Trees.instance(task);
			List<File> files = new ArrayList<File>(sources.keySet());
			int i = 0;
			for(CompilationUnitTree unit : task.parse()) {
				SourcePositions positions = trees.getSourcePositions();
				result.put(files.get(i++), new Parsed(unit, positions));
			}
		} catch(Throwable e) {
			// a source that cannot be parsed has no tests
		}
		return result;
	}

	static String read(File f) {
		try {
			return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
		} catch(Exception e) {
			return "";
		}
	}
}
