package javapad.model;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ClasspathParser
{
	public static File bundledLib = new File(System.getProperty("javapad.lib", "lib"));

	public static File bundled(String name)
	{
		File file = new File(bundledLib, name).getAbsoluteFile();
		if(file.exists())
		{
			return file;
		}
		try
		{
			return new File(ClasspathParser.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsoluteFile();
		}
		catch(Exception e)
		{
			return file;
		}
	}

	public static EclipseProject parse(File dir) throws Exception
	{
		EclipseProject project = new EclipseProject(dir.getAbsoluteFile());
		File dotProject = new File(dir, ".project");
		if(dotProject.exists())
		{
			Document doc = read(dotProject);
			NodeList names = doc.getDocumentElement().getElementsByTagName("name");
			if(names.getLength() > 0)
			{
				project.name = names.item(0).getTextContent().trim();
			}
		}
		project.release = readRelease(project.root);
		Set<File> visited = new HashSet<File>();
		visited.add(project.root);
		addEntries(project, project.root, true, visited);
		if(project.outputFolder == null)
		{
			project.outputFolder = new File(project.root, "bin");
		}
		if(project.sourceFolders.isEmpty())
		{
			project.warnings.add("No source folders in .classpath");
		}
		return project;
	}

	static int readRelease(File root)
	{
		File prefs = new File(root, ".settings/org.eclipse.jdt.core.prefs");
		if(!prefs.exists())
		{
			return 0;
		}
		try
		{
			Properties p = new Properties();
			FileInputStream in = new FileInputStream(prefs);
			p.load(in);
			in.close();
			String source = p.getProperty("org.eclipse.jdt.core.compiler.source");
			if(source == null)
			{
				return 0;
			}
			if(source.startsWith("1."))
			{
				source = source.substring(2);
			}
			return Integer.parseInt(source);
		}
		catch(Exception e)
		{
			return 0;
		}
	}

	static Document read(File f) throws Exception
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		return factory.newDocumentBuilder().parse(f);
	}

	static File resolve(File root, String path)
	{
		File f = new File(path);
		if(f.isAbsolute())
		{
			return f;
		}
		return new File(root, path);
	}

	static void addEntries(EclipseProject project, File dir, boolean main, Set<File> visited) throws Exception
	{
		File cp = new File(dir, ".classpath");
		if(!cp.exists())
		{
			project.warnings.add("Missing .classpath in " + dir);
			return;
		}
		NodeList entries = read(cp).getDocumentElement().getElementsByTagName("classpathentry");
		for(int i = 0; i < entries.getLength(); i++)
		{
			Element e = (Element)entries.item(i);
			String kind = e.getAttribute("kind");
			String path = e.getAttribute("path");
			switch(kind)
			{
			case "src":
				addSource(project, dir, path, main, visited);
				break;
			case "lib":
				project.libraries.add(resolve(dir, path).getAbsoluteFile());
				break;
			case "output":
				if(main)
				{
					project.outputFolder = resolve(dir, path).getAbsoluteFile();
				}
				break;
			case "con":
				addContainer(project, path);
				break;
			case "var":
				project.warnings.add("Classpath variable not supported: " + path);
				break;
			default:
				break;
			}
		}
	}

	static void addSource(EclipseProject project, File dir, String path, boolean main, Set<File> visited) throws Exception
	{
		if(path.startsWith("/"))
		{
			File other = new File(dir.getParentFile(), path.substring(1)).getAbsoluteFile();
			if(!other.exists())
			{
				project.warnings.add("Referenced project not found: " + path);
				return;
			}
			if(visited.add(other))
			{
				addEntries(project, other, false, visited);
				project.libraries.add(new File(other, "bin"));
			}
			return;
		}
		if(!main)
		{
			return;
		}
		File src = path.isEmpty() ? dir : resolve(dir, path);
		project.sourceFolders.add(src.getAbsoluteFile());
	}

	static void addContainer(EclipseProject project, String path)
	{
		if(path.startsWith("org.eclipse.jdt.launching.JRE_CONTAINER"))
		{
			return;
		}
		if(path.startsWith("org.eclipse.jdt.junit.JUNIT_CONTAINER/4"))
		{
			project.junit4 = true;
			project.libraries.add(ClasspathParser.bundled("junit-4.13.2.jar"));
			project.libraries.add(ClasspathParser.bundled("hamcrest-core-1.3.jar"));
			return;
		}
		if(path.startsWith("org.eclipse.jdt.junit.JUNIT_CONTAINER/5") || path.startsWith("org.eclipse.jdt.junit.JUNIT_CONTAINER/6"))
		{
			project.junit5 = true;
			project.libraries.add(ClasspathParser.bundled("junit-platform-console-standalone-6.1.3.jar"));
			return;
		}
		project.warnings.add("Classpath container not supported: " + path);
	}
}
