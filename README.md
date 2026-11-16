# JavaPad

A small desktop editor (SWT) for Eclipse-style Java projects. It opens a project folder (`.project` and `.classpath`, created if missing), edits its sources, and builds, runs and tests it with the JDK it runs on.

## Build and run

```
mvn package                      # target/javapad.jar (everything inside) and lib/ (the jars below)
java -jar target/javapad.jar [project-folder]
```

Without Maven: put the jars in `lib/` on the class path, compile `src/main/java` with `javac`, and run `javapad.JavaPad`. The JDK must provide `jdk.compiler`.

## Using it

- **Ctrl+3** runs any menu command or switches to an open tab; **Ctrl+Shift+R** opens a file by name.
- **Ctrl+Space** completes; typing `.` opens the list. **Ctrl+Shift+O** organizes imports, **Ctrl+Shift+M** adds the import for the name at the caret, **Ctrl+1** is quick fix, **Ctrl+Shift+F** formats the selection (or the file).
- **F3** / Ctrl+click open a declaration, **Ctrl+T** an implementation, **Ctrl+Alt+H** the call hierarchy.
- **Ctrl+B** builds, **Ctrl+R** builds and runs the active file, **F11** repeats the last main class or test run, **Ctrl+F11** runs the tests, **Ctrl+Alt+R** the test at the caret.
- The console is also stdin: type after the last output and press Enter.
- **Git** (needs `git` on the PATH): the sidebar's right-click menu has a Git entry (commit, push, pull, switch, merge, rebase, reset, stash, ignore...), untracked files carry a small `?` box, and the History tab shows the commit graph, the changes of a commit and a context menu for commits and their files.

## Code map

| Package | What it holds |
| --- | --- |
| `javapad.ui` | The SWT window. `MainWindow` wires the parts together; `MenuBuilder`, `EditorMenu`, `FileActions`, `RunController` and `Automation` are the pieces it delegates to. `GitActions`, `HistoryActions`, `HistoryPane`, `GitMenu` and `CommitDialog` are the git side. `CodeEditor` is the editor (key handling, styling, tooltips); `LineEdits` holds its line commands. |
| `javapad.lang` | Code intelligence on javac's Tree API: `TypeIndex`, `CompletionEngine`, `ImportEditor`/`ImportFixer`, `CodeModel` (implementations, call hierarchy), `JavaFormatter`, `CodeMask`. No SWT. |
| `javapad.git` | The `git` command line: `GitRepo` (commands), `GraphLayout` (the commit graph), parsers. No SWT. |
| `javapad.run` | Starting programs and tests: `Launcher`, `MainFinder`, `TestFinder`, `JUnitReport`, `LiveTail`. |
| `javapad.build` | `ProjectCompiler`, which compiles in process. |
| `javapad.model` | `EclipseProject`, `ClasspathParser`, `ProjectGenerator`, `JavaTemplate`. |
| `javapad.runner` | Classes that run inside the edited project's JVM (`JUnit4Runner`, `LiveLog`, `LiveJupiterListener`). |

Code style: tabs, `if(`, braces on every block and on their own line, one statement per line, fields first.

## Tests

`src/test/java` holds JUnit 4 tests for everything that does not need a display. Run them with JUnitCore against `target/classes`, or from your IDE.

## Headless runs

`Automation` reads `-Djavapad.*` properties (`open`, `file`, `action`, `tab`, `shot`, `wait`, `early`, …) so a run can be driven and screenshotted under `xvfb-run`; see the constants at the top of `ui/Automation.java`.
