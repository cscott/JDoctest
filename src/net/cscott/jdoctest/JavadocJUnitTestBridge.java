package net.cscott.jdoctest;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.tools.shell.Global;

/** JUnit test bridge to generate and run the doc tests from a specific
 *  Java source file. */
/* basic command to make things go:
 * javadoc -notree -noindex -nohelp -nonavbar -notimestamp -private -d /tmp/foo -quiet -Xdoclint:none -taglet net.cscott.jdoctest.JDoctest -tagletpath bin:lib/rhino-all-1.9.2.jar:lib/junit-platform-console-standalone-6.1.3.jar -J-ea -classpath bin:lib/rhino-all-1.9.2.jar:lib/junit-platform-console-standalone-6.1.3.jar src/net/cscott/jdoctest/JDoctest.java
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
        // check the reported errors
        StringBuilder err = new StringBuilder();
        long lineNum = -1;
        for (Diagnostic<? extends JavaFileObject> d : runJavadoc(klass, sourceFile)) {
            if (d.getKind() != Diagnostic.Kind.ERROR) continue;
            // try to identify the line number
            if (lineNum < 0 && d.getLineNumber() != Diagnostic.NOPOS)
                lineNum = d.getLineNumber();
            err.append(format(d));
        }
        if (err.length() > 0)
            throw failure(err.toString(), klass, sourceFile, lineNum);
    }
    /** Runs javadoc (and so the doctests) on the given source file, and
     *  returns the diagnostics it reported.  Javadoc gets the class path
     *  which the given class was loaded from.  A note is reported for each
     *  doctest which passes. */
    static List<Diagnostic<? extends JavaFileObject>> runJavadoc(Class<?> klass, File sourceFile) throws IOException {
        // Make a temp dir
        File tmpDir = TemporaryDirectory.createTempDirectory("jdoctest.");
        String verbose = System.setProperty(JDoctest.VERBOSE_PROPERTY, "true");
        try {
            return runJavadocWithTmpdir(klass, sourceFile, tmpDir);
        } finally {
            if (verbose == null)
                System.clearProperty(JDoctest.VERBOSE_PROPERTY);
            else
                System.setProperty(JDoctest.VERBOSE_PROPERTY, verbose);
            // Remove temporary output directory
            TemporaryDirectory.deleteAll(tmpDir);
        }
    }
    static List<Diagnostic<? extends JavaFileObject>> runJavadocWithTmpdir(Class<?> klass, File sourceFile, File tmpDir) throws IOException {
        // The taglet needs the class path too, so that doctests can use
        // the classes being documented.
        String classPath = classPath(klass);
        SPWriter outWriter = new SPWriter();
        DiagnosticCollector<JavaFileObject> diagnostics =
            new DiagnosticCollector<JavaFileObject>();
        List<String> args = Arrays.asList(
                "-private", "-d", tmpDir.getPath(),
                "-notree", "-noindex", "-nohelp", "-nonavbar", "-notimestamp",
                 "-quiet", "-Xdoclint:none",
                 "-taglet", JDoctest.class.getName(),
                 "-tagletpath", classPath,
                 "-classpath", classPath
                 );
        // provide annotation or some way to add additional arguments?
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
        return diagnostics.getDiagnostics();
    }
    /** Returns the class path which the given class was loaded from: the
     *  URLs of its class loaders, the <code>java.class.path</code> and the
     *  locations of JDoctest and Rhino, including the
     *  <code>Class-Path</code> of the manifest of each jar (since test
     *  runners often use a jar with a manifest to set the class path). */
    static String classPath(Class<?> klass) {
        Set<File> entries = new LinkedHashSet<File>();
        for (ClassLoader l = klass.getClassLoader(); l != null; l = l.getParent())
            if (l instanceof URLClassLoader)
                for (URL u : ((URLClassLoader) l).getURLs())
                    addClassPath(entries, u);
        for (String s : System.getProperty("java.class.path", "")
                 .split(File.pathSeparator))
            if (!s.isEmpty())
                addClassPath(entries, new File(s));
        for (Class<?> c : new Class<?>[] {
                klass, JDoctest.class, Context.class, Global.class }) {
            CodeSource cs = c.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null)
                addClassPath(entries, cs.getLocation());
        }
        return entries.stream().map(File::getPath)
            .collect(Collectors.joining(File.pathSeparator));
    }
    private static void addClassPath(Set<File> entries, URL url) {
        if (!"file".equals(url.getProtocol())) return;
        try {
            addClassPath(entries, new File(url.toURI()));
        } catch (URISyntaxException | IllegalArgumentException e) {
            // not a local file
        }
    }
    private static void addClassPath(Set<File> entries, File f) {
        f = f.getAbsoluteFile();
        if (!entries.add(f) || !f.isFile()) return;
        try (JarFile jar = new JarFile(f)) {
            Manifest m = jar.getManifest();
            String cp = (m == null) ? null :
                m.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            if (cp == null) return;
            for (String s : cp.trim().split("\\s+"))
                if (!s.isEmpty())
                    addClassPath(entries, new URL(f.toURI().toURL(), s));
        } catch (IOException e) {
            // not a jar file
        }
    }
    /** Formats a diagnostic as a line of javadoc output. */
    static String format(Diagnostic<? extends JavaFileObject> d) {
        StringBuilder sb = new StringBuilder();
        if (d.getSource() != null)
            sb.append(d.getSource().getName()).append(':')
              .append(d.getLineNumber()).append(": ");
        sb.append(d.getKind().toString().toLowerCase()).append(": ")
          .append(d.getMessage(null)).append('\n');
        return sb.toString();
    }
    /** Returns an error whose stack trace points at the given line of the
     *  given source file. */
    static Error failure(String msg, Class<?> klass, File sourceFile,
                         long lineNum) {
        return new DoctestFailure(msg, klass, sourceFile,
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
