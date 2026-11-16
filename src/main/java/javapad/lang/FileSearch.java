package javapad.lang;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Searches the text of files line by line. No SWT.
public class FileSearch
{
	// A line that contains the text: its number (1-based), the column of the first match (1-based), the length of that match and the line itself.
	public static class Match
	{
		public final File file;
		public final int line;
		public final int column;
		public final int length;
		public final String text;

		public Match(File file, int line, int column, int length, String text)
		{
			this.file = file;
			this.line = line;
			this.column = column;
			this.length = length;
			this.text = text;
		}
	}

	public static class Result
	{
		public final List<Match> matches = new ArrayList<Match>();
		public int filesSearched;
		// set when the search stopped at the limit
		public boolean truncated;
	}

	// regex: treat the query as a regular expression (throws PatternSyntaxException if it is not one)
	public static Pattern pattern(String query, boolean caseSensitive, boolean regex)
	{
		int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		return Pattern.compile(regex ? query : Pattern.quote(query), flags);
	}

	// unsaved: text of files with unsaved changes in an editor, used instead of what is on disk
	public static Result search(List<File> files, Map<File, String> unsaved, Pattern pattern, int limit)
	{
		Result result = new Result();
		for(File f : files)
		{
			String text = unsaved.containsKey(f) ? unsaved.get(f) : read(f);
			if(text == null)
			{
				continue;
			}
			result.filesSearched++;
			String[] lines = text.split("\r?\n", -1);
			for(int i = 0; i < lines.length; i++)
			{
				Matcher m = pattern.matcher(lines[i]);
				if(m.find())
				{
					if(result.matches.size() >= limit)
					{
						result.truncated = true;
						return result;
					}
					result.matches.add(new Match(f, i + 1, m.start() + 1, Math.max(1, m.end() - m.start()), lines[i]));
				}
			}
		}
		return result;
	}

	// the text of a file, or null if it cannot be read or looks binary
	static String read(File f)
	{
		try
		{
			byte[] bytes = Files.readAllBytes(f.toPath());
			for(int i = 0; i < Math.min(bytes.length, 8000); i++)
			{
				if(bytes[i] == 0)
				{
					return null;
				}
			}
			return new String(bytes, StandardCharsets.UTF_8);
		}
		catch(Exception e)
		{
			return null;
		}
	}
}
