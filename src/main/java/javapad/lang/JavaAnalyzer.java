package javapad.lang;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javapad.build.ProjectCompiler;
import javapad.model.EclipseProject;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

// Parses and attributes one buffer (plus whatever it references on the source path and classpath) with javac,
// tolerating errors, and exposes the Tree API objects needed to look things up.
public class JavaAnalyzer
{
	public static class Result
	{
		public JavacTask task;
		public CompilationUnitTree unit;
		public Trees trees;
		public Elements elements;
		public Types types;
		public String text;
		public List<Diagnostic<? extends JavaFileObject>> diagnostics;

		public TreePath pathAt(int position)
		{
			SourcePositions sp = trees.getSourcePositions();
			TreePath[] best = {new TreePath(unit)};
			new TreePathScanner<Void, Void>()
			{
				public Void scan(Tree tree, Void p)
				{
					if(tree != null)
					{
						long start = sp.getStartPosition(unit, tree);
						long end = sp.getEndPosition(unit, tree);
						if(start >= 0 && start <= position && position <= end)
						{
							best[0] = new TreePath(getCurrentPath(), tree);
						}
					}
					return super.scan(tree, p);
				}
			}.scan(new TreePath(unit), null);
			return best[0];
		}
	}

	private final EclipseProject project;
	private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
	private StandardJavaFileManager fileManager;

	public JavaAnalyzer(EclipseProject project)
	{
		this.project = project;
	}

	public EclipseProject project()
	{
		return project;
	}

	// Forget cached directory and jar listings, e.g. after files changed on disk.
	public synchronized void invalidate()
	{
		if(fileManager != null)
		{
			try
			{
				fileManager.close();
			}
			catch(IOException e)
			{
			}
			fileManager = null;
		}
	}

	public synchronized Result analyze(File file, String text)
	{
		if(compiler == null)
		{
			return null;
		}
		if(fileManager == null)
		{
			fileManager = compiler.getStandardFileManager(null, null, null);
		}
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		List<String> options = new ArrayList<String>();
		options.add("-proc:none");
		options.add("-Xlint:none");
		options.add("-XDshould-stop.at=FLOW");
		options.add("-XDshould-stop.ifError=FLOW");
		options.add("-cp");
		options.add(project.classpathString());
		StringBuilder sourcePath = new StringBuilder();
		for(File src : project.sourceFolders)
		{
			if(sourcePath.length() > 0)
			{
				sourcePath.append(File.pathSeparator);
			}
			sourcePath.append(src.getAbsolutePath());
		}
		if(sourcePath.length() > 0)
		{
			options.add("-sourcepath");
			options.add(sourcePath.toString());
		}
		int release = ProjectCompiler.effectiveRelease(project.release, compiler);
		if(release > 0)
		{
			options.add("-source");
			options.add(String.valueOf(release));
			options.add("-target");
			options.add(String.valueOf(release));
		}
		URI uri = file != null ? file.toURI() : URI.create("file:///Untitled.java");
		JavaFileObject source = new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE)
		{
			public CharSequence getCharContent(boolean ignoreEncodingErrors)
			{
				return text;
			}
		};
		try
		{
			JavacTask task = (JavacTask)compiler.getTask(null, fileManager, diagnostics, options, null, java.util.Arrays.asList(source));
			Iterable<? extends CompilationUnitTree> units = task.parse();
			CompilationUnitTree unit = units.iterator().next();
			task.analyze();
			Result r = new Result();
			r.task = task;
			r.unit = unit;
			r.trees = Trees.instance(task);
			r.elements = task.getElements();
			r.types = task.getTypes();
			r.text = text;
			r.diagnostics = diagnostics.getDiagnostics();
			return r;
		}
		catch(Exception | StackOverflowError e)
		{
			return null;
		}
	}
}
