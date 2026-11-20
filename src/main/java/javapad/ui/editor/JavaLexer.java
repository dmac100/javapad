package javapad.ui.editor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JavaLexer {
	public static final int PLAIN = 0;
	public static final int KEYWORD = 1;
	public static final int TYPE = 2;
	public static final int STRING = 3;
	public static final int NUMBER = 4;
	public static final int COMMENT = 5;
	public static final int ANNOTATION = 6;
	public static final int DOC = 7;

	public static final int STATE_NORMAL = 0;
	public static final int STATE_BLOCK_COMMENT = 1;
	public static final int STATE_DOC_COMMENT = 2;
	public static final int STATE_TEXT_BLOCK = 3;

	static final Set<String> KEYWORDS = new HashSet<String>(Arrays.asList(
		"abstract", "assert", "break", "case", "catch", "class", "const", "continue", "default", "do",
		"else", "enum", "extends", "final", "finally", "for", "goto", "if", "implements", "import",
		"instanceof", "interface", "native", "new", "package", "private", "protected", "public", "return",
		"static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient",
		"try", "volatile", "while", "true", "false", "null", "var", "record", "yield", "sealed", "permits",
		"boolean", "byte", "char", "double", "float", "int", "long", "short", "void"));

	public int endState;
	private final boolean java;

	public JavaLexer() {
		this(true);
	}

	// java=false is for other kinds of files: only numbers and double-quoted text are coloured.
	public JavaLexer(boolean java) {
		this.java = java;
	}

	// Numbers and "quoted text" on a single line, for files that are not Java.
	int[] simpleStyles(String line) {
		int n = line.length();
		int[] out = new int[n];
		int i = 0;
		while(i < n) {
			char c = line.charAt(i);
			if(c == '"') {
				int j = i + 1;
				while(j < n && line.charAt(j) != '"') {
					j += line.charAt(j) == '\\' ? 2 : 1;
				}
				int stop = Math.min(n, j + 1);
				fill(out, i, stop, STRING);
				i = stop;
			} else if(Character.isLetterOrDigit(c) || c == '_') {
				int j = i;
				while(j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_')) {
					j++;
				}
				if(Character.isDigit(c)) {
					// a decimal fraction belongs to the number
					if(j + 1 < n && line.charAt(j) == '.' && Character.isDigit(line.charAt(j + 1))) {
						j++;
						while(j < n && Character.isDigit(line.charAt(j))) {
							j++;
						}
					}
					fill(out, i, j, NUMBER);
				}
				i = j;
			} else {
				i++;
			}
		}
		endState = STATE_NORMAL;
		return out;
	}

	public int[] styles(String line, int startState) {
		if(!java) {
			return simpleStyles(line);
		}
		int n = line.length();
		int[] out = new int[n];
		int state = startState;
		int i = 0;
		while(i < n) {
			if(state == STATE_BLOCK_COMMENT || state == STATE_DOC_COMMENT) {
				int style = state == STATE_DOC_COMMENT ? DOC : COMMENT;
				int end = line.indexOf("*/", i);
				int stop = end < 0 ? n : end + 2;
				fill(out, i, stop, style);
				i = stop;
				if(end >= 0) {
					state = STATE_NORMAL;
				}
				continue;
			}
			if(state == STATE_TEXT_BLOCK) {
				i = scanString(line, out, i, "\"\"\"", true);
				if(i < 0) {
					i = n;
				} else {
					state = STATE_NORMAL;
				}
				continue;
			}
			char c = line.charAt(i);
			if(line.startsWith("//", i)) {
				fill(out, i, n, COMMENT);
				i = n;
			} else if(line.startsWith("/*", i)) {
				state = line.startsWith("/**", i) && !line.startsWith("/**/", i) ? STATE_DOC_COMMENT : STATE_BLOCK_COMMENT;
				int style = state == STATE_DOC_COMMENT ? DOC : COMMENT;
				int end = line.indexOf("*/", i + 2);
				int stop = end < 0 ? n : end + 2;
				fill(out, i, stop, style);
				i = stop;
				if(end >= 0) {
					state = STATE_NORMAL;
				}
			} else if(line.startsWith("\"\"\"", i)) {
				fill(out, i, i + 3, STRING);
				state = STATE_TEXT_BLOCK;
				i += 3;
			} else if(c == '"' || c == '\'') {
				int j = i + 1;
				while(j < n && line.charAt(j) != c) {
					if(line.charAt(j) == '\\') {
						j++;
					}
					j++;
				}
				int stop = Math.min(n, j + 1);
				fill(out, i, stop, STRING);
				i = stop;
			} else if(c == '@' && i + 1 < n && Character.isJavaIdentifierStart(line.charAt(i + 1))) {
				int j = i + 1;
				while(j < n && (Character.isJavaIdentifierPart(line.charAt(j)) || line.charAt(j) == '.')) {
					j++;
				}
				fill(out, i, j, ANNOTATION);
				i = j;
			} else if(Character.isDigit(c)) {
				int j = i;
				while(j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '.' || line.charAt(j) == '_')) {
					j++;
				}
				fill(out, i, j, NUMBER);
				i = j;
			} else if(Character.isJavaIdentifierStart(c)) {
				int j = i;
				while(j < n && Character.isJavaIdentifierPart(line.charAt(j))) {
					j++;
				}
				String word = line.substring(i, j);
				if(KEYWORDS.contains(word)) {
					fill(out, i, j, KEYWORD);
				} else if(Character.isUpperCase(c) && !isConstant(word)) {
					fill(out, i, j, TYPE);
				}
				i = j;
			} else {
				i++;
			}
		}
		endState = state;
		return out;
	}

	static boolean isConstant(String word) {
		return word.length() > 1 && word.equals(word.toUpperCase());
	}

	static int scanString(String line, int[] out, int from, String close, boolean escapes) {
		int j = from;
		int n = line.length();
		while(j < n) {
			if(escapes && line.charAt(j) == '\\') {
				j += 2;
				continue;
			}
			if(line.startsWith(close, j)) {
				fill(out, from, j + close.length(), STRING);
				return j + close.length();
			}
			j++;
		}
		fill(out, from, n, STRING);
		return -1;
	}

	static void fill(int[] out, int from, int to, int style) {
		for(int i = from; i < to && i < out.length; i++) {
			out[i] = style;
		}
	}
}
