package javapad.lang;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.io.File;
import java.net.URI;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

public class Navigator {
	// The declaration the code at the offset refers to (or declares).
	public static Element elementAt(JavaAnalyzer.Result r, int offset) {
		for(TreePath p = r.pathAt(offset); p != null; p = p.getParentPath()) {
			Tree leaf = p.getLeaf();
			try {
				if(leaf instanceof MethodInvocationTree) {
					Element e = r.trees.getElement(new TreePath(p, ((MethodInvocationTree)leaf).getMethodSelect()));
					if(e != null) {
						return e;
					}
				} else if(leaf instanceof IdentifierTree || leaf instanceof MemberSelectTree || leaf instanceof MethodTree || leaf instanceof ClassTree
					|| leaf instanceof VariableTree || leaf instanceof NewClassTree || leaf instanceof MemberReferenceTree) {
					Element e = r.trees.getElement(p);
					if(e != null) {
						return e;
					}
				}
			} catch(Exception ex) {
				// javac could not resolve this node; fall through to the next candidate
			}
		}
		return null;
	}

	// For a variable, the class of its type; otherwise the element itself.
	public static Element typeOrMethod(Element e) {
		if(e instanceof VariableElement && e.asType().getKind() == TypeKind.DECLARED) {
			return ((DeclaredType)e.asType()).asElement();
		}
		return e;
	}

	public static String keyOf(Element e, JavaAnalyzer.Result r) {
		if(e instanceof ExecutableElement) {
			return CodeModel.methodKey((ExecutableElement)e, r.elements, r.types);
		}
		if(e instanceof TypeElement) {
			return CodeModel.ownerName((TypeElement)e, r.elements);
		}
		return null;
	}

	public static String describe(Element e, JavaAnalyzer.Result r) {
		if(e instanceof ExecutableElement) {
			return CodeModel.methodDisplay((ExecutableElement)e, r.elements, r.types);
		}
		if(e instanceof TypeElement) {
			return ((TypeElement)e).getQualifiedName().toString();
		}
		return e.getSimpleName().toString();
	}

	// Where the element is declared in source, or null when it comes from a library.
	public static CodeModel.Place declaration(JavaAnalyzer.Result r, Element e) {
		TreePath path;
		try {
			path = r.trees.getPath(e);
		} catch(Exception ex) {
			return null;
		}
		if(path == null) {
			return null;
		}
		CompilationUnitTree unit = path.getCompilationUnit();
		String name = e.getKind() == ElementKind.CONSTRUCTOR ? e.getEnclosingElement().getSimpleName().toString() : e.getSimpleName().toString();
		long start = r.trees.getSourcePositions().getStartPosition(unit, path.getLeaf());
		if(start < 0) {
			return null;
		}
		String source;
		try {
			source = unit.getSourceFile().getCharContent(true).toString();
		} catch(Exception ex) {
			return null;
		}
		long offset = start;
		if(!name.isEmpty()) {
			int idx = source.indexOf(name, (int)start);
			while(idx >= 0) {
				boolean before = idx == 0 || !Character.isJavaIdentifierPart(source.charAt(idx - 1));
				boolean after = idx + name.length() >= source.length() || !Character.isJavaIdentifierPart(source.charAt(idx + name.length()));
				if(before && after) {
					offset = idx;
					break;
				}
				idx = source.indexOf(name, idx + 1);
			}
		}
		LineMap map = unit.getLineMap();
		long line = map.getLineNumber(offset);
		File file = new File(URI.create(unit.getSourceFile().toUri().toString()));
		return new CodeModel.Place(file, (int)line, (int)(offset - map.getStartPosition(line)) + 1, describe(e, r));
	}
}
