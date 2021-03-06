package net.cscott.jdoctest;

import static org.junit.Assert.fail;

import java.io.File;
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
 * Add a {@code @SrcRoot(value="foo/bar")} if the source files for your class
 * live someplace other than "src".
 */
public class JDoctestRunner extends Suite {
        private final String name;
	/**
	 * Annotation for a method which provides parameters to be injected into the
	 * test class constructor by <code>Parameterized</code>
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public static @interface SrcRoot {
	    public String value();
	}

	private class JDoctestRunnerForFile extends
			BlockJUnit4ClassRunner {
	        private final Class<?> parentType;
	        private final File testFile;
		JDoctestRunnerForFile(Class<?> type, File testFile) throws InitializationError {
			super(JavadocJUnitTestBridge.class);
			this.parentType = type;
			this.testFile = testFile;
		}

		@Override
		public Object createTest() throws Exception {
		    return new JavadocJUnitTestBridge(parentType, testFile);
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
	public JDoctestRunner(Class<?> klass) throws Throwable {
		super(null, Collections.<Runner>emptyList());
		this.name = klass.getName();
		String srcRoot = getSrcRoot(klass);
		// not find filename of source file for this class
		Class<?> base = klass;
                while (base.getEnclosingClass() != null)
                    base = base.getEnclosingClass();
		String pkg = base.getPackage().getName();
		File srcPath = new File(srcRoot);
		for (String d : pkg.split("[.]"))
		    srcPath = new File(srcPath, d);
		srcPath = new File(srcPath, base.getSimpleName()+".java");
		if (!srcPath.isFile())
		    fail("Can't find source for "+klass+" at "+srcPath);
		runners.add(new JDoctestRunnerForFile(klass, srcPath));
	}
	@Override
	protected String getName() {
	        return this.name;
	}

	@Override
	protected List<Runner> getChildren() {
		return runners;
	}

        private String getSrcRoot(Class<?> klass) throws InitializationError {
            SrcRoot annotation= klass.getAnnotation(SrcRoot.class);
            if (annotation == null)
                // default value
                return defaultSrcRoot();
            return annotation.value();
        }

	/** Override this method to change the default path to your sources. */
	protected String defaultSrcRoot() {
		return "src";
	}
}
