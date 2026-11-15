package javapad.lang;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ClassFlags
{
	public static final int PUBLIC = 0x0001;
	public static final int INTERFACE = 0x0200;
	public static final int SYNTHETIC = 0x1000;
	public static final int ANNOTATION = 0x2000;
	public static final int ENUM = 0x4000;

	// Reads just far enough into a class file to return its access flags.
	public static int read(InputStream in) throws IOException
	{
		DataInputStream d = new DataInputStream(in);
		if(d.readInt() != 0xCAFEBABE)
		{
			throw new IOException("not a class file");
		}
		d.readUnsignedShort();
		d.readUnsignedShort();
		int count = d.readUnsignedShort();
		for(int i = 1; i < count; i++)
		{
			int tag = d.readUnsignedByte();
			switch(tag)
			{
			case 1:
				d.skipNBytes(d.readUnsignedShort());
				break;
			case 3:
			case 4:
			case 9:
			case 10:
			case 11:
			case 12:
			case 17:
			case 18:
				d.skipNBytes(4);
				break;
			case 5:
			case 6:
				d.skipNBytes(8);
				i++;
				break;
			case 7:
			case 8:
			case 16:
			case 19:
			case 20:
				d.skipNBytes(2);
				break;
			case 15:
				d.skipNBytes(3);
				break;
			default:
				throw new IOException("bad constant pool tag " + tag);
			}
		}
		return d.readUnsignedShort();
	}
}
