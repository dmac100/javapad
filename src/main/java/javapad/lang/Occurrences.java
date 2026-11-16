package javapad.lang;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;

// The other places where the identifier at a position refers to the same thing: the same local variable, field, method or class,
// not just the same letters. Falls back to matching whole words in code when javac cannot resolve the identifier.
public class Occurrences
{
	// Ranges {start, end} (end exclusive) of every use of the identifier text[offset, offset + length), in order.
	public static List<int[]> find(JavaAnalyzer analyzer, File file, String text, int offset, int length)
	{
		String name = text.substring(offset, offset + length);
		JavaAnalyzer.Result r = analyzer == null ? null : analyzer.analyze(file, text);
		if(r != null)
		{
			List<int[]> found = semantic(r, name, offset, length);
			if(found != null)
			{
				return found;
			}
		}
		return textual(text, name);
	}

	// null if the identifier does not resolve to a declaration
	static List<int[]> semantic(JavaAnalyzer.Result r, String name, int offset, int length)
	{
		Element target = Navigator.elementAt(r, offset);
		if(target == null && length > 1)
		{
			target = Navigator.elementAt(r, offset + 1);
		}
		if(target == null)
		{
			return null;
		}
		Element wanted = canonical(target);
		if(wanted.getKind() == ElementKind.OTHER || wanted.asType().getKind() == TypeKind.ERROR)
		{
			// javac makes up a new error symbol for every use of a name it cannot find
			return null;
		}
		if(!wanted.getSimpleName().contentEquals(name))
		{
			// the selection is not this element's name (an unresolved name inside a method, say), so match the words instead
			return null;
		}
		TreeMap<Integer, int[]> found = new TreeMap<Integer, int[]>();
		SourcePositions sp = r.trees.getSourcePositions();
		String text = r.text;
		new TreePathScanner<Void, Void>()
		{
			boolean matches()
			{
				try
				{
					Element e = r.trees.getElement(getCurrentPath());
					return e != null && canonical(e).equals(wanted);
				}
				catch(Exception ex)
				{
					return false;
				}
			}

			void add(int start)
			{
				if(start >= 0 && start + name.length() <= text.length() && text.startsWith(name, start))
				{
					found.put(start, new int[] {start, start + name.length()});
				}
			}

			// the name of a declaration: the first whole word equal to it at or after a position
			void addWordFrom(long from)
			{
				if(from < 0)
				{
					return;
				}
				int i = text.indexOf(name, (int)from);
				while(i >= 0)
				{
					boolean before = i == 0 || !Character.isJavaIdentifierPart(text.charAt(i - 1));
					boolean after = i + name.length() >= text.length() || !Character.isJavaIdentifierPart(text.charAt(i + name.length()));
					if(before && after)
					{
						add(i);
						return;
					}
					i = text.indexOf(name, i + 1);
				}
			}

			public Void visitIdentifier(IdentifierTree n, Void p)
			{
				if(n.getName().contentEquals(name) && matches())
				{
					add((int)sp.getStartPosition(r.unit, n));
				}
				return super.visitIdentifier(n, p);
			}

			public Void visitMemberSelect(MemberSelectTree n, Void p)
			{
				if(n.getIdentifier().contentEquals(name) && matches())
				{
					add((int)sp.getEndPosition(r.unit, n) - name.length());
				}
				return super.visitMemberSelect(n, p);
			}

			public Void visitMemberReference(MemberReferenceTree n, Void p)
			{
				if(n.getName().contentEquals(name) && matches())
				{
					add((int)sp.getEndPosition(r.unit, n) - name.length());
				}
				return super.visitMemberReference(n, p);
			}

			public Void visitVariable(VariableTree n, Void p)
			{
				if(n.getName().contentEquals(name) && matches())
				{
					Tree type = n.getType();
					long from = type == null ? -1 : sp.getEndPosition(r.unit, type);
					addWordFrom(from >= 0 ? from : sp.getStartPosition(r.unit, n));
				}
				return super.visitVariable(n, p);
			}

			public Void visitMethod(MethodTree n, Void p)
			{
				String methodName = n.getName().contentEquals("<init>") ? wanted.getSimpleName().toString() : n.getName().toString();
				if(methodName.equals(name) && matches())
				{
					Tree type = n.getReturnType();
					long from = type == null ? -1 : sp.getEndPosition(r.unit, type);
					// a constructor that javac added itself has no text of its own
					if(!(type == null && sp.getStartPosition(r.unit, n) == sp.getEndPosition(r.unit, n)))
					{
						addWordFrom(from >= 0 ? from : sp.getStartPosition(r.unit, n));
					}
				}
				return super.visitMethod(n, p);
			}

			public Void visitClass(ClassTree n, Void p)
			{
				if(n.getSimpleName().contentEquals(name) && matches())
				{
					long start = sp.getStartPosition(r.unit, n);
					if(start >= 0)
					{
						Matcher m = Pattern.compile("\\b(?:class|interface|enum|record)\\s+(" + Pattern.quote(name) + ")\\b").matcher(text);
						if(m.find((int)start))
						{
							add(m.start(1));
						}
					}
				}
				return super.visitClass(n, p);
			}

			public Void visitTypeParameter(TypeParameterTree n, Void p)
			{
				if(n.getName().contentEquals(name) && matches())
				{
					add((int)sp.getStartPosition(r.unit, n));
				}
				return super.visitTypeParameter(n, p);
			}
		}.scan(r.unit, null);
		return new ArrayList<int[]>(found.values());
	}

	// a constructor stands for its class
	static Element canonical(Element e)
	{
		return e.getKind() == ElementKind.CONSTRUCTOR ? e.getEnclosingElement() : e;
	}

	// every whole word equal to the name that is real code (not in a string or comment)
	static List<int[]> textual(String text, String name)
	{
		List<int[]> list = new ArrayList<int[]>();
		boolean[] code = CodeMask.flags(text);
		int i = text.indexOf(name);
		while(i >= 0)
		{
			boolean before = i == 0 || !Character.isJavaIdentifierPart(text.charAt(i - 1));
			boolean after = i + name.length() >= text.length() || !Character.isJavaIdentifierPart(text.charAt(i + name.length()));
			if(before && after && i < code.length && code[i])
			{
				list.add(new int[] {i, i + name.length()});
			}
			i = text.indexOf(name, i + 1);
		}
		return list;
	}
}
