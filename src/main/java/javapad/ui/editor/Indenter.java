package javapad.ui.editor;

public class Indenter {
	public static String indentOf(String line) {
		int i = 0;
		while(i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
			i++;
		}
		return line.substring(0, i);
	}

	public static String newlineIndent(String lineBeforeCaret) {
		String indent = indentOf(lineBeforeCaret);
		String trimmed = lineBeforeCaret.trim();
		if(trimmed.endsWith("{")) {
			return indent + "\t";
		}
		return indent;
	}

	public static String dedentOnce(String indent) {
		if(indent.endsWith("\t")) {
			return indent.substring(0, indent.length() - 1);
		}
		int n = 0;
		while(n < 4 && n < indent.length() && indent.charAt(indent.length() - 1 - n) == ' ') {
			n++;
		}
		return indent.substring(0, indent.length() - n);
	}

	public static String indentLines(String text) {
		String[] lines = text.split("\n", -1);
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < lines.length; i++) {
			if(i > 0) {
				sb.append('\n');
			}
			if(lines[i].length() > 0) {
				sb.append('\t');
			}
			sb.append(lines[i]);
		}
		return sb.toString();
	}

	public static String outdentLines(String text) {
		String[] lines = text.split("\n", -1);
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < lines.length; i++) {
			if(i > 0) {
				sb.append('\n');
			}
			String line = lines[i];
			String indent = indentOf(line);
			sb.append(dedentOnce(indent));
			sb.append(line.substring(indent.length()));
		}
		return sb.toString();
	}
}
