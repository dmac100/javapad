package javapad.run;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class LaunchSettings
{
	private final File file;
	private final Properties props = new Properties();

	public LaunchSettings(File projectRoot)
	{
		file = new File(projectRoot, ".javapad/launch.properties");
		if(file.exists())
		{
			try
			{
				FileInputStream in = new FileInputStream(file);
				props.load(in);
				in.close();
			}
			catch(Exception e)
			{
				// no saved settings yet, or they cannot be read: use the defaults
			}
		}
	}

	public String getArgs(String className)
	{
		return props.getProperty(className + ".args", "");
	}

	public String getVmArgs(String className)
	{
		return props.getProperty(className + ".vm", "");
	}

	public String getLastMain()
	{
		return props.getProperty("lastMain", "");
	}

	public void setLastMain(String className)
	{
		props.setProperty("lastMain", className);
		save();
	}

	public void set(String className, String args, String vmArgs)
	{
		props.setProperty(className + ".args", args);
		props.setProperty(className + ".vm", vmArgs);
		save();
	}

	void save()
	{
		try
		{
			file.getParentFile().mkdirs();
			FileOutputStream out = new FileOutputStream(file);
			props.store(out, null);
			out.close();
		}
		catch(Exception e)
		{
			// the settings simply are not remembered when the file cannot be written
		}
	}
}
