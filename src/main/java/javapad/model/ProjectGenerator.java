package javapad.model;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProjectGenerator {
	public static List<String> ensure(File dir) throws Exception {
		List<String> notes = new ArrayList<String>();
		if(!new File(dir, ".project").exists()) {
			writeProject(dir);
			notes.add("Created .project");
		}
		if(!new File(dir, ".classpath").exists()) {
			notes.add("Created .classpath (" + writeClasspath(dir) + ")");
		}
		return notes;
	}

	// A project created empty gets a .classpath without source folders; once Java files exist, give it some.
	// Only a .classpath with no src entry at all is touched.
	public static boolean addMissingSources(File dir) throws Exception {
		File file = new File(dir, ".classpath");
		if(!file.exists()) {
			return false;
		}
		String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		if(text.contains("kind=\"src\"") || sourceFolders(dir).isEmpty()) {
			return false;
		}
		writeClasspath(dir);
		return true;
	}

	public static List<String> regenerate(File dir) throws Exception {
		List<String> notes = new ArrayList<String>();
		writeProject(dir);
		notes.add("Regenerated .project");
		notes.add("Regenerated .classpath (" + writeClasspath(dir) + ")");
		return notes;
	}

	public static List<String> sourceFolders(File dir) {
		List<String> sources = new ArrayList<String>();
		if(new File(dir, "src/main/java").isDirectory()) {
			String[] maven = {"src/main/java", "src/main/resources", "src/test/java", "src/test/resources"};
			for(String path : maven) {
				if(new File(dir, path).isDirectory()) {
					sources.add(path);
				}
			}
			return sources;
		}
		for(String path : new String[] {"src", "test"}) {
			if(new File(dir, path).isDirectory()) {
				sources.add(path);
			}
		}
		if(sources.isEmpty() && hasJavaFiles(dir)) {
			// no src folder: the source files sit in the project folder itself (Eclipse writes that as an empty path)
			sources.add("");
		}
		return sources;
	}

	static boolean hasJavaFiles(File dir) {
		File[] files = dir.listFiles();
		if(files != null) {
			for(File f : files) {
				if(f.isFile() && f.getName().endsWith(".java")) {
					return true;
				}
			}
		}
		return false;
	}

	public static List<String> libraries(File dir) {
		List<String> jars = new ArrayList<String>();
		File[] files = new File(dir, "lib").listFiles();
		if(files == null) {
			return jars;
		}
		Arrays.sort(files);
		for(File f : files) {
			if(f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
				jars.add("lib/" + f.getName());
			}
		}
		return jars;
	}

	static String writeClasspath(File dir) throws Exception {
		List<String> sources = sourceFolders(dir);
		List<String> jars = libraries(dir);
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<classpath>\n");
		sb.append("\t<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER\"/>\n");
		for(String src : sources) {
			sb.append("\t<classpathentry kind=\"src\" path=\"" + src + "\"/>\n");
		}
		for(String jar : jars) {
			sb.append("\t<classpathentry kind=\"lib\" path=\"" + jar + "\"/>\n");
		}
		sb.append("\t<classpathentry kind=\"output\" path=\"bin\"/>\n</classpath>\n");
		Files.write(new File(dir, ".classpath").toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
		return sources.size() + " source folder(s), " + jars.size() + " jar(s)";
	}

	static void writeProject(File dir) throws Exception {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription>\n\t<name>" + escape(dir.getAbsoluteFile().getName())
			+ "</name>\n\t<comment></comment>\n\t<projects>\n\t</projects>\n\t<buildSpec>\n\t\t<buildCommand>\n"
			+ "\t\t\t<name>org.eclipse.jdt.core.javabuilder</name>\n\t\t\t<arguments>\n\t\t\t</arguments>\n\t\t</buildCommand>\n"
			+ "\t</buildSpec>\n\t<natures>\n\t\t<nature>org.eclipse.jdt.core.javanature</nature>\n\t</natures>\n</projectDescription>\n";
		Files.write(new File(dir, ".project").toPath(), xml.getBytes(StandardCharsets.UTF_8));
	}

	static String escape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
