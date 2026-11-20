package javapad.model;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EclipseProject {
	public final File root;
	public String name;
	public final List<File> sourceFolders = new ArrayList<File>();
	public File outputFolder;
	public final Set<File> libraries = new LinkedHashSet<File>();
	public final List<String> warnings = new ArrayList<String>();
	public boolean junit4;
	public boolean junit5;
	public int release;
	// set by the compiler when the project's mains need the preview features of java 21 to 24
	public boolean preview;
	public final List<String> notes = new ArrayList<String>();

	public EclipseProject(File root) {
		this.root = root;
		this.name = root.getName();
	}

	public static EclipseProject open(File dir) throws Exception {
		List<String> notes = ProjectGenerator.ensure(dir);
		EclipseProject project = ClasspathParser.parse(dir);
		project.notes.addAll(notes);
		project.addMissingJunit();
		return project;
	}

	// Picks up source folders that appeared after the project was opened; true if the list changed.
	public boolean refreshSources() throws Exception {
		if(!sourceFolders.isEmpty() || !ProjectGenerator.addMissingSources(root)) {
			return false;
		}
		EclipseProject fresh = ClasspathParser.parse(root);
		sourceFolders.addAll(fresh.sourceFolders);
		libraries.addAll(fresh.libraries);
		return !sourceFolders.isEmpty();
	}

	public static EclipseProject regenerate(File dir) throws Exception {
		List<String> notes = ProjectGenerator.regenerate(dir);
		EclipseProject project = ClasspathParser.parse(dir);
		project.notes.addAll(notes);
		project.addMissingJunit();
		return project;
	}

	void addMissingJunit() {
		boolean has4 = junit4;
		boolean has5 = junit5;
		for(File lib : libraries) {
			String n = lib.getName();
			if(n.startsWith("junit-4")) {
				has4 = true;
			}
			if(n.startsWith("junit-jupiter") || n.startsWith("junit-platform-console")) {
				has5 = true;
			}
		}
		if(has4 && has5) {
			return;
		}
		boolean use4 = false;
		boolean use5 = false;
		for(File folder : sourceFolders) {
			String[] uses = scanJunit(folder);
			use4 |= uses[0] != null;
			use5 |= uses[1] != null;
		}
		if(use4 && !has4) {
			libraries.add(ClasspathParser.bundled("junit-4.13.2.jar"));
			libraries.add(ClasspathParser.bundled("hamcrest-core-1.3.jar"));
			junit4 = true;
			notes.add("Using bundled JUnit 4");
		}
		if(use5 && !has5) {
			libraries.add(ClasspathParser.bundled("junit-platform-console-standalone-6.1.3.jar"));
			junit5 = true;
			notes.add("Using bundled JUnit 5");
		}
	}

	static String[] scanJunit(File folder) {
		String[] found = new String[2];
		File[] files = folder.listFiles();
		if(files == null) {
			return found;
		}
		for(File f : files) {
			if(f.isDirectory()) {
				String[] sub = scanJunit(f);
				found[0] = found[0] != null ? found[0] : sub[0];
				found[1] = found[1] != null ? found[1] : sub[1];
			} else if(f.getName().endsWith(".java") && f.length() < 1000000) {
				try {
					String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
					if(text.contains("org.junit.jupiter")) {
						found[1] = f.getName();
					} else if(text.contains("org.junit.")) {
						found[0] = f.getName();
					}
				} catch(Exception e) {
					// a file that cannot be read does not count as a JUnit user
				}
			}
			if(found[0] != null && found[1] != null) {
				break;
			}
		}
		return found;
	}

	public List<File> classpath() {
		List<File> result = new ArrayList<File>();
		result.add(outputFolder);
		result.addAll(libraries);
		return result;
	}

	public String classpathString() {
		StringBuilder sb = new StringBuilder();
		for(File f : classpath()) {
			if(sb.length() > 0) {
				sb.append(File.pathSeparator);
			}
			sb.append(f.getAbsolutePath());
		}
		return sb.toString();
	}

	public String relative(File f) {
		String root = this.root.getAbsolutePath();
		String path = f.getAbsolutePath();
		if(path.startsWith(root + File.separator)) {
			return path.substring(root.length() + 1);
		}
		return path;
	}

	public boolean isSource(File f) {
		for(File src : sourceFolders) {
			if(f.getAbsolutePath().startsWith(src.getAbsolutePath() + File.separator)) {
				return true;
			}
		}
		return false;
	}

	public String classNameOf(File javaFile) {
		for(File src : sourceFolders) {
			String base = src.getAbsolutePath() + File.separator;
			String path = javaFile.getAbsolutePath();
			if(path.startsWith(base) && path.endsWith(".java")) {
				String rel = path.substring(base.length(), path.length() - 5);
				return rel.replace(File.separatorChar, '.');
			}
		}
		return null;
	}

	public File findSource(String qualifiedClass, String fileName) {
		int dot = qualifiedClass.lastIndexOf('.');
		String pkg = dot < 0 ? "" : qualifiedClass.substring(0, dot).replace('.', File.separatorChar);
		for(File src : sourceFolders) {
			File f = new File(new File(src, pkg), fileName);
			if(f.exists()) {
				return f;
			}
		}
		return null;
	}

	public File sourceFor(String className) {
		int inner = className.indexOf('$');
		String outer = inner < 0 ? className : className.substring(0, inner);
		String rel = outer.replace('.', File.separatorChar) + ".java";
		for(File src : sourceFolders) {
			File f = new File(src, rel);
			if(f.exists()) {
				return f;
			}
		}
		return null;
	}
}
