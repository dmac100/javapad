package javapad.run;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

// Follows the live.log written by the test runners and hands each complete line (split on tabs) to the sink.
public class LiveTail
{
	private final File file;
	private final Consumer<String[]> sink;
	private final Thread thread;
	private volatile boolean running = true;
	private long position;
	private String held = "";

	public LiveTail(File file, Consumer<String[]> sink)
	{
		this.file = file;
		this.sink = sink;
		thread = new Thread(() -> loop(), "live-tail");
		thread.setDaemon(true);
	}

	public void start()
	{
		thread.start();
	}

	// Reads whatever is left in the file, then stops.
	public void stop()
	{
		running = false;
		try
		{
			thread.join(2000);
		}
		catch(InterruptedException e)
		{
		}
	}

	void loop()
	{
		while(true)
		{
			boolean last = !running;
			drain();
			if(last)
			{
				return;
			}
			try
			{
				Thread.sleep(80);
			}
			catch(InterruptedException e)
			{
				return;
			}
		}
	}

	// Reads anything new right now, from any thread.
	public void flush()
	{
		drain();
	}

	synchronized void drain()
	{
		if(!file.exists() || file.length() <= position)
		{
			return;
		}
		try(RandomAccessFile in = new RandomAccessFile(file, "r"))
		{
			in.seek(position);
			byte[] data = new byte[(int)(in.length() - position)];
			in.readFully(data);
			position += data.length;
			held += new String(data, StandardCharsets.UTF_8);
		}
		catch(Exception e)
		{
			return;
		}
		int nl = held.indexOf('\n');
		while(nl >= 0)
		{
			String line = held.substring(0, nl);
			held = held.substring(nl + 1);
			if(!line.isEmpty())
			{
				sink.accept(line.split("\t", -1));
			}
			nl = held.indexOf('\n');
		}
	}
}
