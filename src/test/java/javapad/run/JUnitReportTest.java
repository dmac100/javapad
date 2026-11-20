package javapad.run;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;

public class JUnitReportTest
{
	@Test
	public void parsesPassFailErrorSkip() throws Exception
	{
		String xml = "<testsuite><testcase classname=\"a.B\" name=\"ok\" time=\"0.5\"/>"
			+ "<testcase classname=\"a.B\" name=\"bad\" time=\"0.1\"><failure message=\"x &lt; y\" type=\"java.lang.AssertionError\">trace here</failure></testcase>"
			+ "<testcase classname=\"a.B\" name=\"boom\" time=\"0\"><error message=\"m\">t</error></testcase>"
			+ "<testcase classname=\"a.C\" name=\"skip\" time=\"0\"><skipped/></testcase></testsuite>";
		File f = File.createTempFile("report", ".xml");
		Files.write(f.toPath(), xml.getBytes(StandardCharsets.UTF_8));
		List<TestResult> r = JUnitReport.parse(f);
		f.delete();
		assertEquals(4, r.size());
		assertEquals(TestResult.PASSED, r.get(0).status);
		assertEquals(0.5, r.get(0).seconds, 0.0001);
		assertEquals(TestResult.FAILED, r.get(1).status);
		assertEquals("x < y", r.get(1).message);
		assertEquals("trace here", r.get(1).trace);
		assertEquals(TestResult.ERROR, r.get(2).status);
		assertEquals(TestResult.SKIPPED, r.get(3).status);
	}
}
