package javapad.lang;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

// Finds the package a file's folder implies and rewrites the package declaration to match.
public class PackageFixer {
	// The package implied by the file's folder under a source folder, "" for the default package, null when the file is in none.
	public static String expected(File file, List<File> sourceFolders) {
		Path dir = file.getAbsoluteFile().getParentFile().toPath().normalize();
		for(File src : sourceFolders) {
			Path root = src.toPath().toAbsolutePath().normalize();
			if(dir.startsWith(root)) {
				return root.relativize(dir).toString().replace(File.separatorChar, '.');
			}
		}
		return null;
	}

	// The declared package, "" when there is none.
	public static String declared(String content) {
		int[] span = declaration(content);
		if(span == null) {
			return "";
		}
		return content.substring(span[2], span[1]).replaceAll("\\s+", "").replaceAll(";$", "");
	}

	// The text with its package declaration set to pkg, or the same text when it already matches.
	public static String fix(String content, String pkg) {
		if(declared(content).equals(pkg)) {
			return content;
		}
		int[] span = declaration(content);
		if(span == null) {
			int at = skipTrivia(content, 0);
			return content.substring(0, at) + "package " + pkg + ";\n\n" + content.substring(at);
		}
		if(pkg.isEmpty()) {
			int end = span[1] + 1;
			while(end < content.length() && (content.charAt(end) == '\n' || content.charAt(end) == '\r')) {
				end++;
			}
			return content.substring(0, span[0]) + content.substring(end);
		}
		return content.substring(0, span[2]) + pkg + ";" + content.substring(span[1] + 1);
	}

	// {start of "package", index of the ';' (exclusive end of name), start of the name}, or null.
	private static int[] declaration(String c) {
		int start = skipTrivia(c, 0);
		if(!c.startsWith("package", start) || start + 7 >= c.length() || !Character.isWhitespace(c.charAt(start + 7))) {
			return null;
		}
		int semi = c.indexOf(';', start);
		if(semi < 0) {
			return null;
		}
		int name = start + 7;
		while(Character.isWhitespace(c.charAt(name))) {
			name++;
		}
		return new int[] {start, semi, name};
	}

	private static int skipTrivia(String c, int i) {
		while(i < c.length()) {
			char ch = c.charAt(i);
			if(Character.isWhitespace(ch)) {
				i++;
			} else if(c.startsWith("//", i)) {
				int nl = c.indexOf('\n', i);
				i = nl < 0 ? c.length() : nl + 1;
			} else if(c.startsWith("/*", i)) {
				int end = c.indexOf("*/", i + 2);
				i = end < 0 ? c.length() : end + 2;
			} else {
				break;
			}
		}
		return i;
	}
}
