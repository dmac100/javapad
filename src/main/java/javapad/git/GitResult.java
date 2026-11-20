package javapad.git;

// The outcome of one git command.
public class GitResult {
	public final int exit;
	public final String out;
	public final String err;

	public GitResult(int exit, String out, String err) {
		this.exit = exit;
		this.out = out;
		this.err = err;
	}

	public boolean ok() {
		return exit == 0;
	}

	// the error text if there is one, otherwise the output
	public String message() {
		String s = err.trim().isEmpty() ? out : err;
		return s.trim();
	}
}
