package javapad.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;

public class EditorTab
{
	public final File file;
	public final CTabItem item;
	public final CodeEditor editor;
	private boolean dirty;

	public EditorTab(CTabFolder folder, File file, Theme theme) throws IOException
	{
		this.file = file;
		item = new CTabItem(folder, SWT.CLOSE);
		editor = new CodeEditor(folder, theme, file.getName().endsWith(".java"));
		item.setControl(editor.getControl());
		item.setData(this);
		item.setToolTipText(file.getAbsolutePath());
		editor.setContent(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		updateTitle();
		editor.setModifyHook(() ->
		{
			dirty = true;
			updateTitle();
		});
	}

	public boolean isDirty()
	{
		return dirty;
	}

	public void save() throws IOException
	{
		Files.write(file.toPath(), editor.getContent().getBytes(StandardCharsets.UTF_8));
		dirty = false;
		updateTitle();
	}

	void updateTitle()
	{
		item.setText((dirty ? "*" : "") + file.getName());
	}
}
