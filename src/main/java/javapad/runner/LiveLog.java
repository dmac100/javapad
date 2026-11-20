package javapad.runner;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

// Appends one tab-separated event per line to the file named by -Djavapad.live, for JavaPad to follow while tests run.
public class LiveLog {
	static final String PATH = System.getProperty("javapad.live");

	public static boolean enabled() {
		return PATH != null;
	}

	public static synchronized void event(String... fields) {
		if(PATH == null) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < fields.length; i++) {
			if(i > 0) {
				sb.append('\t');
			}
			String f = fields[i] == null ? "" : fields[i];
			if(f.length() > 500) {
				f = f.substring(0, 500);
			}
			sb.append(f.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '));
		}
		sb.append('\n');
		try(FileOutputStream out = new FileOutputStream(new File(PATH), true)) {
			out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
		} catch(Exception e) {
			// live progress is optional; the final report still has the results
		}
	}
}
