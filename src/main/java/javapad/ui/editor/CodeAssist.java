package javapad.ui.editor;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javapad.lang.CodeModel;
import javapad.lang.CompletionEngine;
import javapad.lang.JavaAnalyzer;
import javapad.lang.TypeIndex;
import javapad.model.EclipseProject;
import org.eclipse.swt.widgets.Display;

// Owns the per-project type index and javac analyzer, and runs every request on one worker thread.
public class CodeAssist {
	private final Display display;
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "code-assist");
		t.setDaemon(true);
		return t;
	});
	private volatile TypeIndex index;
	private volatile JavaAnalyzer analyzer;
	private volatile CompletionEngine engine;
	private volatile EclipseProject project;
	private volatile CodeModel model;
	private int buildGeneration;
	private int modelGeneration;

	public CodeAssist(Display display) {
		this.display = display;
	}

	public void setProject(EclipseProject project) {
		this.project = project;
		JavaAnalyzer old = analyzer;
		if(old != null) {
			worker.execute(() -> old.invalidate());
		}
		analyzer = project == null ? null : new JavaAnalyzer(project);
		index = null;
		model = null;
		engine = analyzer == null ? null : new CompletionEngine(analyzer, () -> index);
		rebuildIndex();
	}

	// The source and jar listings changed (a file was saved, or a build ran).
	public void refresh() {
		JavaAnalyzer a = analyzer;
		if(a != null) {
			worker.execute(() -> a.invalidate());
			rebuildIndex();
		}
	}

	void rebuildIndex() {
		EclipseProject p = project;
		if(p == null) {
			return;
		}
		int generation = ++buildGeneration;
		Thread t = new Thread(() -> {
			TypeIndex built = TypeIndex.build(p.sourceFolders, p.libraries);
			if(generation == buildGeneration) {
				index = built;
			}
		}, "type-index");
		t.setDaemon(true);
		t.start();
		int modelBuild = ++modelGeneration;
		Thread m = new Thread(() -> {
			CodeModel built = CodeModel.build(p);
			if(modelBuild == modelGeneration) {
				model = built;
			}
		}, "code-model");
		m.setDaemon(true);
		m.start();
	}

	// Null until the first analysis of the whole project has finished.
	public CodeModel model() {
		return model;
	}

	public TypeIndex index() {
		return index;
	}

	public JavaAnalyzer analyzer() {
		return analyzer;
	}

	public boolean available() {
		return engine != null;
	}

	public void complete(File file, String text, int offset, Consumer<CompletionEngine.Completions> done) {
		CompletionEngine e = engine;
		if(e == null) {
			return;
		}
		worker.execute(() -> {
			CompletionEngine.Completions result = null;
			try {
				result = e.propose(file, text, offset);
			} catch(Throwable ex) {
				// a failed analysis yields no proposals
			}
			CompletionEngine.Completions finalResult = result;
			display.asyncExec(() -> {
				if(finalResult != null) {
					done.accept(finalResult);
				}
			});
		});
	}

	// Runs arbitrary work against the analyzer off the UI thread and returns the result on it.
	public <T> void submit(Supplier<T> work, Consumer<T> done) {
		worker.execute(() -> {
			T result = null;
			try {
				result = work.get();
			} catch(Throwable ex) {
				// a failed analysis yields no result
			}
			T finalResult = result;
			display.asyncExec(() -> done.accept(finalResult));
		});
	}
}
