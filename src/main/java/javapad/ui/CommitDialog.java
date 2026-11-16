package javapad.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javapad.git.FileChange;
import javapad.git.GitRepo;
import javapad.git.GitStatus;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

// The commit dialog: message, author, committer, amend, and the files to include.
class CommitDialog
{
	static class Request
	{
		String message;
		String author;
		String committer;
		boolean amend;
		boolean push;
		List<String> paths = new ArrayList<String>();
	}

	private final Shell parent;
	private final GitRepo repo;
	private final GitStatus status;
	private final BiConsumer<Shell, FileChange> comparer;
	private Request result;
	private Table table;
	private Button untrackedBox;
	private final List<FileChange> listed = new ArrayList<FileChange>();
	private final List<String> checked = new ArrayList<String>();

	// comparer: shows a file next to its last committed version (double-click in the list)
	CommitDialog(Shell parent, GitRepo repo, GitStatus status, BiConsumer<Shell, FileChange> comparer)
	{
		this.comparer = comparer;
		this.parent = parent;
		this.repo = repo;
		this.status = status;
	}

	Request open()
	{
		Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		dialog.setText("Commit");
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 8;
		layout.marginHeight = 8;
		dialog.setLayout(layout);
		label(dialog, "Message:");
		Text message = new Text(dialog, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		GridData md = new GridData(SWT.FILL, SWT.FILL, true, false);
		md.heightHint = 80;
		md.widthHint = 800;
		message.setLayoutData(md);
		label(dialog, "Author:");
		Text author = field(dialog, repo.identity());
		label(dialog, "Committer:");
		Text committer = field(dialog, repo.identity());
		Button amend = new Button(dialog, SWT.CHECK);
		amend.setText("Amend previous commit");
		amend.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		table = new Table(dialog, SWT.CHECK | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		GridData td = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		td.heightHint = 260;
		table.setLayoutData(td);
		table.setHeaderVisible(true);
		String[] titles = {"File", "State"};
		int[] widths = {620, 160};
		for(int i = 0; i < titles.length; i++)
		{
			TableColumn c = new TableColumn(table, SWT.NONE);
			c.setText(titles[i]);
			c.setWidth(widths[i]);
		}
		// one row: the selection buttons at the left, the commit buttons at the right edge
		Composite row = new Composite(dialog, SWT.NONE);
		row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		GridLayout rl = new GridLayout(2, false);
		rl.marginWidth = 0;
		rl.marginHeight = 0;
		row.setLayout(rl);
		Composite selection = new Composite(row, SWT.NONE);
		selection.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
		GridLayout sl = new GridLayout(3, false);
		sl.marginWidth = 0;
		sl.marginHeight = 0;
		selection.setLayout(sl);
		untrackedBox = new Button(selection, SWT.CHECK);
		untrackedBox.setText("Show untracked files");
		untrackedBox.setSelection(true);
		Button all = new Button(selection, SWT.PUSH);
		all.setText("Select All");
		Button none = new Button(selection, SWT.PUSH);
		none.setText("Deselect All");
		Composite buttons = new Composite(row, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
		GridLayout bl = new GridLayout(3, false);
		bl.marginWidth = 0;
		bl.marginHeight = 0;
		buttons.setLayout(bl);
		Button commitPush = new Button(buttons, SWT.PUSH);
		commitPush.setText("Commit and Push");
		Button commit = new Button(buttons, SWT.PUSH);
		commit.setText("Commit");
		Button cancel = new Button(buttons, SWT.PUSH);
		cancel.setText("Cancel");
		for(FileChange c : status.changes)
		{
			if(!c.untracked())
			{
				checked.add(c.path);
			}
		}
		fill();
		untrackedBox.addListener(SWT.Selection, e ->
		{
			remember();
			fill();
		});
		table.addListener(SWT.DefaultSelection, e ->
		{
			int i = table.getSelectionIndex();
			if(i >= 0 && i < listed.size())
			{
				comparer.accept(dialog, listed.get(i));
			}
		});
		all.addListener(SWT.Selection, e -> setAll(true));
		none.addListener(SWT.Selection, e -> setAll(false));
		amend.addListener(SWT.Selection, e ->
		{
			if(amend.getSelection() && message.getText().trim().isEmpty() && repo.hasCommits())
			{
				message.setText(repo.lastMessage());
				author.setText(repo.lastAuthor());
			}
		});
		commit.addListener(SWT.Selection, e -> accept(dialog, message, author, committer, amend, false));
		commitPush.addListener(SWT.Selection, e -> accept(dialog, message, author, committer, amend, true));
		cancel.addListener(SWT.Selection, e -> dialog.dispose());
		dialog.pack();
		dialog.setLocation(parent.getLocation().x + 40, parent.getLocation().y + 40);
		dialog.open();
		message.setFocus();
		Display display = parent.getDisplay();
		while(!dialog.isDisposed())
		{
			if(!display.readAndDispatch())
			{
				display.sleep();
			}
		}
		return result;
	}

	static void label(Composite parent, String text)
	{
		Label l = new Label(parent, SWT.NONE);
		l.setText(text);
		l.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
	}

	static Text field(Composite parent, String text)
	{
		Text t = new Text(parent, SWT.BORDER);
		t.setText(text);
		t.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		return t;
	}

	// remembers which listed files are ticked, so toggling "show untracked" keeps the choice
	void remember()
	{
		for(int i = 0; i < listed.size(); i++)
		{
			String path = listed.get(i).path;
			checked.remove(path);
			if(table.getItem(i).getChecked())
			{
				checked.add(path);
			}
		}
	}

	void fill()
	{
		table.removeAll();
		listed.clear();
		for(FileChange c : status.changes)
		{
			if(c.untracked() && !untrackedBox.getSelection())
			{
				continue;
			}
			listed.add(c);
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(new String[] {c.path, c.label()});
			item.setChecked(checked.contains(c.path));
		}
	}

	void setAll(boolean on)
	{
		for(TableItem item : table.getItems())
		{
			item.setChecked(on);
		}
	}

	void accept(Shell dialog, Text message, Text author, Text committer, Button amend, boolean push)
	{
		remember();
		Request r = new Request();
		r.message = message.getText().trim();
		if(r.message.isEmpty())
		{
			ConfirmDialog.message(dialog, "Commit", "Enter a commit message.", SWT.ICON_WARNING);
			return;
		}
		r.author = author.getText();
		r.committer = committer.getText();
		r.amend = amend.getSelection();
		r.push = push;
		for(FileChange c : status.changes)
		{
			if(checked.contains(c.path))
			{
				r.paths.add(c.path);
			}
		}
		if(r.paths.isEmpty() && !r.amend)
		{
			ConfirmDialog.message(dialog, "Commit", "Select at least one file.", SWT.ICON_WARNING);
			return;
		}
		result = r;
		dialog.dispose();
	}
}
