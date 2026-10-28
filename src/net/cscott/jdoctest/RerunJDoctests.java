package net.cscott.jdoctest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * This is a JUnit test which re-runs all of the doctests emitted by JDoctest.
 * Set the {@code net.cscott.jdoctest.output} property when you emit your
 * javadoc, and stand-alone versions of your tests will be emitted into the
 * specified directory.  If your tests are not in "api/tests", subclass this
 * test class and override {@link #testDir()} to return the directory you set
 * {@code net.cscott.jdoctest.output} to.
 * @author C. Scott Ananian
 */
public class RerunJDoctests {
    /** Override this in your subclass to change the test directory. */
    protected String testDir() {
        return "api/tests";
    }

    @TestFactory
    public Stream<DynamicTest> rerunDoctests() {
        return doctests(testDir());
    }

    /** Returns a test for each javascript test found beneath the given
     *  directory. */
    public static Stream<DynamicTest> doctests(String testDir) {
        return listTests(testDir).stream().map(f -> DynamicTest.dynamicTest(
                f.getPath(), f.toURI(), () -> JsJUnitTestBridge.runDoctest(f)));
    }

    /** List all the javascript tests found beneath the given directory. */
    public static List<File> listTests(String testDir) {
        // find all the files underneath testDir
        List<File> tests = new ArrayList<File>();
        JsJUnitTestBridge.collectAllTests(new File(testDir), tests);
        return tests;
    }
}
