package net.cscott.jdoctest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the javadoc of the annotated class contains doctests
 * which should be run as JUnit tests.  When JDoctest is on the test
 * classpath, the JUnit Platform finds its test engine, which runs javadoc
 * on the source file of each annotated class it is asked to test.
 * <p>
 * This annotation has no runtime dependencies; classes using it do not
 * need JUnit on their classpath.
 *
 * @see JDoctestEngine
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Doctests {
    /**
     * The directory containing the source file of the annotated class.
     * If empty (the default), the {@value JDoctestEngine#SRC_ROOT_PARAMETER}
     * JUnit configuration parameter is used, or else "src".
     */
    String srcRoot() default "";
}
