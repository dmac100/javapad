package javapad.git;

// One entry of "git stash list".
public class Stash {
	public final String ref;
	public final String description;

	public Stash(String ref, String description) {
		this.ref = ref;
		this.description = description;
	}

	public String toString() {
		return ref + ": " + description;
	}
}
