package javapad.ui.dialog;

import javapad.lang.Outline;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

// Ctrl+O: type part of a method's name to pick one from the current file, like Eclipse's quick outline.
// It looks and works like the Ctrl+Shift+R open-file dialog.
public class QuickOutlineDialog {
	private final Shell parent;
	private final List<Outline.Node> methods;
	private final String title;
	private Shell shell;
	private Text text;
	private org.eclipse.swt.widgets.List list;
	private List<Outline.Node> shown = new ArrayList<Outline.Node>();
	private Outline.Node result;

	public QuickOutlineDialog(Shell parent, List<Outline.Node> methods, String file) {
		this.parent = parent;
		this.methods = methods;
		this.title = "Go to Method in " + file;
	}

	public boolean isEmpty() {
		return methods.isEmpty();
	}

	public Outline.Node open() {
		return open(null);
	}

	public Outline.Node open(Runnable shown) {
		GridLayout layout = new GridLayout(1, false);
		layout.verticalSpacing = 2;
		layout.marginWidth = 2;
		layout.marginHeight = 2;
		shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		shell.setLayout(layout);
		shell.setSize(560, 380);
		shell.setText(title);
		Rectangle b = parent.getBounds();
		Point size = shell.getSize();
		shell.setLocation(b.x + (b.width - size.x) / 2, b.y + (b.height - size.y) / 3);
		text = new Text(shell, SWT.BORDER);
		list = new org.eclipse.swt.widgets.List(shell, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		text.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		list.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		list.addListener(SWT.DefaultSelection, e -> accept());
		list.addListener(SWT.FocusIn, e -> text.setFocus());
		list.addListener(SWT.MouseDoubleClick, e -> accept());
		text.addListener(SWT.Modify, e -> refresh());
		text.addListener(SWT.KeyDown, e -> key(e));
		refresh();
		shell.open();
		text.setFocus();
		if(shown != null) {
			parent.getDisplay().timerExec(400, shown);
		}
		Display display = parent.getDisplay();
		while(!shell.isDisposed()) {
			if(!display.readAndDispatch()) {
				display.sleep();
			}
		}
		return result;
	}

	public void setText(String s) {
		if(text != null && !text.isDisposed()) {
			text.setText(s);
			text.setSelection(s.length());
		}
	}

	public void close() {
		if(shell != null && !shell.isDisposed()) {
			shell.dispose();
		}
	}

	void key(Event e) {
		if(e.keyCode == SWT.ESC) {
			e.doit = false;
			shell.dispose();
		} else if(e.keyCode == SWT.ARROW_UP) {
			e.doit = false;
			move(-1);
		} else if(e.keyCode == SWT.ARROW_DOWN) {
			e.doit = false;
			move(1);
		} else if(e.keyCode == SWT.PAGE_UP) {
			e.doit = false;
			move(-10);
		} else if(e.keyCode == SWT.PAGE_DOWN) {
			e.doit = false;
			move(10);
		} else if(e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR || e.keyCode == SWT.LF) {
			e.doit = false;
			accept();
		}
	}

	void move(int delta) {
		int count = list.getItemCount();
		if(count == 0) {
			return;
		}
		int index = Math.max(0, Math.min(count - 1, list.getSelectionIndex() + delta));
		list.setSelection(index);
		list.showSelection();
	}

	public void accept() {
		if(list.getItemCount() == 0) {
			return;
		}
		int index = list.getSelectionIndex() >= 0 ? list.getSelectionIndex() : 0;
		result = shown.get(index);
		shell.dispose();
	}

	void refresh() {
		shown = Outline.search(methods, text.getText(), 300);
		List<String> labels = new ArrayList<String>();
		for(Outline.Node node : shown) {
			labels.add(Outline.label(node, true));
		}
		list.setItems(labels.toArray(new String[0]));
		if(list.getItemCount() > 0) {
			list.select(0);
		}
	}
}
