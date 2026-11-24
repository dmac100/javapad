package javapad.ui.pane;

import javapad.lang.Outline;
import javapad.ui.Theme;
import javapad.ui.editor.EditorTab;
import javapad.ui.editor.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

// The Outline tab: the types, methods and fields of the active Java file, in source order. It follows the editor as
// it is typed in, selects the declaration around the caret, and opens a declaration when one is chosen.
public class OutlinePane {
	// wait this long after a change before parsing again, so that typing does not start a parse per key
	static final int REFRESH_MILLIS = 400;

	private final Composite composite;
	private final Tree tree;
	private final Display display;
	private Theme theme;
	private Consumer<Location> opener;
	private EditorTab tab;
	private StyledText watched;
	private List<Outline.Node> roots = new ArrayList<Outline.Node>();
	private final Map<Outline.Node, TreeItem> items = new HashMap<Outline.Node, TreeItem>();
	private TreeItem messageItem;
	// tells the tree selections made here from the ones the user makes
	private boolean updating;
	private int generation;
	private int pending;
	private final ModifyListener modifyListener = e -> modified();
	private final CaretListener caretListener = e -> syncSelection();

	public OutlinePane(Composite parent, Theme theme) {
		this.theme = theme;
		display = parent.getDisplay();
		composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		composite.setLayout(layout);
		tree = new Tree(composite, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
		tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tree.addListener(SWT.Selection, e -> openSelected());
		tree.addListener(SWT.DefaultSelection, e -> openSelected());
		restyle(theme);
		showMessage("No file open.");
	}

	public Composite getControl() {
		return composite;
	}

	public void setOpener(Consumer<Location> opener) {
		this.opener = opener;
	}

	// Shows the declarations of the active editor, or empties the view when there is none.
	public void setEditor(EditorTab editor) {
		if(editor == tab) {
			syncSelection();
			return;
		}
		detach();
		tab = editor;
		roots = new ArrayList<Outline.Node>();
		if(editor == null) {
			showMessage("No file open.");
			return;
		}
		if(!editor.file.getName().endsWith(".java")) {
			showMessage("No outline for " + editor.file.getName() + ".");
			return;
		}
		watched = editor.editor.getControl();
		watched.addModifyListener(modifyListener);
		watched.addCaretListener(caretListener);
		showMessage("Reading " + editor.file.getName() + " ...");
		refresh();
	}

	void detach() {
		if(watched != null && !watched.isDisposed()) {
			watched.removeModifyListener(modifyListener);
			watched.removeCaretListener(caretListener);
		}
		watched = null;
	}

	// Parses the file again; called when the tab is shown, and by the editor as it changes.
	public void refresh() {
		if(tab == null) {
			showMessage("No file open.");
			return;
		}
		if(!tab.file.getName().endsWith(".java")) {
			showMessage("No outline for " + tab.file.getName() + ".");
			return;
		}
		String content = tab.editor.getContent();
		EditorTab source = tab;
		int mine = ++generation;
		Thread worker = new Thread(() -> {
			List<Outline.Node> parsed = Outline.parse(content);
			display.asyncExec(() -> {
				if(tree.isDisposed() || mine != generation || source != tab) {
					return;
				}
				setNodes(parsed);
			});
		}, "outline");
		worker.setDaemon(true);
		worker.start();
	}

	void modified() {
		// nothing is parsed while the tab is hidden; it is refreshed when it is shown again
		if(!composite.isVisible()) {
			return;
		}
		int mine = ++pending;
		display.timerExec(REFRESH_MILLIS, () -> {
			if(!tree.isDisposed() && mine == pending) {
				refresh();
			}
		});
	}

	void setNodes(List<Outline.Node> parsed) {
		roots = parsed;
		items.clear();
		messageItem = null;
		tree.setRedraw(false);
		try {
			tree.removeAll();
			for(Outline.Node node : parsed) {
				add(node, null);
			}
			if(tree.getItemCount() == 0) {
				message("No declarations in " + (tab == null ? "this file" : tab.file.getName()) + ".");
			} else {
				for(TreeItem top : tree.getItems()) {
					top.setExpanded(true);
				}
			}
		} finally {
			tree.setRedraw(true);
		}
		syncSelection();
	}

	void add(Outline.Node node, TreeItem parent) {
		TreeItem item = parent == null ? new TreeItem(tree, SWT.NONE) : new TreeItem(parent, SWT.NONE);
		item.setText(node.label);
		item.setData(node);
		items.put(node, item);
		for(Outline.Node child : node.children) {
			add(child, item);
		}
	}

	void showMessage(String message) {
		generation++;
		roots = new ArrayList<Outline.Node>();
		items.clear();
		tree.removeAll();
		message(message);
	}

	void message(String message) {
		messageItem = new TreeItem(tree, SWT.NONE);
		messageItem.setText(message);
		messageItem.setForeground(theme.gutter);
	}

	// Selects the declaration around the caret without moving the editor.
	void syncSelection() {
		if(updating || watched == null || watched.isDisposed() || roots.isEmpty()) {
			return;
		}
		int line = watched.getLineAtOffset(watched.getCaretOffset()) + 1;
		Outline.Node node = Outline.atLine(roots, line);
		TreeItem item = node == null ? null : items.get(node);
		if(item == null) {
			return;
		}
		TreeItem[] selected = tree.getSelection();
		if(selected.length == 1 && selected[0] == item) {
			return;
		}
		updating = true;
		try {
			tree.setSelection(item);
			tree.showItem(item);
		} finally {
			updating = false;
		}
	}

	void openSelected() {
		if(updating || tab == null || opener == null) {
			return;
		}
		TreeItem[] selected = tree.getSelection();
		if(selected.length != 1 || !(selected[0].getData() instanceof Outline.Node)) {
			return;
		}
		Outline.Node node = (Outline.Node)selected[0].getData();
		opener.accept(new Location(tab.file, node.line, node.column));
	}

	public void restyle(Theme theme) {
		this.theme = theme;
		composite.setBackground(theme.sidebar);
		tree.setBackground(theme.sidebar);
		tree.setForeground(theme.sidebarText);
		if(messageItem != null && !messageItem.isDisposed()) {
			messageItem.setForeground(theme.gutter);
		}
	}
}
