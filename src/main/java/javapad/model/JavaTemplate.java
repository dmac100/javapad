package javapad.model;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

// The starting text of a new .java file.
public class JavaTemplate
{
	// "public class Name {\n}", preceded by a package line when the file is in a package folder of a source folder.
	// Empty when the file name is not a valid class name.
	public static String of(File file, List<File> sourceFolders)
	{
		String cls = file.getName().substring(0, file.getName().length() - ".java".length());
		if(cls.isEmpty() || !Character.isJavaIdentifierStart(cls.charAt(0)) || !cls.chars().allMatch(Character::isJavaIdentifierPart))
		{
			return "";
		}
		Path dir = file.getAbsoluteFile().getParentFile().toPath().normalize();
		String pkg = "";
		for(File src : sourceFolders)
		{
			Path root = src.toPath().toAbsolutePath().normalize();
			if(dir.startsWith(root))
			{
				pkg = root.relativize(dir).toString().replace(File.separatorChar, '.');
				break;
			}
		}
		return (pkg.isEmpty() ? "" : "package " + pkg + ";\n\n") + "public class " + cls + " {\n}";
	}
}
