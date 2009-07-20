package net.cscott.jdoctest;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

/**
 * The <code>JDoctestRunner</code> runs doctests generated from a single given
 * class.  Just annotate the class {@code @RunWith(value=JDoctestRunner.class)}.
 * Add a {@code @DoctestRoot(value="foo/bar")} if you are putting the
 * doctest-processed output someplace other than "api/tests".
 */
public class JDoctestRunner extends Suite {
	/**
	 * Annotation for a method which provides parameters to be injected into the
	 * test class constructor by <code>Parameterized</code>
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public static @interface DoctestRoot {
	    public String value();
	}

	private class JDoctestRunnerForFile extends
			BlockJUnit4ClassRunner {
	        private final Class<?> parentType;
	        private final File testFile;
		JDoctestRunnerForFile(Class<?> type, File testFile) throws InitializationError {
			super(JUnitTestBridge.class);
			this.parentType = type;
			this.testFile = testFile;
		}

		@Override
		public Object createTest() throws Exception {
		    return new JUnitTestBridge(testFile);
		}

		@Override
		protected String getName() {
		    return String.format("%s[%s]", parentType.getName(), testFile.getName());
		}

		@Override
		protected String testName(final FrameworkMethod method) {
			return String.format("%s[%s]", method.getName(), testFile.getPath());
		}

		@Override
		protected void validateZeroArgConstructor(List<Throwable> errors) {
			// constructor can, nay, should have args.
		}

		@Override
		protected Statement classBlock(RunNotifier notifier) {
			return childrenInvoker(notifier);
		}
	}

	private final ArrayList<Runner> runners= new ArrayList<Runner>();

	/**
	 * Only called reflectively. Do not use programmatically.
	 */
	public JDoctestRunner(final Class<?> klass) throws Throwable {
		super(klass, Collections.<Runner>emptyList());
		String doctestRoot = getDoctestRoot(klass);
		String dir = klass.getPackage().getName();
		File testDir = new File(doctestRoot, dir);
		if (!testDir.isDirectory())
		    fail("No tests found in "+testDir);
		FilenameFilter filter = new FilenameFilter() {
	            public boolean accept(File dir, String name) {
	                return name.startsWith("test-"+klass.getSimpleName()+"-");
	            }};
		for (File f: testDir.listFiles(filter)) {
		    runners.add(new JDoctestRunnerForFile(klass, f));
		}
		if (runners.isEmpty())
		    fail("No tests found for "+klass.getSimpleName()+" in "+testDir);
	}

	@Override
	protected List<Runner> getChildren() {
		return runners;
	}

        private static String getDoctestRoot(Class<?> klass) throws InitializationError {
            DoctestRoot annotation= klass.getAnnotation(DoctestRoot.class);
            if (annotation == null)
                // default value
                return "api/tests";
            return annotation.value();
        }
}
