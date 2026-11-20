package javapad.lang;

import java.util.List;

public class Names {
	// Prefix match ignoring case, or a camel-hump match: "NPE" matches NullPointerException.
	public static boolean matches(String prefix, String name) {
		if(prefix.isEmpty()) {
			return true;
		}
		if(name.regionMatches(true, 0, prefix, 0, prefix.length())) {
			return true;
		}
		if(prefix.length() < 2) {
			return false;
		}
		StringBuilder humps = new StringBuilder();
		for(int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if(i == 0 || Character.isUpperCase(c) || name.charAt(i - 1) == '_') {
				humps.append(c);
			}
		}
		return humps.toString().regionMatches(true, 0, prefix, 0, prefix.length());
	}

	public static String simple(String qualified) {
		int dot = qualified.lastIndexOf('.');
		return dot < 0 ? qualified : qualified.substring(dot + 1);
	}

	// List<java.lang.String> becomes List<String>
	public static String shorten(String type) {
		return type.replaceAll("\\b[a-z_][\\w]*(\\.[a-z_][\\w]*)*\\.(?=[A-Z_])", "");
	}
}
