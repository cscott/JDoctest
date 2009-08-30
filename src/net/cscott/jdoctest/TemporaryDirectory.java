package net.cscott.jdoctest;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Create a temporary directory.
 * From <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4735419">Sun bug 4735419</a>
 */
@SuppressWarnings("serial")
class TemporaryDirectory extends File
{

    public TemporaryDirectory(String p) {
        super(p);
    }

    /* -- Temporary files -- */

    private static final Object tmpDirectoryLock = new Object();
    private static final Random counter = new Random();

    private static File generateFile(String prefix, String suffix, File dir)
    throws IOException
    {
        int v = counter.nextInt() & 0xffff;
        return new File(dir, prefix + Integer.toString(v) + suffix);
    }

    public static File createTempDirectory(String prefix, String suffix,
                                           File directory)
        throws IOException
    {
        if (prefix == null) throw new NullPointerException();
        if (prefix.length() < 3)
            throw new IllegalArgumentException("Prefix string too short");
        String s = (suffix == null) ? ".tmp" : suffix;
        synchronized (tmpDirectoryLock) {
            if (directory == null) {
                String tmpDir = getTempDir();
                directory = new File(tmpDir);
            }
            if (!directory.isDirectory())
                throw new IOException("Parent directory doesn't exist");
            File f;
            do {
                f = generateFile(prefix, s, directory);
            } while (!f.mkdir());
            return f;
        }
    }


    public static File createTempDirectory(String prefix, String suffix)
        throws IOException
    {
        return createTempDirectory(prefix, suffix, null);
    }
    public static File createTempDirectory(String prefix)
        throws IOException
    {
        return createTempDirectory(prefix, null, null);
    }
    public static File createTempDirectory()
        throws IOException
    {
        return createTempDirectory("tmp", null, null);
    }

    private static String getTempDir() {
        return System.getProperty("java.io.tmpdir");
    }

    /** Recursively delete a directory. */
    public static boolean deleteAll(File d) {
        if (d.isDirectory())
            for (File f : d.listFiles())
                deleteAll(f);
        return d.delete();
    }
}

