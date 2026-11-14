package javapad.run;

public class TestResult
{
	public static final int PASSED = 0;
	public static final int FAILED = 1;
	public static final int ERROR = 2;
	public static final int SKIPPED = 3;

	public final String className;
	public final String name;
	public final double seconds;
	public final int status;
	public final String message;
	public final String trace;

	public TestResult(String className, String name, double seconds, int status, String message, String trace)
	{
		this.className = className;
		this.name = name;
		this.seconds = seconds;
		this.status = status;
		this.message = message;
		this.trace = trace;
	}

	public boolean bad()
	{
		return status == FAILED || status == ERROR;
	}
}
