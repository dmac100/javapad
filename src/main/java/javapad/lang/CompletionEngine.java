package javapad.lang;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

public class CompletionEngine {
	static final String DUMMY = "__jp";
	static final Pattern IMPORT_LINE = Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]*)$");
	static final Pattern AFTER_NEW = Pattern.compile("\\bnew\\s+$");
	static final int LIMIT = 1500;
	static final String[] KEYWORDS = {"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "continue", "default", "do",
		"double", "else", "enum", "extends", "final", "finally", "float", "for", "if", "implements", "import", "instanceof", "int", "interface", "long",
		"new", "package", "private", "protected", "public", "return", "short", "static", "super", "switch", "synchronized", "this", "throw", "throws",
		"try", "void", "volatile", "while", "true", "false", "null"};
	private final JavaAnalyzer analyzer;
	private final Supplier<TypeIndex> indexSupplier;

	public static class Completions {
		public final int start;
		public final List<Proposal> proposals;

		public Completions(int start, List<Proposal> proposals) {
			this.start = start;
			this.proposals = proposals;
		}
	}

	public CompletionEngine(JavaAnalyzer analyzer, Supplier<TypeIndex> index) {
		this.analyzer = analyzer;
		this.indexSupplier = index;
	}

	static boolean isIdent(char c) {
		return Character.isJavaIdentifierPart(c);
	}

	public Completions propose(File file, String text, int offset) {
		TypeIndex index = indexSupplier.get();
		if(index == null) {
			index = new TypeIndex(Collections.<TypeIndex.Entry>emptyList());
		}
		int start = offset;
		while(start > 0 && isIdent(text.charAt(start - 1))) {
			start--;
		}
		String prefix = text.substring(start, offset);
		int lineStart = text.lastIndexOf('\n', offset - 1) + 1;
		String lineBefore = text.substring(lineStart, offset);
		Matcher im = IMPORT_LINE.matcher(lineBefore);
		if(im.matches()) {
			return importProposals(index, im.group(2), offset);
		}
		int lineEnd = text.indexOf('\n', offset);
		String rest = text.substring(offset, lineEnd < 0 ? text.length() : lineEnd).trim();
		// a call-shaped placeholder is a valid statement and expression, so javac keeps the surrounding tree intact
		String placeholder = rest.startsWith("(") ? DUMMY : DUMMY + "()" + (rest.isEmpty() || rest.startsWith("}") ? ";" : "");
		String patched = text.substring(0, start) + placeholder + text.substring(offset);
		JavaAnalyzer.Result r = analyzer.analyze(file, patched);
		List<Proposal> out = new ArrayList<Proposal>();
		if(r != null) {
			TreePath path = r.pathAt(start + 1);
			Tree leaf = path.getLeaf();
			if(leaf instanceof MemberSelectTree && ((MemberSelectTree)leaf).getIdentifier().contentEquals(DUMMY)) {
				memberProposals(r, index, path, (MemberSelectTree)leaf, prefix, out);
			} else {
				String beforeWord = text.substring(lineStart, start);
				boolean annotation = beforeWord.endsWith("@");
				boolean typesOnly = annotation || AFTER_NEW.matcher(beforeWord).find();
				identifierProposals(r, index, path, text, prefix, typesOnly, annotation, out);
			}
		}
		return new Completions(start, finish(out));
	}

	static List<Proposal> finish(List<Proposal> proposals) {
		Map<String, Proposal> unique = new LinkedHashMap<String, Proposal>();
		for(Proposal p : proposals) {
			unique.putIfAbsent(p.kind + ":" + p.label, p);
		}
		List<Proposal> list = new ArrayList<Proposal>(unique.values());
		list.sort(Comparator.comparingInt((Proposal p) -> p.rank).thenComparing(p -> p.name.toLowerCase()).thenComparing(p -> p.label));
		return list.size() > LIMIT ? new ArrayList<Proposal>(list.subList(0, LIMIT)) : list;
	}

	Completions importProposals(TypeIndex index, String qualified, int offset) {
		int dot = qualified.lastIndexOf('.');
		String qualifier = dot < 0 ? "" : qualified.substring(0, dot);
		String prefix = qualified.substring(dot + 1);
		List<Proposal> out = new ArrayList<Proposal>();
		for(String child : index.childPackages(qualifier)) {
			if(Names.matches(prefix, child)) {
				out.add(new Proposal(Proposal.PACKAGE, child, child, qualifier, child + ".", null, false, 10));
			}
		}
		for(TypeIndex.Entry e : index.typesIn(qualifier)) {
			if(Names.matches(prefix, e.simple)) {
				out.add(new Proposal(Proposal.TYPE, e.simple, e.simple, e.pkg, e.simple + ";", null, false, 20));
			}
		}
		return new Completions(offset - prefix.length(), finish(out));
	}

	static Scope scopeAt(Trees trees, TreePath path) {
		try {
			return trees.getScope(path);
		} catch(Throwable e) {
			return null;
		}
	}

	TypeElement typeElementOf(TypeMirror tm) {
		if(tm == null) {
			return null;
		}
		if(tm.getKind() == TypeKind.DECLARED) {
			return (TypeElement)((DeclaredType)tm).asElement();
		}
		if(tm.getKind() == TypeKind.TYPEVAR) {
			return typeElementOf(((TypeVariable)tm).getUpperBound());
		}
		if(tm.getKind() == TypeKind.INTERSECTION) {
			return null;
		}
		return null;
	}

	void memberProposals(JavaAnalyzer.Result r, TypeIndex index, TreePath path, MemberSelectTree ms, String prefix, List<Proposal> out) {
		TreePath qualifierPath = new TreePath(path, ms.getExpression());
		Element qualifier = r.trees.getElement(qualifierPath);
		TypeMirror type = r.trees.getTypeMirror(qualifierPath);
		Scope scope = scopeAt(r.trees, path);
		String qualifierName = ms.getExpression().toString();
		boolean unresolved = qualifier == null || type == null || type.getKind() == TypeKind.ERROR;
		if(qualifier instanceof PackageElement || (unresolved && index.hasPackage(qualifierName))) {
			String pkg = qualifier instanceof PackageElement ? ((PackageElement)qualifier).getQualifiedName().toString() : qualifierName;
			for(String child : index.childPackages(pkg)) {
				if(Names.matches(prefix, child)) {
					out.add(new Proposal(Proposal.PACKAGE, child, child, pkg, child, null, false, 10));
				}
			}
			for(TypeIndex.Entry e : index.typesIn(pkg)) {
				if(Names.matches(prefix, e.simple)) {
					out.add(new Proposal(Proposal.TYPE, e.simple, e.simple, e.pkg, e.simple, null, false, 20));
				}
			}
			return;
		}
		if(unresolved) {
			return;
		}
		boolean staticContext = qualifier != null && (qualifier.getKind().isClass() || qualifier.getKind().isInterface());
		if(type.getKind() == TypeKind.ARRAY) {
			if(Names.matches(prefix, "length")) {
				out.add(new Proposal(Proposal.FIELD, "length", "length : int", "array", "length", null, false, 10));
			}
			TypeElement object = r.elements.getTypeElement("java.lang.Object");
			addMembers(r, scope, (DeclaredType)object.asType(), object, false, prefix, out);
			return;
		}
		TypeElement te = typeElementOf(type);
		if(te == null) {
			return;
		}
		DeclaredType dt = type.getKind() == TypeKind.DECLARED ? (DeclaredType)type : (DeclaredType)te.asType();
		addMembers(r, scope, dt, te, staticContext, prefix, out);
	}

	void addMembers(JavaAnalyzer.Result r, Scope scope, DeclaredType dt, TypeElement te, boolean staticContext, String prefix, List<Proposal> out) {
		for(Element m : r.elements.getAllMembers(te)) {
			ElementKind kind = m.getKind();
			if(kind == ElementKind.CONSTRUCTOR || kind == ElementKind.STATIC_INIT || kind == ElementKind.INSTANCE_INIT) {
				continue;
			}
			String name = m.getSimpleName().toString();
			if(!Names.matches(prefix, name)) {
				continue;
			}
			boolean isStatic = m.getModifiers().contains(Modifier.STATIC);
			boolean nestedType = kind.isClass() || kind.isInterface();
			if(nestedType ? !staticContext : (staticContext && !isStatic)) {
				continue;
			}
			if(scope != null) {
				try {
					if(!r.trees.isAccessible(scope, m, dt)) {
						continue;
					}
				} catch(Exception e) {
					// if accessibility cannot be decided, offer the member
				}
			}
			Element owner = m.getEnclosingElement();
			int rank = owner.equals(te) ? 10 : owner.toString().equals("java.lang.Object") ? 12 : 11;
			out.add(proposalFor(r, m, dt, rank));
		}
	}

	Proposal proposalFor(JavaAnalyzer.Result r, Element m, DeclaredType dt, int rank) {
		String name = m.getSimpleName().toString();
		String owner = m.getEnclosingElement().getSimpleName().toString();
		if(m instanceof ExecutableElement) {
			ExecutableElement method = (ExecutableElement)m;
			List<? extends TypeMirror> paramTypes;
			TypeMirror returnType;
			try {
				ExecutableType et = (ExecutableType)r.types.asMemberOf(dt, m);
				paramTypes = et.getParameterTypes();
				returnType = et.getReturnType();
			} catch(Exception e) {
				paramTypes = ((ExecutableType)method.asType()).getParameterTypes();
				returnType = method.getReturnType();
			}
			StringBuilder sb = new StringBuilder(name).append('(');
			for(int i = 0; i < paramTypes.size(); i++) {
				if(i > 0) {
					sb.append(", ");
				}
				sb.append(Names.shorten(paramTypes.get(i).toString()));
				String pn = method.getParameters().get(i).getSimpleName().toString();
				if(!pn.matches("arg\\d+")) {
					sb.append(' ').append(pn);
				}
			}
			sb.append(") : ").append(Names.shorten(returnType.toString()));
			return new Proposal(Proposal.METHOD, name, sb.toString(), owner, name, null, !paramTypes.isEmpty(), rank);
		}
		if(m instanceof VariableElement) {
			TypeMirror t = m.asType();
			try {
				t = r.types.asMemberOf(dt, m);
			} catch(Exception e) {
				// keep the declared type when it cannot be viewed as a member of the qualifier
			}
			return new Proposal(Proposal.FIELD, name, name + " : " + Names.shorten(t.toString()), owner, name, null, false, rank);
		}
		return new Proposal(Proposal.TYPE, name, name + " - " + m, owner, name, null, false, rank);
	}

	void identifierProposals(JavaAnalyzer.Result r, TypeIndex index, TreePath path, String text, String prefix, boolean typesOnly, boolean annotation, List<Proposal> out) {
		Scope scope = scopeAt(r.trees, path);
		Set<String> visibleTypes = new LinkedHashSet<String>();
		if(scope != null) {
			Set<TypeElement> classes = new LinkedHashSet<TypeElement>();
			for(Scope s = scope; s != null; s = s.getEnclosingScope()) {
				TypeElement ec = s.getEnclosingClass();
				if(ec != null) {
					classes.add(ec);
				}
				for(Element e : s.getLocalElements()) {
					String name = e.getSimpleName().toString();
					if(!Names.matches(prefix, name) || name.equals(DUMMY)) {
						continue;
					}
					ElementKind kind = e.getKind();
					if(e instanceof TypeElement && (kind.isClass() || kind.isInterface())) {
						if(annotation && kind != ElementKind.ANNOTATION_TYPE) {
							continue;
						}
						visibleTypes.add(((TypeElement)e).getQualifiedName().toString());
						out.add(new Proposal(Proposal.TYPE, name, name + " - " + ((TypeElement)e).getQualifiedName(), "", name, null, false, 20));
					} else if(!typesOnly && e instanceof VariableElement && (kind == ElementKind.LOCAL_VARIABLE || kind == ElementKind.PARAMETER
						|| kind == ElementKind.EXCEPTION_PARAMETER || kind == ElementKind.RESOURCE_VARIABLE || kind.name().equals("BINDING_VARIABLE"))) {
						out.add(new Proposal(Proposal.LOCAL, name, name + " : " + Names.shorten(e.asType().toString()), "local", name, null, false, 0));
					}
				}
			}
			if(!typesOnly) {
				for(TypeElement cls : classes) {
					DeclaredType dt = (DeclaredType)cls.asType();
					for(Element m : r.elements.getAllMembers(cls)) {
						ElementKind kind = m.getKind();
						String name = m.getSimpleName().toString();
						if(kind == ElementKind.CONSTRUCTOR || kind == ElementKind.STATIC_INIT || kind == ElementKind.INSTANCE_INIT || kind.isClass()
							|| kind.isInterface() || !Names.matches(prefix, name)) {
							continue;
						}
						try {
							if(!r.trees.isAccessible(scope, m, dt)) {
								continue;
							}
						} catch(Exception e) {
							// if accessibility cannot be decided, offer the member
						}
						out.add(proposalFor(r, m, dt, m.getEnclosingElement().equals(cls) ? 10 : 11));
					}
				}
			}
		}
		if(!prefix.isEmpty()) {
			for(TypeIndex.Entry e : index.withPrefix(prefix, 150)) {
				if(annotation && e.kind != TypeIndex.ANNOTATION) {
					continue;
				}
				if(visibleTypes.contains(e.fqn)) {
					continue;
				}
				boolean visible = ImportEditor.isVisible(text, e.fqn, e.pkg);
				int rank = visible ? 20 : e.origin == TypeIndex.PROJECT ? 21 : 22;
				out.add(new Proposal(Proposal.TYPE, e.simple, e.simple + " - " + e.pkg, e.pkg, e.simple, visible ? null : e.fqn, false, rank));
			}
		}
		if(!typesOnly && !prefix.isEmpty()) {
			for(String kw : KEYWORDS) {
				if(kw.startsWith(prefix) && !kw.equals(prefix)) {
					out.add(new Proposal(Proposal.KEYWORD, kw, kw, "keyword", kw, null, false, 30));
				}
			}
		}
	}
}
