package javapad.lang;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JavaFormatterTest {
	@Test
	public void bracesMoveUpAndKeywordsLoseTheirSpace() {
		String in = "class A\n{\n    void f(int x)\n    {\n        if (x > 0)\n        {\n            g();\n        }\n        else\n        {\n            h();\n        }\n    }\n}\n";
		String out = "class A {\n\tvoid f(int x) {\n\t\tif(x > 0) {\n\t\t\tg();\n\t\t} else {\n\t\t\th();\n\t\t}\n\t}\n}\n";
		assertEquals(out, JavaFormatter.format(in));
	}

	@Test
	public void formattingTwiceChangesNothing() {
		String in = "class A {\nvoid f() {\ntry {\nx();\n} catch (Exception e) {\ny();\n}\nswitch (a) {\ncase 1:\nz();\nbreak;\ndefault:\nbreak;\n}\n}\n}\n";
		String once = JavaFormatter.format(in);
		assertEquals(once, JavaFormatter.format(once));
		assertEquals("class A {\n\tvoid f() {\n\t\ttry {\n\t\t\tx();\n\t\t} catch(Exception e) {\n\t\t\ty();\n\t\t}\n\t\tswitch(a) {\n\t\t\tcase 1:\n\t\t\t\tz();\n\t\t\t\tbreak;\n\t\t\tdefault:\n\t\t\t\tbreak;\n\t\t}\n\t}\n}\n", once);
	}

	@Test
	public void continuationsAndAnonymousClasses() {
		String in = "class A {\nvoid f() {\nfoo(a,\nb);\nx = a +\nb;\nrun(new Runnable() {\npublic void run() {\n}\n});\n}\n}\n";
		String out = "class A {\n\tvoid f() {\n\t\tfoo(a,\n\t\t\tb);\n\t\tx = a +\n\t\t\tb;\n\t\trun(new Runnable() {\n\t\t\tpublic void run() {\n\t\t\t}\n\t\t});\n\t}\n}\n";
		assertEquals(out, JavaFormatter.format(in));
	}

	@Test
	public void stringsAndCommentsAreLeftAlone() {
		String in = "class A {\nString s = \"if (x) {\";\n/*\n * if (a)\n    * x\n */\n// } else\n}\n";
		String out = "class A {\n\tString s = \"if (x) {\";\n\t/*\n\t * if (a)\n\t * x\n\t */\n\t// } else\n}\n";
		assertEquals(out, JavaFormatter.format(in));
	}

	@Test
	public void onlyTheSelectedLinesChange() {
		String in = "class A {\n\tvoid f() {\n     a();\n     b();\n\t}\n}\n";
		JavaFormatter.Result r = JavaFormatter.format(in, 2, 2);
		assertEquals("class A {\n\tvoid f() {\n\t\ta();\n     b();\n\t}\n}\n", r.text);
		assertEquals(2, r.lastLine);
	}

	@Test
	public void lineEndingsAreKept() {
		assertEquals("class A {\r\n\tint x;\r\n}\r\n", JavaFormatter.format("class A\r\n{\r\nint x;\r\n}\r\n"));
	}
}
