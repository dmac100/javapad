package javapad.lang;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

// The nested ranges of code a selection can grow to or shrink to: a word, an expression, a statement, the inside of a block,
// the block, a method, a class, the whole file. Ranges are {start, end} with end exclusive.
public class SelectionRanges
{
	// The smallest range that strictly contains [start, end); the whole text if nothing smaller does.
	public static int[] expand(String text, int start, int end, boolean java)
	{
		int[] best = null;
		for(int[] r : candidates(text, start, end, java))
		{
			boolean contains = r[0] <= start && end <= r[1] && r[1] - r[0] > end - start;
			if(contains && (best == null || r[1] - r[0] < best[1] - best[0]))
			{
				best = r;
			}
		}
		return best == null ? new int[] {start, end} : best;
	}

	// The largest range strictly inside [start, end) that holds the offset; an empty range at the offset if there is none.
	public static int[] shrink(String text, int start, int end, int offset, boolean java)
	{
		int[] best = null;
		for(int[] r : candidates(text, start, end, java))
		{
			boolean inside = start <= r[0] && r[1] <= end && r[1] - r[0] < end - start;
			boolean holds = r[0] <= offset && offset <= r[1];
			if(inside && holds && (best == null || r[1] - r[0] > best[1] - best[0]))
			{
				best = r;
			}
		}
		return best == null ? new int[] {offset, offset} : best;
	}

	static List<int[]> candidates(String text, int start, int end, boolean java)
	{
		List<int[]> list = new ArrayList<int[]>();
		addWord(text, start, end, list);
		if(java)
		{
			addTrees(text, list);
		}
		else
		{
			addLines(text, start, end, list);
		}
		addBrackets(text, list);
		list.add(new int[] {0, text.length()});
		return list;
	}

	// the identifier (or number) the selection lies in
	static void addWord(String text, int start, int end, List<int[]> list)
	{
		int s = start;
		int e = end;
		while(s > 0 && Character.isJavaIdentifierPart(text.charAt(s - 1)))
		{
			s--;
		}
		while(e < text.length() && Character.isJavaIdentifierPart(text.charAt(e)))
		{
			e++;
		}
		if(e > s && text.substring(start, end).chars().allMatch(c -> Character.isJavaIdentifierPart(c)))
		{
			list.add(new int[] {s, e});
		}
	}

	// the lines the selection touches, without their indentation and line break
	static void addLines(String text, int start, int end, List<int[]> list)
	{
		int s = text.lastIndexOf('\n', start - 1) + 1;
		int e = text.indexOf('\n', Math.max(start, end - 1));
		if(e < 0)
		{
			e = text.length();
		}
		add(text, s, e, true, list);
	}

	// matching (), [] and {} in code, both the brackets and what is between them
	static void addBrackets(String text, List<int[]> list)
	{
		boolean[] code = CodeMask.flags(text);
		List<Integer> open = new ArrayList<Integer>();
		for(int i = 0; i < text.length(); i++)
		{
			if(i < code.length && !code[i])
			{
				continue;
			}
			char c = text.charAt(i);
			if(c == '(' || c == '[' || c == '{')
			{
				open.add(i);
			}
			else if((c == ')' || c == ']' || c == '}') && !open.isEmpty())
			{
				int o = open.remove(open.size() - 1);
				list.add(new int[] {o, i + 1});
				add(text, o + 1, i, true, list);
			}
		}
	}

	// every node javac's parser finds, plus the inside of blocks, class bodies, argument lists and string literals
	static void addTrees(String text, List<int[]> list)
	{
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if(compiler == null)
		{
			return;
		}
		JavaFileObject source = new SimpleJavaFileObject(URI.create("string:///Selection.java"), JavaFileObject.Kind.SOURCE)
		{
			public CharSequence getCharContent(boolean ignoreEncodingErrors)
			{
				return text;
			}
		};
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		try(StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null))
		{
			List<String> options = Arrays.asList("-proc:none", "-Xlint:none");
			JavacTask task = (JavacTask)compiler.getTask(null, fm, diagnostics, options, null, Arrays.asList(source));
			SourcePositions sp = Trees.instance(task).getSourcePositions();
			for(CompilationUnitTree unit : task.parse())
			{
				new TreeScanner<Void, Void>()
				{
					public Void scan(Tree tree, Void p)
					{
						if(tree != null)
						{
							add(text, (int)sp.getStartPosition(unit, tree), (int)sp.getEndPosition(unit, tree), false, list);
						}
						return super.scan(tree, p);
					}

					public Void visitBlock(BlockTree n, Void p)
					{
						int s = (int)sp.getStartPosition(unit, n);
						int e = (int)sp.getEndPosition(unit, n);
						if(s >= 0 && e > s && text.charAt(e - 1) == '}')
						{
							add(text, text.indexOf('{', s) + 1, e - 1, true, list);
						}
						return super.visitBlock(n, p);
					}

					public Void visitClass(ClassTree n, Void p)
					{
						addSpan(n.getMembers());
						return super.visitClass(n, p);
					}

					public Void visitMethod(MethodTree n, Void p)
					{
						addSpan(n.getParameters());
						return super.visitMethod(n, p);
					}

					public Void visitMethodInvocation(MethodInvocationTree n, Void p)
					{
						addSpan(n.getArguments());
						return super.visitMethodInvocation(n, p);
					}

					public Void visitNewClass(NewClassTree n, Void p)
					{
						addSpan(n.getArguments());
						return super.visitNewClass(n, p);
					}

					public Void visitLiteral(LiteralTree n, Void p)
					{
						int s = (int)sp.getStartPosition(unit, n);
						int e = (int)sp.getEndPosition(unit, n);
						if(n.getValue() instanceof String && s >= 0 && e - s >= 2 && text.charAt(s) == '"')
						{
							add(text, s + 1, e - 1, false, list);
						}
						return super.visitLiteral(n, p);
					}

					// from the first of the trees to the end of the last one
					void addSpan(List<? extends Tree> trees)
					{
						int s = -1;
						int e = -1;
						for(Tree t : trees)
						{
							int ts = (int)sp.getStartPosition(unit, t);
							int te = (int)sp.getEndPosition(unit, t);
							// javac's own default constructor has no text
							if(ts >= 0 && te > ts)
							{
								s = s < 0 ? ts : Math.min(s, ts);
								e = Math.max(e, te);
							}
						}
						add(text, s, e, false, list);
					}
				}.scan(unit, null);
			}
		}
		catch(Throwable e)
		{
			// text that javac cannot parse at all still has its words, brackets and lines
		}
	}

	static void add(String text, int start, int end, boolean trim, List<int[]> list)
	{
		if(start < 0 || end > text.length() || end <= start)
		{
			return;
		}
		if(trim)
		{
			while(start < end && Character.isWhitespace(text.charAt(start)))
			{
				start++;
			}
			while(end > start && Character.isWhitespace(text.charAt(end - 1)))
			{
				end--;
			}
			if(end <= start)
			{
				return;
			}
		}
		list.add(new int[] {start, end});
	}
}
