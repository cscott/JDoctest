package net.cscott.jdoctest;


import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
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
public class JDocJUnitTest {
    /** Change this in a {@code @Before} method if your tests are in a different
     *  subdirectory. */
    public static String testDir = "api/tests";

    @Test
    public void runAllDoctests() {
        // find all the files underneath testDir
        List<File> tests = new ArrayList<File>();
        collectAllTests(new File(testDir), tests);
        List<String> failures = new ArrayList<String>();
        // Run each one in turn.
        Context cx = Context.enter();
        try {
            String test_text;
            for (File test: tests) {
                // read the doctest text
                try {
                    test_text = readFully(test);
                } catch (IOException e) {
                    failures.add("Can't read "+test);
                    continue;
                }
                boolean expect_fail = Patterns.expectFail(test_text);
                Global global = new Global(); // this is also a scope.
                global.init(cx);
                // okay, evaluate the doctest.
                // if the tests fail, we will throw an exception here.
                String fail=null;
                try {
                    @SuppressWarnings("unused")
                    int testsRun = global.runDoctest(cx, global, test_text,
                                                     test.getName(), 1);
                    // XXX: we don't handle EXPECT FAIL yet
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
                    failures.add(test+": "+fail);
            }
        } finally {
            Context.exit();
        }
        // okay, did we succeed?
        if (!failures.isEmpty())
            fail(failures.toString());
    }
    private static void collectAllTests(File testDir, List<File> results) {
        assert testDir.isDirectory();
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
