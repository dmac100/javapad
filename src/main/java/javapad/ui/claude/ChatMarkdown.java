package javapad.ui.claude;

import java.util.ArrayList;
import java.util.List;

// The little bit of Markdown that Claude's answers use, made readable in plain text: "- item" and "* item" lines become bullets, and **text** becomes bold (the asterisks go).
public class ChatMarkdown {
	static class Result {
		final String text;
		// start and length of each bold run, in the text
		final List<int[]> bold;

		Result(String text, List<int[]> bold) {
			this.text = text;
			this.bold = bold;
		}
	}

	static Result render(String raw) {
		StringBuilder out = new StringBuilder();
		List<int[]> bold = new ArrayList<int[]>();
		String[] lines = raw.split("\n", -1);
		for(int i = 0; i < lines.length; i++) {
			if(i > 0) {
				out.append('\n');
			}
			line(lines[i], out, bold);
		}
		return new Result(out.toString(), bold);
	}

	static void line(String line, StringBuilder out, List<int[]> bold) {
		int indent = 0;
		while(indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
			indent++;
		}
		String rest = line.substring(indent);
		out.append(line, 0, indent);
		if(rest.length() >= 2 && (rest.charAt(0) == '-' || rest.charAt(0) == '*' || rest.charAt(0) == '+') && rest.charAt(1) == ' ') {
			out.append(indent > 0 ? '◦' : '•').append(' ');
			rest = rest.substring(2);
		}
		inline(rest, out, bold);
	}

	// **bold** runs; a "**" that is never closed stays as it is (the rest of an answer may still be on its way)
	static void inline(String s, StringBuilder out, List<int[]> bold) {
		int i = 0;
		while(i < s.length()) {
			int open = s.indexOf("**", i);
			int close = open < 0 ? -1 : s.indexOf("**", open + 2);
			if(open < 0 || close < 0 || close == open + 2) {
				out.append(s, i, s.length());
				return;
			}
			out.append(s, i, open);
			int start = out.length();
			out.append(s, open + 2, close);
			bold.add(new int[] {start, out.length() - start});
			i = close + 2;
		}
	}
}
