package javapad.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javapad.lang.ImportEditor;
import javapad.lang.ImportFixer;
import javapad.lang.JavaAnalyzer;
import javapad.lang.TypeIndex;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

// Add Import, Organize Imports and the import quick fix. Analysis runs off the UI thread; prompts and edits happen on it.
public class ImportActions
{
	private final Shell shell;
	private final CodeAssist assist;

	public ImportActions(Shell shell, CodeAssist assist)
	{
		this.shell = shell;
		this.assist = assist;
	}

	void info(String title, String message)
	{
		ConfirmDialog.message(shell, title, message, SWT.ICON_INFORMATION);
	}

	static String wordAt(String content, int caret)
	{
		int start = caret;
		int end = caret;
		while(start > 0 && Character.isJavaIdentifierPart(content.charAt(start - 1)))
		{
			start--;
		}
		while(end < content.length() && Character.isJavaIdentifierPart(content.charAt(end)))
		{
			end++;
		}
		return start == end ? null : content.substring(start, end);
	}

	// Picks one candidate, asking only when there is more than one. Null means cancelled.
	TypeIndex.Entry choose(String name, List<TypeIndex.Entry> found)
	{
		if(found.size() == 1)
		{
			return found.get(0);
		}
		List<String> names = new ArrayList<String>();
		for(TypeIndex.Entry e : found)
		{
			names.add(e.fqn);
		}
		String pick = ChoiceDialog.choose(shell, "Import " + name + " from", names);
		if(pick == null)
		{
			return null;
		}
		for(TypeIndex.Entry e : found)
		{
			if(e.fqn.equals(pick))
			{
				return e;
			}
		}
		return null;
	}

	boolean ready()
	{
		if(assist.index() == null)
		{
			info("Imports", "The type index is still being built. Try again in a moment.");
			return false;
		}
		return true;
	}

	public void addImport(EditorTab tab)
	{
		if(!ready())
		{
			return;
		}
		CodeEditor ed = tab.editor;
		String content = ed.getContent();
		String word = wordAt(content, ed.getControl().getCaretOffset());
		if(word == null)
		{
			info("Add Import", "Put the cursor on a type name.");
			return;
		}
		List<TypeIndex.Entry> found = ImportFixer.candidates(assist.index(), word, content);
		if(found.isEmpty())
		{
			info("Add Import", assist.index().exact(word).isEmpty() ? "No type named '" + word + "' was found." : "'" + word + "' is already imported or visible.");
			return;
		}
		TypeIndex.Entry pick = choose(word, found);
		if(pick != null)
		{
			ed.insertImport(pick.fqn, pick.pkg);
		}
	}

	public void organize(EditorTab tab)
	{
		if(!ready())
		{
			return;
		}
		CodeEditor ed = tab.editor;
		String content = ed.getContent();
		JavaAnalyzer analyzer = assist.analyzer();
		TypeIndex index = assist.index();
		assist.submit(() ->
		{
			JavaAnalyzer.Result r = analyzer.analyze(tab.file, content);
			return r == null ? null : ImportFixer.plan(content, r, index);
		}, plan ->
		{
			if(plan == null)
			{
				info("Organize Imports", "The file could not be analyzed.");
				return;
			}
			if(!ed.getContent().equals(content))
			{
				return;
			}
			for(Map.Entry<String, List<TypeIndex.Entry>> choice : plan.choices.entrySet())
			{
				TypeIndex.Entry pick = choose(choice.getKey(), choice.getValue());
				if(pick == null)
				{
					return;
				}
				plan.keep.add(pick.fqn);
			}
			String result = ImportEditor.rewrite(content, plan.keep, plan.statics);
			if(!result.equals(content))
			{
				ed.replaceContentMinimal(result);
			}
			if(!plan.notFound.isEmpty())
			{
				info("Organize Imports", "No type was found for: " + String.join(", ", plan.notFound));
			}
		});
	}

	// Ctrl+1: add the import for an unresolved type on the caret's line.
	public void quickFix(EditorTab tab)
	{
		if(!ready())
		{
			return;
		}
		CodeEditor ed = tab.editor;
		String content = ed.getContent();
		int line = ed.getControl().getLineAtOffset(ed.getControl().getCaretOffset()) + 1;
		JavaAnalyzer analyzer = assist.analyzer();
		assist.submit(() ->
		{
			JavaAnalyzer.Result r = analyzer.analyze(tab.file, content);
			return r == null ? new ArrayList<ImportFixer.Missing>() : ImportFixer.unresolved(r);
		}, missing ->
		{
			if(!ed.getContent().equals(content))
			{
				return;
			}
			boolean any = false;
			for(ImportFixer.Missing m : missing)
			{
				if(m.line != line)
				{
					continue;
				}
				List<TypeIndex.Entry> found = ImportFixer.candidates(assist.index(), m.name, ed.getContent());
				if(found.isEmpty())
				{
					continue;
				}
				any = true;
				TypeIndex.Entry pick = choose(m.name, found);
				if(pick == null)
				{
					return;
				}
				ed.insertImport(pick.fqn, pick.pkg);
			}
			if(!any)
			{
				info("Quick Fix", "No quick fix is available on this line.");
			}
		});
	}
}
