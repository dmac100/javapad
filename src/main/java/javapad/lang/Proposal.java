package javapad.lang;

public class Proposal {
	public static final int LOCAL = 0;
	public static final int FIELD = 1;
	public static final int METHOD = 2;
	public static final int TYPE = 3;
	public static final int PACKAGE = 4;
	public static final int KEYWORD = 5;

	public final int kind;
	public final String name;
	public final String label;
	public final String detail;
	public final String insert;
	public final String importType;
	public final boolean hasParams;
	public final int rank;

	public Proposal(int kind, String name, String label, String detail, String insert, String importType, boolean hasParams, int rank) {
		this.kind = kind;
		this.name = name;
		this.label = label;
		this.detail = detail;
		this.insert = insert;
		this.importType = importType;
		this.hasParams = hasParams;
		this.rank = rank;
	}

	public boolean isMethod() {
		return kind == METHOD;
	}

	public String kindLetter() {
		switch(kind) {
		case LOCAL:
			return "v";
		case FIELD:
			return "f";
		case METHOD:
			return "m";
		case TYPE:
			return "T";
		case PACKAGE:
			return "p";
		default:
			return "k";
		}
	}

	public String toString() {
		return label;
	}
}
