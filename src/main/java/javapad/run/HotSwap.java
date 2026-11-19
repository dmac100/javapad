package javapad.run;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.zip.CRC32;

// Replaces the code of a running program after a build, like Eclipse's hot code replace. The program is started with the
// JDWP agent listening on a local port; we attach with JDI and, after each build, redefine the loaded classes whose class
// files changed. A standard JVM only accepts changes inside method bodies; anything else is reported and needs a restart.
public class HotSwap
{
	static final File PREFERENCE = new File(System.getProperty("user.home"), ".javapad/hotswap");
	static final int ATTACH_TIMEOUT_MS = 10000;

	private final File outputFolder;
	private final int port;
	// output(message, isError)
	private final BiConsumer<String, Boolean> output;
	// the checksum of each class file as the running program has it, by binary class name
	private final Map<String, Long> running;
	private VirtualMachine vm;
	private boolean closed;

	// Takes the snapshot of the class files the program will start with; call it after the build and before the launch.
	public HotSwap(File outputFolder, BiConsumer<String, Boolean> output)
	{
		this.outputFolder = outputFolder;
		this.output = output;
		this.port = freePort();
		running = snapshot(outputFolder);
	}

	public static boolean enabled()
	{
		try
		{
			return !new String(Files.readAllBytes(PREFERENCE.toPath()), StandardCharsets.UTF_8).trim().equals("off");
		}
		catch(IOException e)
		{
			return true;
		}
	}

	public static void setEnabled(boolean on)
	{
		try
		{
			PREFERENCE.getParentFile().mkdirs();
			Files.write(PREFERENCE.toPath(), (on ? "on" : "off").getBytes(StandardCharsets.UTF_8));
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	// whether the command already sets up a debugger of its own (in the user's VM arguments)
	public static boolean hasAgent(List<String> command)
	{
		for(String s : command)
		{
			if(s.contains("jdwp"))
			{
				return true;
			}
		}
		return false;
	}

	// The VM argument that makes the program listen for us; quiet, so it prints nothing of its own.
	public String agentArgument()
	{
		return "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,quiet=y,address=127.0.0.1:" + port;
	}

	static int freePort()
	{
		try(ServerSocket s = new ServerSocket(0))
		{
			return s.getLocalPort();
		}
		catch(IOException e)
		{
			return 47011;
		}
	}

	// Attaches in the background, retrying while the program starts up.
	public void attach()
	{
		Thread t = new Thread(() ->
		{
			long until = System.currentTimeMillis() + ATTACH_TIMEOUT_MS;
			Exception last = null;
			while(!isClosed() && System.currentTimeMillis() < until)
			{
				try
				{
					VirtualMachine attached = connect();
					synchronized(this)
					{
						if(closed)
						{
							attached.dispose();
						}
						else
						{
							vm = attached;
						}
					}
					return;
				}
				catch(Exception e)
				{
					last = e;
					sleep(100);
				}
			}
			if(!isClosed())
			{
				output.accept("Hot swap is off for this run: cannot attach to the program (" + last + ")\n", true);
			}
		}, "hotswap-attach");
		t.setDaemon(true);
		t.start();
	}

	VirtualMachine connect() throws Exception
	{
		AttachingConnector socket = null;
		for(AttachingConnector c : Bootstrap.virtualMachineManager().attachingConnectors())
		{
			if(c.transport().name().equals("dt_socket"))
			{
				socket = c;
			}
		}
		if(socket == null)
		{
			throw new IllegalStateException("no socket connector");
		}
		Map<String, Connector.Argument> args = socket.defaultArguments();
		args.get("hostname").setValue("127.0.0.1");
		args.get("port").setValue(String.valueOf(port));
		if(args.containsKey("timeout"))
		{
			args.get("timeout").setValue("2000");
		}
		return socket.attach(args);
	}

	synchronized boolean isClosed()
	{
		return closed;
	}

	public synchronized void close()
	{
		closed = true;
		if(vm != null)
		{
			try
			{
				vm.dispose();
			}
			catch(Exception e)
			{
				// the program has already gone
			}
			vm = null;
		}
	}

	// Call after a successful build: redefines the changed classes in the background and reports what happened.
	public void swap()
	{
		Thread t = new Thread(() -> swapNow(), "hotswap");
		t.setDaemon(true);
		t.start();
	}

	synchronized void swapNow()
	{
		if(closed)
		{
			return;
		}
		if(vm == null)
		{
			output.accept("Not hot-swapped: still connecting to the program\n", true);
			return;
		}
		Map<String, Long> now = snapshot(outputFolder);
		Map<ReferenceType, byte[]> redefine = new HashMap<ReferenceType, byte[]>();
		Map<String, Long> redefined = new HashMap<String, Long>();
		List<String> names = new ArrayList<String>();
		try
		{
			for(Map.Entry<String, Long> e : now.entrySet())
			{
				String name = e.getKey();
				Long before = running.get(name);
				if(before == null || before.equals(e.getValue()))
				{
					// unchanged, or new: a new class is loaded from disk when it is first used
					continue;
				}
				List<ReferenceType> loaded = vm.classesByName(name);
				if(loaded.isEmpty())
				{
					// not loaded yet, so the program will read the new class file anyway
					running.put(name, e.getValue());
					continue;
				}
				byte[] bytes = Files.readAllBytes(classFile(name).toPath());
				for(ReferenceType type : loaded)
				{
					redefine.put(type, bytes);
				}
				redefined.put(name, e.getValue());
				names.add(simpleName(name));
			}
			if(redefine.isEmpty())
			{
				return;
			}
			if(!vm.canRedefineClasses())
			{
				output.accept("Not hot-swapped: this JVM cannot redefine classes\n", true);
				return;
			}
			vm.redefineClasses(redefine);
			running.putAll(redefined);
			names.sort(null);
			output.accept("Hot-swapped " + names.size() + (names.size() == 1 ? " class: " : " classes: ") + String.join(", ", names) + "\n", false);
		}
		catch(UnsupportedOperationException e)
		{
			output.accept("Hot swap failed: " + reason(e) + ". Restart the program to use the new code.\n", true);
		}
		catch(com.sun.jdi.VMDisconnectedException e)
		{
			vm = null;
		}
		catch(Exception | LinkageError e)
		{
			output.accept("Hot swap failed: " + e + "\n", true);
		}
	}

	// JDI's messages for changes the JVM refuses, in words that say what to do about them
	static String reason(UnsupportedOperationException e)
	{
		String m = e.getMessage() == null ? "" : e.getMessage();
		if(m.contains("add method") || m.contains("delete method"))
		{
			return "a method was added or removed";
		}
		if(m.contains("schema change") || m.contains("field"))
		{
			return "fields were added, removed or changed";
		}
		if(m.contains("hierarchy"))
		{
			return "the superclass or interfaces changed";
		}
		if(m.contains("modifiers"))
		{
			return "the modifiers of a class or method changed";
		}
		return m.isEmpty() ? "the JVM refused the change" : m;
	}

	static String simpleName(String binaryName)
	{
		return binaryName.substring(binaryName.lastIndexOf('.') + 1);
	}

	File classFile(String binaryName)
	{
		return new File(outputFolder, binaryName.replace('.', File.separatorChar) + ".class");
	}

	// binary class name -> checksum, for every class file under the folder
	static Map<String, Long> snapshot(File folder)
	{
		Map<String, Long> result = new HashMap<String, Long>();
		Path root = folder.toPath();
		if(!folder.isDirectory())
		{
			return result;
		}
		try(Stream<Path> files = Files.walk(root))
		{
			files.filter(p -> p.toString().endsWith(".class")).forEach(p ->
			{
				String rel = root.relativize(p).toString();
				String name = rel.substring(0, rel.length() - ".class".length()).replace(File.separatorChar, '.');
				try
				{
					CRC32 crc = new CRC32();
					crc.update(Files.readAllBytes(p));
					result.put(name, crc.getValue());
				}
				catch(IOException e)
				{
					// a file that cannot be read is left out
				}
			});
		}
		catch(IOException e)
		{
			// an unreadable folder has nothing to swap
		}
		return result;
	}

	static void sleep(long ms)
	{
		try
		{
			Thread.sleep(ms);
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}
}
