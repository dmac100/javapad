package javapad.ui.editor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.swt.custom.StyledText;

public class Completion
{
	static final Pattern WORD_END = Pattern.compile("[\\w$]+$");
	static final Pattern NON_WORD = Pattern.compile("[^\\w$]+");

	private final StyledText text;
	private int lastCaret = -1;
	private String lastText = "";
	private List<String> completions;
	private int index;
	private int wordStart;

	public Completion(StyledText text)
	{
		this.text = text;
	}

	public boolean canComplete()
	{
		int offset = text.getCaretOffset();
		if(offset == 0 || text.getSelectionCount() > 0)
		{
			return false;
		}
		char c = text.getText(offset - 1, offset - 1).charAt(0);
		return Character.isLetterOrDigit(c) || c == '_' || c == '$';
	}

	public void complete()
	{
		int offset = text.getCaretOffset();
		String content = text.getText();
		if(offset != lastCaret || !content.equals(lastText))
		{
			completions = null;
		}
		if(completions == null)
		{
			fill(content, offset);
		}
		if(!completions.isEmpty())
		{
			String completion = completions.get(index);
			text.replaceTextRange(wordStart, offset - wordStart, completion);
			text.setCaretOffset(wordStart + completion.length());
			index = (index + 1) % completions.size();
		}
		lastCaret = text.getCaretOffset();
		lastText = text.getText();
	}

	void fill(String content, int offset)
	{
		index = 0;
		completions = new ArrayList<String>();
		String before = content.substring(0, offset);
		String after = content.substring(offset);
		Matcher m = WORD_END.matcher(before);
		if(!m.find())
		{
			return;
		}
		String prefix = m.group();
		wordStart = offset - prefix.length();
		Set<String> words = new LinkedHashSet<String>();
		String[] beforeWords = NON_WORD.split(before.substring(0, wordStart));
		for(int i = beforeWords.length - 1; i >= 0; i--)
		{
			words.add(beforeWords[i]);
		}
		for(String w : NON_WORD.split(after))
		{
			words.add(w);
		}
		for(String word : words)
		{
			if(word.length() > prefix.length() && word.toLowerCase().startsWith(prefix.toLowerCase()))
			{
				completions.add(word);
			}
		}
		completions.add(prefix);
	}
}
