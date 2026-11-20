package javapad.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.After;
import org.junit.Test;

public class HotSwapTest {
	static final String LOOP = "package p;\n"
		+ "public class Loop {\n"
		+ "\tpublic static void main(String[] a) throws Exception {\n"
		+ "\t\twhile(true) {\n"
		+ "\t\t\tSystem.out.println(value());\n"
		+ "\t\t\tThread.sleep(50);\n"
		+ "\t\t}\n"
		+ "\t}\n"
		+ "\tstatic String value() {\n"
		+ "\t\treturn \"%s\";\n"
		+ "\t}\n"
		+ "%s"
		+ "}\n";

	Process process;
	HotSwap swap;
	final List<String> messages = Collections.synchronizedList(new ArrayList<String>());

	@After
	public void tearDown() {
		if(swap != null) {
			swap.close();
		}
		if(process != null) {
			process.destroyForcibly();
		}
	}

	static void compile(File src, File out, String value, String extra) throws Exception {
		File f = new File(src, "p/Loop.java");
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), String.format(LOOP, value, extra).getBytes());
		int code = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", out.getPath(), f.getPath());
		assertEquals(0, code);
	}

	BufferedReader start(File dir) throws Exception {
		File src = new File(dir, "src");
		File out = new File(dir, "bin");
		compile(src, out, "old", "");
		swap = new HotSwap(out, (text, error) -> messages.add(text));
		List<String> cmd = new ArrayList<String>(Arrays.asList(Launcher.javaExecutable(), swap.agentArgument(), "-cp", out.getPath(), "p.Loop"));
		process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
		assertEquals("old", in.readLine());
		swap.attach();
		long until = System.currentTimeMillis() + 10000;
		while(System.currentTimeMillis() < until && !connected()) {
			Thread.sleep(50);
		}
		return in;
	}

	boolean connected() {
		messages.clear();
		swap.swapNow();
		return messages.isEmpty();
	}

	@Test
	public void replacesAMethodBodyInTheRunningProgram() throws Exception {
		File dir = Files.createTempDirectory("hotswap").toFile();
		BufferedReader in = start(dir);
		compile(new File(dir, "src"), new File(dir, "bin"), "new", "");
		swap.swapNow();
		assertEquals(Arrays.asList("Hot-swapped 1 class: Loop\n"), messages);
		String line = in.readLine();
		for(int i = 0; i < 20 && line.equals("old"); i++) {
			line = in.readLine();
		}
		assertEquals("new", line);
	}

	@Test
	public void reportsChangesTheJvmRefuses() throws Exception {
		File dir = Files.createTempDirectory("hotswap").toFile();
		start(dir);
		compile(new File(dir, "src"), new File(dir, "bin"), "old", "\tstatic void added() {}\n");
		swap.swapNow();
		assertEquals(1, messages.size());
		assertTrue(messages.get(0), messages.get(0).startsWith("Hot swap failed: a method was added or removed."));
	}
}
