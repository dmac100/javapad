package javapad.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class FileSearchTest {
	static File file(String name, String text) throws Exception {
		File dir = Files.createTempDirectory("fs").toFile();
		File f = new File(dir, name);
		Files.write(f.toPath(), text.getBytes());
		return f;
	}

	@Test
	public void caseSensitivityIsAnOption() throws Exception {
		File f = file("a.txt", "Hello\nhello world\nHELLO\n");
		Map<File, String> none = new HashMap<File, String>();
		FileSearch.Result insensitive = FileSearch.search(Arrays.asList(f), none, FileSearch.pattern("hello", false, false), 100);
		assertEquals(3, insensitive.matches.size());
		FileSearch.Result sensitive = FileSearch.search(Arrays.asList(f), none, FileSearch.pattern("hello", true, false), 100);
		assertEquals(1, sensitive.matches.size());
		assertEquals(2, sensitive.matches.get(0).line);
		assertEquals(1, sensitive.matches.get(0).column);
		assertEquals(5, sensitive.matches.get(0).length);
	}

	@Test
	public void theQueryIsLiteralUnlessItIsARegularExpression() throws Exception {
		File f = file("a.txt", "a.b\naxb\n");
		Map<File, String> none = new HashMap<File, String>();
		assertEquals(1, FileSearch.search(Arrays.asList(f), none, FileSearch.pattern("a.b", true, false), 100).matches.size());
		assertEquals(2, FileSearch.search(Arrays.asList(f), none, FileSearch.pattern("a.b", true, true), 100).matches.size());
	}

	@Test
	public void unsavedTextIsSearchedInsteadOfTheFile() throws Exception {
		File f = file("a.txt", "old\n");
		Map<File, String> unsaved = new HashMap<File, String>();
		unsaved.put(f, "new text\r\nmore new\r\n");
		FileSearch.Result r = FileSearch.search(Arrays.asList(f), unsaved, FileSearch.pattern("new", true, false), 100);
		assertEquals(2, r.matches.size());
		assertEquals("new text", r.matches.get(0).text);
		assertEquals(0, FileSearch.search(Arrays.asList(f), unsaved, FileSearch.pattern("old", true, false), 100).matches.size());
	}

	@Test
	public void stopsAtTheLimitAndSkipsBinaryFiles() throws Exception {
		File f = file("a.txt", "x\nx\nx\nx\n");
		File bin = file("b.dat", "x\0x\n");
		FileSearch.Result r = FileSearch.search(Arrays.asList(bin, f), new HashMap<File, String>(), FileSearch.pattern("x", true, false), 3);
		assertEquals(3, r.matches.size());
		assertTrue(r.truncated);
		assertEquals(1, r.filesSearched);
	}
}
