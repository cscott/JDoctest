package net.cscott.jdoctest;


import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.tools.shell.Global;

/**
 * This is a JUnit test which runs all of the doctests emitted by JDoctest.
 * Set the {@code net.cscott.jdoctest.output} property when you emit your
 * javadoc, and stand-alone versions of your tests will be emitted into the
 * specified directory.  Subclass this test class and add an method tagged with
 * {@code org.junit.Before} which sets the {@code testDir} field to the
 * directory you set {@code net.cscott.jdoctest.output} to, and JUnit will
 * run all your doctests.
 * @author C. Scott Ananian
 */
@RunWith(value = Parameterized.class)
public class JDocJUnitTest {
    private final String testFile;
    public JDocJUnitTest(String testFile) {
        this.testFile = testFile;
    }

    /** Reimplement this in your subclass to change the test directory. */
    @Parameters
    public static Collection<Object[]> listTests() {
        return listTests("api/tests");
    }
    /** List all the javascript tests found beneath the given directory. */
    public static Collection<Object[]> listTests(String testDir) {
        // find all the files underneath testDir
        List<File> tests = new ArrayList<File>();
        collectAllTests(new File(testDir), tests);
        List<Object[]> result = new ArrayList<Object[]>();
        for (File f : tests)
            result.add(new Object[] { f.getPath() });
        return result;
    }

    @Test
    public void runDoctest() {
        runDoctest(this.testFile);
    }
    // ------ Helper functions for easy re-use. --------
    public static void runDoctest(String testFile) {
        runDoctest(new File(testFile));
    }
    public static void runDoctest(File testFile) {
        String testText;
        try {
            testText = readFully(testFile);
        } catch (IOException e) {
            fail("Can't read "+testFile);
            return;
        }
        runDoctest(testFile.getPath(), testText);
    }
    public static void runDoctest(String testSource, String testText) {
        // Run each one in turn.
        Context cx = Context.enter();
        try {
            boolean expect_fail = Patterns.expectFail(testText);
            Global global = new Global(); // this is also a scope.
            global.init(cx);
            // okay, evaluate the doctest.
            // if the tests fail, we will throw an exception here.
            String fail=null;
            try {
                @SuppressWarnings("unused")
                int testsRun = global.runDoctest(cx, global, testText,
                                                 testSource, 1);
            } catch (AssertionError e) {
                fail = e.getMessage();
                if (fail==null) fail="<unknown assertion failure>";
            } catch (RhinoException e) {
                fail = e.getMessage();
                if (fail==null) fail="<unknown failure>";
            }
            if (expect_fail) {
                fail = (fail!=null) ? null :
                    "Expected to fail, but did not.";
            }
            if (fail!=null)
                fail(testSource+": "+fail);
        } finally {
            Context.exit();
        }
    }
    private static void collectAllTests(File testDir, List<File> results) {
        if (!testDir.isDirectory())
            fail("JDoctest test directory "+testDir+" does not exist");
        for (File f : testDir.listFiles()) {
            if (f.isDirectory())
                collectAllTests(f, results);
            else if (f.getName().endsWith(".js"))
                results.add(f);
        }
    }
    private static String readFully(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        Reader r = new InputStreamReader(new FileInputStream(f), "utf-8");
        while (true) {
            int chars = r.read(buf);
            if (chars<0) break;
            sb.append(buf, 0, chars);
        }
        return sb.toString();
    }
}
