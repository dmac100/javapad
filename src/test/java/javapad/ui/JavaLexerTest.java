package javapad.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JavaLexerTest
{
	@Test
	public void keywordsStringsAndComments()
	{
		JavaLexer lexer = new JavaLexer();
		String line = "public String s = \"a//b\"; // c";
		int[] st = lexer.styles(line, JavaLexer.STATE_NORMAL);
		assertEquals(JavaLexer.KEYWORD, st[0]);
		assertEquals(JavaLexer.TYPE, st[7]);
		assertEquals(JavaLexer.STRING, st[line.indexOf('"') + 2]);
		assertEquals(JavaLexer.COMMENT, st[line.indexOf("// c")]);
		assertEquals(JavaLexer.PLAIN, st[line.indexOf(" s ") + 1]);
	}

	@Test
	public void blockCommentSpansLines()
	{
		JavaLexer lexer = new JavaLexer();
		lexer.styles("int a; /* start", JavaLexer.STATE_NORMAL);
		assertEquals(JavaLexer.STATE_BLOCK_COMMENT, lexer.endState);
		int[] st = lexer.styles("still comment */ int b;", JavaLexer.STATE_BLOCK_COMMENT);
		assertEquals(JavaLexer.COMMENT, st[0]);
		assertEquals(JavaLexer.KEYWORD, st[17]);
		assertEquals(JavaLexer.STATE_NORMAL, lexer.endState);
	}

	@Test
	public void textBlockSpansLines()
	{
		JavaLexer lexer = new JavaLexer();
		lexer.styles("String s = \"\"\"", JavaLexer.STATE_NORMAL);
		assertEquals(JavaLexer.STATE_TEXT_BLOCK, lexer.endState);
		int[] st = lexer.styles("  int x // not a comment", JavaLexer.STATE_TEXT_BLOCK);
		assertEquals(JavaLexer.STRING, st[2]);
		assertEquals(JavaLexer.STRING, st[10]);
		lexer.styles("\"\"\";", JavaLexer.STATE_TEXT_BLOCK);
		assertEquals(JavaLexer.STATE_NORMAL, lexer.endState);
	}

	@Test
	public void numbersAnnotationsAndDoc()
	{
		JavaLexer lexer = new JavaLexer();
		int[] st = lexer.styles("@Override int x = 0x1F;", JavaLexer.STATE_NORMAL);
		assertEquals(JavaLexer.ANNOTATION, st[0]);
		assertEquals(JavaLexer.NUMBER, st[19]);
		lexer.styles("/** doc", JavaLexer.STATE_NORMAL);
		assertEquals(JavaLexer.STATE_DOC_COMMENT, lexer.endState);
	}
}
