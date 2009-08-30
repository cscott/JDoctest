package net.cscott.jdoctest;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/** Basic JUnit test: run the doc tests from the specified java source file. */
/* basic command to make things go:
 * javadoc -notree -noindex -nohelp -nonavbar -notimestamp -private -d /tmp/foo -quiet -taglet net.cscott.jdoctest.JDoctest -tagletpath jdoctest-1.4.jar:lib/rhino1_7R2-RC3/js.jar:bin -J-ea -classpath bin:/home/cananian/jdk1.6.0_11/lib/tools.jar:lib/rhino1_7R2-RC3/js.jar:lib/junit-4.6.jar src/net/cscott/jdoctest/JDoctest.java
 */
public class JavadocJUnitTestBridge {
    public final Class<?> klass;
    public final File sourceFile;
    public JavadocJUnitTestBridge(Class<?> klass, File sourceFile) {
        this.klass = klass;
        this.sourceFile = sourceFile;
    }
    /* stand-alone! */
    public static void main(String[] args) throws IOException {
        for (String a : args)
            new JavadocJUnitTestBridge(JavadocJUnitTestBridge.class, new File(a)).runDoctest();
    }

    @Test
    public void runDoctest() throws IOException {
        runDoctest(this.klass, this.sourceFile);
    }

    /** Helper class to bundle up a StringWriter as a PrintWriter. */
    static class SPWriter extends PrintWriter {
        StringWriter sw;
        SPWriter() { this(new StringWriter()); }
        private SPWriter(StringWriter sw) { super(sw); this.sw = sw; }
        public String toString() { flush(); return sw.toString(); }
    }
    // --- generic implementation, for reuse ---
    public static void runDoctest(Class<?> klass, File sourceFile) throws IOException {
        // Make a temp dir
        File tmpDir = TemporaryDirectory.createTempDirectory("jdoctest.");
        try {
            runDoctestWithTmpdir(klass, sourceFile, tmpDir);
        } finally {
            // Remove temporary output directory
            TemporaryDirectory.deleteAll(tmpDir);
        }
    }
    static void runDoctestWithTmpdir(Class<?> klass, File sourceFile, File tmpDir) {
        SPWriter errWriter = new SPWriter(),
                 warnWriter = new SPWriter(),
                 noticeWriter = new SPWriter();
        String[] args = {
                "-private", "-d", tmpDir.getPath(),
                "-notree", "-noindex", "-nohelp", "-nonavbar", "-notimestamp",
                 "-quiet",
                 "-taglet", JDoctest.class.getName(),
                 //"-tagletpath", tagletPath, "-J-ea", //XXX necessary?
                 //"-classpath", classPath, // XXX necessary?
                 sourceFile.getPath() };
        // provide annotation or some way to add additional arguments
        // or class path entries? (maybe for taglet path?)
        int status = com.sun.tools.javadoc.Main.execute(
                JavadocJUnitTestBridge.class.getSimpleName(),
                errWriter, warnWriter, noticeWriter,
                com.sun.tools.doclets.standard.Standard.class.getName(),
                args);
        String err = errWriter.toString(),
               warn = warnWriter.toString(),
               notice = noticeWriter.toString();
        // check status/contents of errWriter/warnWriter/noticeWriter
        if (err.length() > 0) {
            // try to identify the line number
            int lineNum = 1;
            Matcher m = errorLine.matcher(err);
            if (m.find())
                lineNum = Integer.valueOf(m.group(1));
            throw new DoctestFailure(err, klass, sourceFile, lineNum);
        }
    }
    private static final Pattern errorLine =
        Pattern.compile("^.*[.]java:(\\d+):",
                        Pattern.MULTILINE|Pattern.CASE_INSENSITIVE);
    private static class DoctestFailure extends Error {
        private static final long serialVersionUID = -7687329363469162375L;

        DoctestFailure(String msg, Class<?> klass,
                       File sourceFile, int lineNum) {
            super(msg);
            StackTraceElement[] stackTrace = new StackTraceElement[] {
                    new StackTraceElement(klass.getCanonicalName(), "<init>",
                                          sourceFile.getPath(), lineNum)
            };
            this.setStackTrace(stackTrace);
        }
    }
}
