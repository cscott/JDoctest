package net.cscott.jdoctest;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This is a JUnit test which re-runs all of the doctests emitted by JDoctest.
 * Set the {@code net.cscott.jdoctest.output} property when you emit your
 * javadoc, and stand-alone versions of your tests will be emitted into the
 * specified directory.  Subclass this test class and (if your tests are not
 * in "api/tests") override the listTests() class to invoke
 * {@link #listTests(String)} with the directory you set
 * {@code net.cscott.jdoctest.output} to. (Don't forget to make the
 * whole thing {@code @RunWith(value=Parameterized.class)} and tag your
 * overridden {@link #listTests()} with the {@code Parameters} annotation.)
 * @author C. Scott Ananian
 */
@RunWith(value = Parameterized.class)
public class RerunJDoctests extends JsJUnitTestBridge {
    public RerunJDoctests(String testFile) {
        super(new File(testFile));
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
}
