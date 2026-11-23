package javapad.ui.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// A small JSON reader: objects become Maps, arrays Lists, numbers Doubles, and strings, booleans and null as themselves.
public class Json {
	private final String s;
	private int i;

	private Json(String s) {
		this.s = s;
	}

	// throws IllegalArgumentException when the text is not JSON
	static Object parse(String text) {
		Json j = new Json(text);
		j.space();
		Object value = j.value();
		j.space();
		if(j.i != text.length()) {
			throw new IllegalArgumentException("Extra text after the JSON value");
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> object(Object o) {
		return o instanceof Map ? (Map<String, Object>)o : null;
	}

	@SuppressWarnings("unchecked")
	static List<Object> array(Object o) {
		return o instanceof List ? (List<Object>)o : null;
	}

	static String string(Object o) {
		return o instanceof String ? (String)o : null;
	}

	void space() {
		while(i < s.length() && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
	}

	char peek() {
		if(i >= s.length()) {
			throw new IllegalArgumentException("Unexpected end of JSON");
		}
		return s.charAt(i);
	}

	Object value() {
		char c = peek();
		if(c == '{') {
			return members();
		}
		if(c == '[') {
			return elements();
		}
		if(c == '"') {
			return text();
		}
		if(s.startsWith("true", i)) {
			i += 4;
			return Boolean.TRUE;
		}
		if(s.startsWith("false", i)) {
			i += 5;
			return Boolean.FALSE;
		}
		if(s.startsWith("null", i)) {
			i += 4;
			return null;
		}
		return number();
	}

	Map<String, Object> members() {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		i++;
		space();
		if(peek() == '}') {
			i++;
			return map;
		}
		while(true) {
			space();
			String key = text();
			space();
			expect(':');
			space();
			map.put(key, value());
			space();
			char c = peek();
			i++;
			if(c == '}') {
				return map;
			}
			if(c != ',') {
				throw new IllegalArgumentException("Expected , or } at " + (i - 1));
			}
		}
	}

	List<Object> elements() {
		List<Object> list = new ArrayList<Object>();
		i++;
		space();
		if(peek() == ']') {
			i++;
			return list;
		}
		while(true) {
			space();
			list.add(value());
			space();
			char c = peek();
			i++;
			if(c == ']') {
				return list;
			}
			if(c != ',') {
				throw new IllegalArgumentException("Expected , or ] at " + (i - 1));
			}
		}
	}

	void expect(char c) {
		if(peek() != c) {
			throw new IllegalArgumentException("Expected " + c + " at " + i);
		}
		i++;
	}

	String text() {
		expect('"');
		StringBuilder sb = new StringBuilder();
		while(true) {
			char c = peek();
			i++;
			if(c == '"') {
				return sb.toString();
			}
			if(c != '\\') {
				sb.append(c);
				continue;
			}
			char e = peek();
			i++;
			switch(e) {
				case 'n':
					sb.append('\n');
					break;
				case 't':
					sb.append('\t');
					break;
				case 'r':
					sb.append('\r');
					break;
				case 'b':
					sb.append('\b');
					break;
				case 'f':
					sb.append('\f');
					break;
				case 'u':
					if(i + 4 > s.length()) {
						throw new IllegalArgumentException("Short \\u escape");
					}
					sb.append((char)Integer.parseInt(s.substring(i, i + 4), 16));
					i += 4;
					break;
				default:
					sb.append(e);
					break;
			}
		}
	}

	Double number() {
		int start = i;
		while(i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
			i++;
		}
		if(start == i) {
			throw new IllegalArgumentException("Unexpected character at " + i);
		}
		return Double.valueOf(s.substring(start, i));
	}
}
