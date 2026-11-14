package javapad.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IndenterTest
{
	@Test
	public void newlineKeepsIndentAndAddsAfterBrace()
	{
		assertEquals("\t", Indenter.newlineIndent("\tint a;"));
		assertEquals("\t\t", Indenter.newlineIndent("\tif(x) {"));
		assertEquals("", Indenter.newlineIndent("class A"));
		assertEquals("\t", Indenter.newlineIndent("{"));
	}

	@Test
	public void dedent()
	{
		assertEquals("\t", Indenter.dedentOnce("\t\t"));
		assertEquals("  ", Indenter.dedentOnce("      "));
		assertEquals("", Indenter.dedentOnce(""));
	}

	@Test
	public void blockShift()
	{
		assertEquals("\ta\n\n\t\tb", Indenter.indentLines("a\n\n\tb"));
		assertEquals("a\n\n\tb", Indenter.outdentLines("\ta\n\n\t\tb"));
	}
}
