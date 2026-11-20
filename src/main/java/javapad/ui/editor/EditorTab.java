package javapad.ui.editor;

import javapad.ui.Theme;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;

public class EditorTab {
	public File file;
	public CTabItem item;
	public final CodeEditor editor;
	private boolean dirty;
	// set while the tab is moved to another folder, when its control must outlive the old item
	private boolean moving;

	public EditorTab(CTabFolder folder, File file, Theme theme) throws IOException {
		this.file = file;
		item = new CTabItem(folder, SWT.CLOSE);
		editor = new CodeEditor(folder, theme, file.getName().endsWith(".java"));
		item.setControl(editor.getFrame());
		bind(item);
		editor.setContent(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		updateTitle();
		editor.setModifyHook(() -> {
			dirty = true;
			updateTitle();
		});
	}

	// closing a tab does not dispose its control by itself
	private void bind(CTabItem bound) {
		bound.addListener(SWT.Dispose, e -> {
			if(!moving) {
				editor.getFrame().dispose();
			}
		});
		bound.setData(this);
		bound.setToolTipText(file.getAbsolutePath());
	}

	// Moves the tab (with its editor) to a folder, at the index.
	public void moveTo(CTabFolder folder, int index) {
		CTabItem old = item;
		boolean sameFolder = old.getParent() == folder;
		moving = true;
		old.dispose();
		item = new CTabItem(folder, SWT.CLOSE, Math.min(index, folder.getItemCount()));
		if(!sameFolder) {
			editor.getFrame().setParent(folder);
		}
		item.setControl(editor.getFrame());
		bind(item);
		updateTitle();
		moving = false;
		folder.setSelection(item);
	}

	// The file was moved or renamed on disk; the tab, with any unsaved changes, follows it.
	public void rename(File moved) {
		file = moved;
		item.setToolTipText(moved.getAbsolutePath());
		updateTitle();
	}

	public boolean isDirty() {
		return dirty;
	}

	public void save() throws IOException {
		Files.write(file.toPath(), editor.getContent().getBytes(StandardCharsets.UTF_8));
		dirty = false;
		updateTitle();
	}

	// Re-reads the file if it changed on disk (after a git checkout, say) and keeps the tab clean.
	public void reload() throws IOException {
		String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		if(!content.equals(editor.getContent())) {
			editor.replaceContentMinimal(content);
		}
		dirty = false;
		updateTitle();
	}

	void updateTitle() {
		item.setText((dirty ? "*" : "") + file.getName());
	}
}
