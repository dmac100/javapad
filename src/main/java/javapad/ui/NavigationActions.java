package javapad.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javapad.lang.CodeModel;
import javapad.lang.JavaAnalyzer;
import javapad.lang.Navigator;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

// Open Declaration (F3), Open Implementation (Ctrl+T) and the other jumps. Analysis runs off the UI thread.
public class NavigationActions
{
	private final Shell shell;
	private final CodeAssist assist;
	private final Consumer<Location> lineOpener;
	private Consumer<Target> hierarchy;

	static class Target
	{
		Element element;
		String key;
		String description;
		CodeModel.Place declaration;
		boolean method;
		boolean type;
	}

	public NavigationActions(Shell shell, CodeAssist assist, Consumer<Location> lineOpener)
	{
		this.shell = shell;
		this.assist = assist;
		this.lineOpener = lineOpener;
	}

	void setHierarchy(Consumer<Target> hierarchy)
	{
		this.hierarchy = hierarchy;
	}

	public void callHierarchy(EditorTab tab, int offset)
	{
		resolve(tab, offset, false, target ->
		{
			if(target == null || !target.method || target.key == null)
			{
				info("Call Hierarchy", "Put the cursor on a method or constructor.");
			}
			else if(hierarchy != null)
			{
				hierarchy.accept(target);
			}
		});
	}

	void info(String title, String message)
	{
		ConfirmDialog.message(shell, title, message, SWT.ICON_INFORMATION);
	}

	// every target is shown with its whole line selected
	void open(CodeModel.Place p)
	{
		lineOpener.accept(new Location(p.file, p.line, p.column));
	}

	// Finds what is at the offset, on the worker thread.
	void resolve(EditorTab tab, int offset, boolean forTypeHierarchy, Consumer<Target> done)
	{
		JavaAnalyzer analyzer = assist.analyzer();
		if(analyzer == null)
		{
			info("Navigate", "Open a project first.");
			return;
		}
		String content = tab.editor.getContent();
		assist.submit(() ->
		{
			JavaAnalyzer.Result r = analyzer.analyze(tab.file, content);
			if(r == null)
			{
				return null;
			}
			Element e = Navigator.elementAt(r, offset);
			if(e != null && forTypeHierarchy)
			{
				e = Navigator.typeOrMethod(e);
			}
			if(e == null)
			{
				return null;
			}
			Target t = new Target();
			t.element = e;
			t.key = Navigator.keyOf(e, r);
			t.method = e instanceof ExecutableElement;
			t.type = e instanceof TypeElement;
			t.description = Navigator.describe(e, r);
			t.declaration = Navigator.declaration(r, e);
			return t;
		}, done);
	}

	public void openDeclaration(EditorTab tab, int offset)
	{
		resolve(tab, offset, false, target ->
		{
			if(target == null)
			{
				info("Open Declaration", "There is no declaration at the cursor.");
			}
			else if(target.declaration == null)
			{
				CodeModel model = assist.model();
				CodeModel.Place place = null;
				if(model != null && target.key != null)
				{
					CodeModel.MethodInfo m = model.method(target.key);
					CodeModel.TypeInfo t = model.type(target.key);
					place = m != null ? m.place : t != null ? t.place : null;
				}
				if(place != null)
				{
					open(place);
				}
				else
				{
					info("Open Declaration", target.description + " comes from a library, so there is no source to open.");
				}
			}
			else
			{
				open(target.declaration);
			}
		});
	}

	public void openImplementation(EditorTab tab, int offset)
	{
		resolve(tab, offset, true, target ->
		{
			if(target == null || target.key == null)
			{
				info("Open Implementation", "Put the cursor on a method or a type.");
				return;
			}
			CodeModel model = assist.model();
			if(model == null)
			{
				info("Open Implementation", "The project is still being analyzed. Try again in a moment.");
				return;
			}
			Map<String, CodeModel.Place> found = new LinkedHashMap<String, CodeModel.Place>();
			if(target.method)
			{
				for(CodeModel.MethodInfo m : model.implementers(target.key))
				{
					found.put(label(m.display, m.place), m.place);
				}
			}
			else
			{
				for(CodeModel.TypeInfo t : model.allSubtypes(target.key))
				{
					found.put(label(t.display, t.place), t.place);
				}
			}
			if(found.isEmpty())
			{
				info("Open Implementation", "No implementations of " + target.description + " were found in this project.");
				return;
			}
			if(found.size() == 1)
			{
				open(found.values().iterator().next());
				return;
			}
			String pick = ChoiceDialog.choose(shell, (target.method ? "Implementations of " : "Subtypes of ") + target.description, new ArrayList<String>(found.keySet()));
			if(pick != null)
			{
				open(found.get(pick));
			}
		});
	}

	static String label(String display, CodeModel.Place p)
	{
		return display + "   (" + p.file.getName() + ":" + p.line + ")";
	}
}
