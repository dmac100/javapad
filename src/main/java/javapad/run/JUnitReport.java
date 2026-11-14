package javapad.run;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JUnitReport
{
	public static List<TestResult> parse(File xml) throws Exception
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		Document doc = factory.newDocumentBuilder().parse(xml);
		List<TestResult> results = new ArrayList<TestResult>();
		NodeList cases = doc.getElementsByTagName("testcase");
		for(int i = 0; i < cases.getLength(); i++)
		{
			Element e = (Element)cases.item(i);
			String cls = e.getAttribute("classname");
			String name = e.getAttribute("name");
			double time = 0;
			try
			{
				time = Double.parseDouble(e.getAttribute("time"));
			}
			catch(NumberFormatException ex)
			{
			}
			int status = TestResult.PASSED;
			String message = "";
			String trace = "";
			for(Node n = e.getFirstChild(); n != null; n = n.getNextSibling())
			{
				if(!(n instanceof Element))
				{
					continue;
				}
				Element c = (Element)n;
				String tag = c.getTagName();
				if(tag.equals("failure") || tag.equals("error"))
				{
					status = tag.equals("failure") ? TestResult.FAILED : TestResult.ERROR;
					message = c.getAttribute("message");
					trace = c.getTextContent().trim();
				}
				else if(tag.equals("skipped") && status == TestResult.PASSED)
				{
					status = TestResult.SKIPPED;
					message = c.getAttribute("message");
				}
			}
			results.add(new TestResult(cls, name, time, status, message, trace));
		}
		return results;
	}
}
