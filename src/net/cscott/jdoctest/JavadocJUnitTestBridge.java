package net.cscott.jdoctest;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.Test;

/** JUnit test bridge to generate and run the doc tests from a specific
 *  Java source file. */
/* basic command to make things go:
 * javadoc -notree -noindex -nohelp -nonavbar -notimestamp -private -d /tmp/foo -quiet -Xdoclint:none -taglet net.cscott.jdoctest.JDoctest -tagletpath jdoctest-1.6.jar:lib/rhino1_7R5pre/js.jar:bin -J-ea -classpath bin:lib/rhino1_7R5pre/js.jar:lib/junit-4.6.jar src/net/cscott/jdoctest/JDoctest.java
 */
public class JavadocJUnitTestBridge {
    public final Class<?> klass;
    public final File sourceFile;
    public JavadocJUnitTestBridge(Class<?> klass, File sourceFile) {
        this.klass = klass;
        this.sourceFile = sourceFile;
    }
    /* stand-alone! (for testing) */
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
    static void runDoctestWithTmpdir(Class<?> klass, File sourceFile, File tmpDir) throws IOException {
        SPWriter outWriter = new SPWriter();
        DiagnosticCollector<JavaFileObject> diagnostics =
            new DiagnosticCollector<JavaFileObject>();
        List<String> args = Arrays.asList(
                "-private", "-d", tmpDir.getPath(),
                "-notree", "-noindex", "-nohelp", "-nonavbar", "-notimestamp",
                 "-quiet", "-Xdoclint:none",
                 "-taglet", JDoctest.class.getName()
                 //"-tagletpath", tagletPath, "-J-ea", //XXX necessary?
                 //"-classpath", classPath, // XXX necessary?
                 );
        // provide annotation or some way to add additional arguments
        // or class path entries? (maybe for taglet path?)
        DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
        if (tool == null)
            throw new IllegalStateException
                ("No javadoc tool available; is this a full JDK?");
        StandardJavaFileManager fm =
            tool.getStandardFileManager(diagnostics, null, null);
        try {
            DocumentationTool.DocumentationTask task = tool.getTask(
                outWriter, fm, diagnostics, null /* standard doclet */,
                args, fm.getJavaFileObjects(sourceFile));
            task.call();
        } finally {
            fm.close();
        }
        // check the reported errors
        StringBuilder err = new StringBuilder();
        long lineNum = -1;
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) continue;
            // try to identify the line number
            if (lineNum < 0 && d.getLineNumber() != Diagnostic.NOPOS)
                lineNum = d.getLineNumber();
            if (d.getSource() != null)
                err.append(d.getSource().getName()).append(':')
                   .append(d.getLineNumber()).append(": ");
            err.append("error: ").append(d.getMessage(null)).append('\n');
        }
        if (err.length() > 0)
            throw new DoctestFailure(err.toString(), klass, sourceFile,
                                     (int) Math.max(1, lineNum));
    }
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
