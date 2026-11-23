package javapad.ui.chat;

// The chat AIs that can be used from the pane. Both run in the multipass virtual machine "claude".
public enum Assistant {
	CLAUDE("Claude Code", "claude"),
	OPENCODE("OpenCode", "opencode");

	public final String label;
	public final String cli;

	Assistant(String label, String cli) {
		this.label = label;
		this.cli = cli;
	}

	// the choice shown in a dropdown, defaulting to Claude Code for an unknown label
	static Assistant withLabel(String label) {
		for(Assistant a : values()) {
			if(a.label.equals(label)) {
				return a;
			}
		}
		return CLAUDE;
	}
}
