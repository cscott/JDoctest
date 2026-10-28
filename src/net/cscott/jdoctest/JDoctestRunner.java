package net.cscott.jdoctest;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;

/**
 * The <code>JDoctestRunner</code> runs doctests generated from a single given
 * class, by invoking javadoc on its source file.  Return its
 * {@link #doctests(Class)} from a JUnit {@code @TestFactory} method:
 * <pre>
 *   class MyClassDoctests {
 *       &#64;TestFactory
 *       Stream&lt;DynamicTest&gt; doctests() {
 *           return JDoctestRunner.doctests(MyClass.class);
 *       }
 *   }
 * </pre>
 * Add a {@code @SrcRoot(value="foo/bar")} to the tested class if its
 * source files live someplace other than "src".
 */
public class JDoctestRunner {
	private JDoctestRunner() { }

	/**
	 * Annotation for a class whose source files live someplace other
	 * than "src".
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public static @interface SrcRoot {
	    public String value();
	}

	/**
	 * Returns a test which runs the doctests in the source file of the
	 * given class.  The source file is found beneath the directory named
	 * by the class's {@link SrcRoot} annotation, or "src" if it has none.
	 */
	public static Stream<DynamicTest> doctests(Class<?> klass) {
		SrcRoot annotation = klass.getAnnotation(SrcRoot.class);
		return doctests(klass,
				(annotation == null) ? "src" : annotation.value());
	}

	/**
	 * Returns a test which runs the doctests in the source file of the
	 * given class, found beneath the given source directory.
	 */
	public static Stream<DynamicTest> doctests(Class<?> klass, String srcRoot) {
		// find filename of source file for this class
		Class<?> base = klass;
                while (base.getEnclosingClass() != null)
                    base = base.getEnclosingClass();
		File srcPath = new File(srcRoot);
		String pkg = base.getPackageName();
		if (!pkg.isEmpty())
		    for (String d : pkg.split("[.]"))
			srcPath = new File(srcPath, d);
		srcPath = new File(srcPath, base.getSimpleName()+".java");
		if (!srcPath.isFile())
		    fail("Can't find source for "+klass+" at "+srcPath);
		final File sourceFile = srcPath;
		return Stream.of(DynamicTest.dynamicTest(
			String.format("%s[%s]", klass.getName(), sourceFile.getPath()),
			sourceFile.toURI(),
			() -> JavadocJUnitTestBridge.runDoctest(klass, sourceFile)));
	}
}
