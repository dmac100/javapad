package javapad.build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javapad.model.EclipseProject;
import javapad.model.Problem;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class ProjectCompiler
{
	public static List<Problem> compile(EclipseProject project)
	{
		List<Problem> problems = new ArrayList<Problem>();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if(compiler == null)
		{
			problems.add(new Problem("", 0, 0, true, "No Java compiler available; run JavaPad with a JDK"));
			return problems;
		}
		List<File> sources = new ArrayList<File>();
		for(File src : project.sourceFolders)
		{
			collect(src, ".java", sources);
		}
		project.outputFolder.mkdirs();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null);
		try
		{
			List<String> options = new ArrayList<String>();
			options.add("-d");
			options.add(project.outputFolder.getAbsolutePath());
			options.add("-cp");
			options.add(project.classpathString());
			options.add("-g");
			options.add("-encoding");
			options.add("UTF-8");
			options.add("-proc:none");
			options.add("-Xlint:-options");
			if(project.release > 0)
			{
				options.add("--release");
				options.add(String.valueOf(project.release));
			}
			Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromFiles(sources);
			compiler.getTask(null, fm, diagnostics, options, null, units).call();
		}
		finally
		{
			try
			{
				fm.close();
			}
			catch(IOException e)
			{
			}
		}
		for(Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics())
		{
			if(d.getKind() == Diagnostic.Kind.NOTE)
			{
				continue;
			}
			String file = d.getSource() == null ? "" : new File(d.getSource().toUri()).getAbsolutePath();
			boolean error = d.getKind() == Diagnostic.Kind.ERROR;
			String message = d.getMessage(Locale.ENGLISH);
			int nl = message.indexOf('\n');
			if(nl > 0)
			{
				message = message.substring(0, nl);
			}
			problems.add(new Problem(file, (int)d.getLineNumber(), column(d), error, message));
		}
		copyResources(project);
		return problems;
	}

	static int column(Diagnostic<? extends JavaFileObject> d)
	{
		int fallback = (int)d.getColumnNumber();
		if(d.getSource() == null || d.getPosition() < 0)
		{
			return fallback;
		}
		try
		{
			CharSequence content = d.getSource().getCharContent(true);
			int pos = (int)d.getPosition();
			int start = pos;
			while(start > 0 && content.charAt(start - 1) != '\n')
			{
				start--;
			}
			return pos - start + 1;
		}
		catch(IOException e)
		{
			return fallback;
		}
	}

	static void collect(File dir, String suffix, List<File> out)
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
				collect(f, suffix, out);
			}
			else if(f.getName().endsWith(suffix))
			{
				out.add(f);
			}
		}
	}

	static void copyResources(EclipseProject project)
	{
		for(File src : project.sourceFolders)
		{
			if(src.equals(project.root))
			{
				continue;
			}
			Path base = src.toPath();
			try(Stream<Path> walk = Files.walk(base))
			{
				walk.filter(Files::isRegularFile).forEach(p ->
				{
					String name = p.getFileName().toString();
					if(name.endsWith(".java") || name.endsWith(".class"))
					{
						return;
					}
					Path target = project.outputFolder.toPath().resolve(base.relativize(p));
					try
					{
						Files.createDirectories(target.getParent());
						Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
					}
					catch(IOException e)
					{
					}
				});
			}
			catch(IOException e)
			{
			}
		}
	}
}
