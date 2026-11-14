package javapad.run;

public interface OutputListener
{
	void output(String text, boolean error);

	void finished(int exitCode);
}
