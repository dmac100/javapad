package javapad.lang;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

// The declarations of one Java file, in source order and nested as they are written, for the outline view and the
// "go to method" popup. Parses only (no attribution), so it is quick enough to run while typing.
public class Outline {
	public enum Kind {
		TYPE, METHOD, CONSTRUCTOR, FIELD, ENUM_CONSTANT
	}

	public static class Node {
		public Kind kind;
		public String name;
		// what the outline and the popup show: "foo(int) : void", "name : Type", "Foo<T>  (class)"
		public String label;
		public String type;
		// the simple name of the type holding this member, or the outer type of a nested one
		public String owner;
		// 0 for a top-level type, 1 for its members or a nested type, and so on
		public int depth;
		public int line;
		public int column;
		public int endLine;
		public final List<Node> children = new ArrayList<Node>();

		public boolean method() {
			return kind == Kind.METHOD || kind == Kind.CONSTRUCTOR;
		}
	}

	// The declarations of a source text, top-level types first.
	public static List<Node> parse(String source) {
		List<Node> roots = new ArrayList<Node>();
		if(source == null || source.isEmpty()) {
			return roots;
		}
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if(compiler == null) {
			return roots;
		}
		JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Outline.java"), JavaFileObject.Kind.SOURCE) {
			public CharSequence getCharContent(boolean ignoreEncodingErrors) {
				return source;
			}
		};
		try {
			DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
			JavacTask task = (JavacTask)compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null, List.of(file));
			CompilationUnitTree unit = task.parse().iterator().next();
			SourcePositions positions = Trees.instance(task).getSourcePositions();
			LineMap lines = unit.getLineMap();
			SourceText text = new SourceText(source, unit, positions, lines);
			for(Tree declaration : unit.getTypeDecls()) {
				if(declaration instanceof ClassTree && named(((ClassTree)declaration).getSimpleName().toString())) {
					roots.add(type((ClassTree)declaration, null, 0, text));
				}
			}
		} catch(Throwable e) {
			// a file javac cannot parse has no outline; the editor shows the errors
			return roots;
		}
		return roots;
	}

	// Every declaration, depth first.
	public static List<Node> flatten(List<Node> nodes) {
		List<Node> all = new ArrayList<Node>();
		collect(nodes, all);
		return all;
	}

	static void collect(List<Node> nodes, List<Node> into) {
		for(Node node : nodes) {
			into.add(node);
			collect(node.children, into);
		}
	}

	// The methods and constructors, in source order, for the "go to method" popup.
	public static List<Node> methods(List<Node> nodes) {
		List<Node> methods = new ArrayList<Node>();
		for(Node node : flatten(nodes)) {
			if(node.method()) {
				methods.add(node);
			}
		}
		return methods;
	}

	// The innermost declaration whose extent contains the line (1-based), or null.
	public static Node atLine(List<Node> nodes, int line) {
		Node best = null;
		for(Node node : nodes) {
			if(line < node.line || line > node.endLine) {
				continue;
			}
			Node inner = atLine(node.children, line);
			Node found = inner != null ? inner : node;
			if(best == null || found.depth > best.depth || (found.depth == best.depth && found.line >= best.line)) {
				best = found;
			}
		}
		return best;
	}

	// The declarations whose name or label contains every word typed, best matches first: a name that starts with
	// the first word, then a name that contains it, then anything else.
	public static List<Node> search(List<Node> nodes, String query, int limit) {
		String[] words = query.toLowerCase().trim().split("\\s+");
		List<Node> best = new ArrayList<Node>();
		List<Node> good = new ArrayList<Node>();
		List<Node> rest = new ArrayList<Node>();
		for(Node node : flatten(nodes)) {
			String label = label(node, true).toLowerCase();
			String name = node.name.toLowerCase();
			boolean all = true;
			for(String word : words) {
				if(!word.isEmpty() && !label.contains(word)) {
					all = false;
					break;
				}
			}
			if(!all) {
				continue;
			}
			if(words.length == 0 || words[0].isEmpty() || name.startsWith(words[0])) {
				best.add(node);
			} else if(name.contains(words[0])) {
				good.add(node);
			} else {
				rest.add(node);
			}
		}
		best.addAll(good);
		best.addAll(rest);
		return best.size() > limit ? new ArrayList<Node>(best.subList(0, limit)) : best;
	}

	// The label to show; qualified adds the type around a member of a nested type, so that equal signatures in
	// two nested types can be told apart.
	public static String label(Node node, boolean qualified) {
		if(qualified && node.kind != Kind.TYPE && node.depth >= 2 && node.owner != null) {
			return node.owner + "." + node.label;
		}
		return node.label;
	}

	static Node type(ClassTree tree, String owner, int depth, SourceText text) {
		String name = tree.getSimpleName().toString();
		Node node = new Node();
		node.kind = Kind.TYPE;
		node.name = name;
		node.owner = owner;
		node.depth = depth;
		String kind = kindName(tree.getKind());
		node.type = kind;
		node.label = name + typeParameters(tree.getTypeParameters()) + "  (" + kind + ")";
		place(node, text.nameOffset(tree, name), tree, text);
		for(Tree member : tree.getMembers()) {
			if(member instanceof ClassTree) {
				ClassTree nested = (ClassTree)member;
				if(named(nested.getSimpleName().toString())) {
					node.children.add(type(nested, name, depth + 1, text));
				}
			} else if(member instanceof MethodTree) {
				node.children.add(method((MethodTree)member, name, depth + 1, text));
			} else if(member instanceof VariableTree) {
				node.children.add(field((VariableTree)member, name, depth + 1, tree.getKind() == Tree.Kind.ENUM, text));
			} else if(member instanceof BlockTree) {
				// initializer blocks have no name to show
				continue;
			}
		}
		return node;
	}

	static Node method(MethodTree tree, String owner, int depth, SourceText text) {
		Node node = new Node();
		boolean constructor = tree.getName().contentEquals("<init>");
		node.kind = constructor ? Kind.CONSTRUCTOR : Kind.METHOD;
		node.name = constructor ? owner : tree.getName().toString();
		node.owner = owner;
		node.depth = depth;
		String parameters = parameters(tree);
		if(constructor) {
			node.type = "constructor";
			node.label = owner + parameters;
		} else {
			String returns = tree.getReturnType() == null ? "" : tree.getReturnType().toString();
			node.type = returns;
			node.label = tree.getName() + typeParameters(tree.getTypeParameters()) + parameters + (returns.isEmpty() ? "" : " : " + returns);
		}
		place(node, text.nameOffset(tree, node.name), tree, text);
		return node;
	}

	static Node field(VariableTree tree, String owner, int depth, boolean inEnum, SourceText text) {
		Node node = new Node();
		String type = tree.getType() == null ? "" : tree.getType().toString();
		boolean constant = inEnum && type.equals(owner) && tree.getInitializer() != null && tree.getInitializer().getKind() == Tree.Kind.NEW_CLASS;
		node.kind = constant ? Kind.ENUM_CONSTANT : Kind.FIELD;
		node.name = tree.getName().toString();
		node.owner = owner;
		node.depth = depth;
		node.type = type;
		node.label = constant ? node.name : node.name + (type.isEmpty() ? "" : " : " + type);
		place(node, text.nameOffset(tree, node.name), tree, text);
		return node;
	}

	static String parameters(MethodTree tree) {
		StringBuilder sb = new StringBuilder("(");
		boolean first = true;
		for(VariableTree parameter : tree.getParameters()) {
			if(!first) {
				sb.append(", ");
			}
			first = false;
			sb.append(parameter.getType() == null ? "?" : parameter.getType().toString());
		}
		return sb.append(')').toString();
	}

	static String typeParameters(List<? extends TypeParameterTree> parameters) {
		if(parameters.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("<");
		boolean first = true;
		for(TypeParameterTree parameter : parameters) {
			if(!first) {
				sb.append(", ");
			}
			first = false;
			sb.append(parameter.getName());
		}
		return sb.append('>').toString();
	}

	// javac names what it could not parse "<error>"; such a type has nothing to show.
	static boolean named(String name) {
		return !name.isEmpty() && Character.isJavaIdentifierStart(name.charAt(0));
	}

	static String kindName(Tree.Kind kind) {
		switch(kind) {
			case INTERFACE:
				return "interface";
			case ENUM:
				return "enum";
			case RECORD:
				return "record";
			case ANNOTATION_TYPE:
				return "@interface";
			default:
				return "class";
		}
	}

	static void place(Node node, long offset, Tree tree, SourceText text) {
		long start = offset;
		if(start < 0) {
			start = text.positions.getStartPosition(text.unit, tree);
		}
		if(start < 0) {
			start = 0;
		}
		node.line = (int)text.lines.getLineNumber(start);
		node.column = (int)(start - text.lines.getStartPosition(node.line)) + 1;
		long end = text.positions.getEndPosition(text.unit, tree);
		node.endLine = end < 0 ? node.line : (int)text.lines.getLineNumber(end);
		if(node.endLine < node.line) {
			node.endLine = node.line;
		}
	}

	// The source, and where its lines and declarations start.
	static class SourceText {
		final String source;
		final CompilationUnitTree unit;
		final SourcePositions positions;
		final LineMap lines;

		SourceText(String source, CompilationUnitTree unit, SourcePositions positions, LineMap lines) {
			this.source = source;
			this.unit = unit;
			this.positions = positions;
			this.lines = lines;
		}

		// The offset of a declaration's name, found as a whole word after its modifiers and annotations.
		long nameOffset(Tree tree, String name) {
			long start = positions.getStartPosition(unit, tree);
			if(start < 0 || name == null || name.isEmpty()) {
				return start;
			}
			int index = source.indexOf(name, (int)start);
			while(index >= 0) {
				boolean before = index == 0 || !Character.isJavaIdentifierPart(source.charAt(index - 1));
				boolean after = index + name.length() >= source.length() || !Character.isJavaIdentifierPart(source.charAt(index + name.length()));
				if(before && after) {
					return index;
				}
				index = source.indexOf(name, index + 1);
			}
			return start;
		}
	}
}
