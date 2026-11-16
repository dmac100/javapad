package javapad.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javapad.lang.CodeModel;
import javapad.lang.Names;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

// Who calls a method (or what it calls), expanded one level at a time.
public class CallHierarchyPane
{
	private final Composite composite;
	private final Label title;
	private final Button callersButton;
	private final Button calleesButton;
	private final Tree tree;
	private final Theme theme;
	private Supplier<CodeModel> models;
	private Consumer<Location> opener;
	private String rootKey;
	private String rootDisplay;
	private CodeModel.Place rootPlace;

	static class Node
	{
		String key;
		List<CodeModel.Place> sites = new ArrayList<CodeModel.Place>();
		int next;
		boolean recursive;
	}

	public CallHierarchyPane(Composite parent, Theme theme)
	{
		this.theme = theme;
		composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(4, false);
		layout.marginWidth = 4;
		layout.marginHeight = 2;
		layout.horizontalSpacing = 12;
		composite.setLayout(layout);
		callersButton = new Button(composite, SWT.RADIO);
		callersButton.setText("Callers");
		callersButton.setSelection(true);
		calleesButton = new Button(composite, SWT.RADIO);
		calleesButton.setText("Callees");
		title = new Label(composite, SWT.NONE);
		title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		tree = new Tree(composite, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
		tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1));
		callersButton.addListener(SWT.Selection, e -> rebuild());
		calleesButton.addListener(SWT.Selection, e -> rebuild());
		tree.addListener(SWT.Expand, e -> expanding((TreeItem)e.item));
		tree.addListener(SWT.DefaultSelection, e -> activate());
		title.setText("Pick a method and press Ctrl+Alt+H.");
		restyle();
	}

	public Composite getControl()
	{
		return composite;
	}

	public void setModelSupplier(Supplier<CodeModel> models)
	{
		this.models = models;
	}

	public void setOpener(Consumer<Location> opener)
	{
		this.opener = opener;
	}

	public void restyle()
	{
		composite.setBackground(theme.chrome);
		title.setBackground(theme.chrome);
		title.setForeground(theme.foreground);
		callersButton.setBackground(theme.chrome);
		callersButton.setForeground(theme.foreground);
		calleesButton.setBackground(theme.chrome);
		calleesButton.setForeground(theme.foreground);
		tree.setBackground(theme.background);
		tree.setForeground(theme.foreground);
	}

	public boolean showingCallers()
	{
		return callersButton.getSelection();
	}

	public void showCallees(boolean callees)
	{
		callersButton.setSelection(!callees);
		calleesButton.setSelection(callees);
	}

	public Tree getTree()
	{
		return tree;
	}

	public String rootDisplay()
	{
		return rootDisplay;
	}

	public void show(String key, String display, CodeModel.Place place)
	{
		rootKey = key;
		rootDisplay = display;
		rootPlace = place;
		rebuild();
	}

	void rebuild()
	{
		tree.removeAll();
		if(rootKey == null)
		{
			return;
		}
		title.setText((showingCallers() ? "Callers of " : "Callees of ") + rootDisplay);
		TreeItem root = new TreeItem(tree, SWT.NONE);
		Node node = new Node();
		node.key = rootKey;
		if(rootPlace != null)
		{
			node.sites.add(rootPlace);
		}
		root.setData(node);
		root.setText(rootDisplay);
		populate(root);
		root.setExpanded(true);
		composite.layout(true, true);
	}

	CodeModel model()
	{
		return models == null ? null : models.get();
	}

	void expanding(TreeItem item)
	{
		TreeItem[] kids = item.getItems();
		if(kids.length == 1 && kids[0].getData() == null)
		{
			populate(item);
		}
	}

	String displayOf(CodeModel model, String key)
	{
		CodeModel.MethodInfo m = model == null ? null : model.method(key);
		if(m != null)
		{
			return m.display;
		}
		int hash = key.indexOf('#');
		if(hash < 0)
		{
			return key;
		}
		String owner = Names.simple(key.substring(0, hash));
		String rest = key.substring(hash + 1);
		if(rest.equals("<init>") || rest.equals("<clinit>"))
		{
			return owner + " (initializer)";
		}
		if(rest.startsWith("<init>"))
		{
			return "new " + owner + Names.shorten(rest.substring("<init>".length()));
		}
		return owner + "." + Names.shorten(rest);
	}

	void populate(TreeItem item)
	{
		CodeModel model = model();
		Node node = (Node)item.getData();
		item.removeAll();
		if(model == null)
		{
			TreeItem wait = new TreeItem(item, SWT.NONE);
			wait.setText("The project is still being analyzed...");
			wait.setData(new Node());
			return;
		}
		boolean callers = showingCallers();
		Map<String, Node> grouped = new LinkedHashMap<String, Node>();
		List<CodeModel.Call> calls = callers ? model.callersOf(node.key) : model.calleesOf(node.key);
		for(CodeModel.Call c : calls)
		{
			String other = callers ? c.callerKey : c.targetKey;
			Node n = grouped.get(other);
			if(n == null)
			{
				n = new Node();
				n.key = other;
				grouped.put(other, n);
			}
			n.sites.add(c.place);
		}
		List<Node> sorted = new ArrayList<Node>(grouped.values());
		sorted.sort((a, b) -> displayOf(model, a.key).compareToIgnoreCase(displayOf(model, b.key)));
		for(Node n : sorted)
		{
			TreeItem child = new TreeItem(item, SWT.NONE);
			n.recursive = isAncestor(item, n.key);
			child.setData(n);
			String where = n.sites.size() == 1 ? "   (" + n.sites.get(0).file.getName() + ":" + n.sites.get(0).line + ")" : "   (" + n.sites.size() + " calls)";
			boolean project = callers || model.method(n.key) != null || model.calleesOf(n.key).size() > 0;
			child.setText(displayOf(model, n.key) + where + (n.recursive ? "   (recursive)" : ""));
			child.setForeground(project ? theme.foreground : theme.gutter);
			if(!n.recursive && hasChildren(model, n.key, callers))
			{
				new TreeItem(child, SWT.NONE);
			}
		}
		if(sorted.isEmpty())
		{
			TreeItem none = new TreeItem(item, SWT.NONE);
			none.setText(callers ? "No callers found" : "No calls found");
			none.setData(new Node());
			none.setForeground(theme.gutter);
		}
	}

	boolean hasChildren(CodeModel model, String key, boolean callers)
	{
		return callers ? !model.callersOf(key).isEmpty() : !model.calleesOf(key).isEmpty();
	}

	boolean isAncestor(TreeItem item, String key)
	{
		for(TreeItem t = item; t != null; t = t.getParentItem())
		{
			Object data = t.getData();
			if(data instanceof Node && key.equals(((Node)data).key))
			{
				return true;
			}
		}
		return false;
	}

	// Opens the selected call site; activating the same row again moves to its next call site.
	void activate()
	{
		TreeItem[] sel = tree.getSelection();
		if(sel.length == 0 || !(sel[0].getData() instanceof Node) || opener == null)
		{
			return;
		}
		Node n = (Node)sel[0].getData();
		if(n.sites.isEmpty())
		{
			return;
		}
		CodeModel.Place p = n.sites.get(n.next % n.sites.size());
		n.next++;
		opener.accept(new Location(p.file, p.line, p.column));
	}
}
