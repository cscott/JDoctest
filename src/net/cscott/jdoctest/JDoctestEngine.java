package net.cscott.jdoctest;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.discovery.ModuleSelector;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.discovery.EngineDiscoveryRequestResolver;
import org.junit.platform.engine.support.discovery.SelectorResolver;

/**
 * JUnit Platform test engine which runs the doctests of classes annotated
 * with {@link Doctests}.  It is registered as a service, so it is found
 * automatically whenever JDoctest is on the test classpath.  Its engine ID
 * is {@value #ENGINE_ID}.
 * <p>
 * Each annotated class becomes one test, which runs javadoc on the class's
 * source file and fails if any of its doctests fail.  The engine handles
 * class, package, classpath root and module selectors, so annotated classes
 * are found however the build tool or IDE asks for them.  Since the
 * annotation marks the classes to test explicitly, class name patterns
 * (such as the default "<code>*Test</code>" patterns of the console
 * launcher and <code>@Suite</code>) are ignored, but package name
 * filters are honored.
 */
public class JDoctestEngine implements TestEngine {
    /** The ID of this test engine. */
    public static final String ENGINE_ID = "jdoctest";
    /** JUnit configuration parameter giving the default source directory
     *  for {@link Doctests} classes which don't specify one. */
    public static final String SRC_ROOT_PARAMETER = "net.cscott.jdoctest.srcRoot";
    private static final String CLASS_SEGMENT = "class";

    @Override
    public String getId() {
        return ENGINE_ID;
    }

    @Override
    public Optional<String> getGroupId() {
        return Optional.of("net.cscott");
    }

    @Override
    public Optional<String> getVersion() {
        return Optional.of(Version.PACKAGE_VERSION);
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest request,
                                   UniqueId uniqueId) {
        EngineDescriptor engine = new EngineDescriptor(uniqueId, "JDoctest");
        String defaultSrcRoot = request.getConfigurationParameters()
            .get(SRC_ROOT_PARAMETER).orElse("src");
        EngineDiscoveryRequestResolver.<EngineDescriptor>builder()
            .addSelectorResolver(ctx -> new ClassResolver
                                 (defaultSrcRoot, ctx.getPackageFilter()))
            .build()
            .resolve(request, engine);
        return engine;
    }

    private static boolean hasDoctests(Class<?> klass) {
        return klass.isAnnotationPresent(Doctests.class);
    }

    /** Turns {@link Doctests} classes into test descriptors. */
    private static class ClassResolver implements SelectorResolver {
        private final String defaultSrcRoot;
        /** Filters class names by their package name. */
        private final Predicate<String> classNameFilter;
        ClassResolver(String defaultSrcRoot, Predicate<String> packageFilter) {
            this.defaultSrcRoot = defaultSrcRoot;
            this.classNameFilter = name -> packageFilter.test
                (name.lastIndexOf('.') < 0 ? "" :
                 name.substring(0, name.lastIndexOf('.')));
        }

        @Override
        public Resolution resolve(PackageSelector selector, Context context) {
            return classes(ReflectionSupport.findAllClassesInPackage
                (selector.getPackageName(), JDoctestEngine::hasDoctests,
                 classNameFilter));
        }

        @Override
        public Resolution resolve(ClasspathRootSelector selector,
                                  Context context) {
            return classes(ReflectionSupport.findAllClassesInClasspathRoot
                (selector.getClasspathRoot(), JDoctestEngine::hasDoctests,
                 classNameFilter));
        }

        @Override
        public Resolution resolve(ModuleSelector selector, Context context) {
            return classes(ReflectionSupport.findAllClassesInModule
                (selector.getModuleName(), JDoctestEngine::hasDoctests,
                 classNameFilter));
        }

        private static Resolution classes(List<Class<?>> classes) {
            return Resolution.selectors(classes.stream()
                .map(DiscoverySelectors::selectClass)
                .collect(Collectors.toSet()));
        }

        @Override
        public Resolution resolve(ClassSelector selector, Context context) {
            Class<?> klass = selector.getJavaClass();
            if (!hasDoctests(klass))
                return Resolution.unresolved();
            return context.addToParent(parent -> Optional.of(
                new ClassDescriptor(parent.getUniqueId(), klass,
                                    defaultSrcRoot)))
                .map(d -> Resolution.match(Match.exact(d)))
                .orElse(Resolution.unresolved());
        }

        @Override
        public Resolution resolve(UniqueIdSelector selector, Context context) {
            UniqueId.Segment last = selector.getUniqueId().getLastSegment();
            if (!CLASS_SEGMENT.equals(last.getType()))
                return Resolution.unresolved();
            return Resolution.selectors(Set.of(
                DiscoverySelectors.selectClass(last.getValue())));
        }
    }

    /** The doctests of a single class. */
    static class ClassDescriptor extends AbstractTestDescriptor {
        final Class<?> klass;
        final File sourceFile;
        ClassDescriptor(UniqueId parentId, Class<?> klass,
                        String defaultSrcRoot) {
            super(parentId.append(CLASS_SEGMENT, klass.getName()),
                  klass.getName(), ClassSource.from(klass));
            this.klass = klass;
            String srcRoot = klass.getAnnotation(Doctests.class).srcRoot();
            this.sourceFile = sourceFile
                (klass, srcRoot.isEmpty() ? defaultSrcRoot : srcRoot);
        }
        @Override
        public Type getType() {
            return Type.TEST;
        }
    }

    /** Returns the source file for the given class, found beneath the
     *  given source directory. */
    static File sourceFile(Class<?> klass, String srcRoot) {
        Class<?> base = klass;
        while (base.getEnclosingClass() != null)
            base = base.getEnclosingClass();
        File srcPath = new File(srcRoot);
        String pkg = base.getPackageName();
        if (!pkg.isEmpty())
            for (String d : pkg.split("[.]"))
                srcPath = new File(srcPath, d);
        return new File(srcPath, base.getSimpleName()+".java");
    }

    @Override
    public void execute(ExecutionRequest request) {
        TestDescriptor engine = request.getRootTestDescriptor();
        EngineExecutionListener listener = request.getEngineExecutionListener();
        listener.executionStarted(engine);
        for (TestDescriptor child : engine.getChildren())
            execute((ClassDescriptor) child, listener);
        listener.executionFinished(engine, TestExecutionResult.successful());
    }

    private void execute(ClassDescriptor test,
                         EngineExecutionListener listener) {
        listener.executionStarted(test);
        TestExecutionResult result;
        try {
            if (!test.sourceFile.isFile())
                throw new AssertionError("Can't find source for "+test.klass+
                                         " at "+test.sourceFile);
            JavadocJUnitTestBridge.runDoctest(test.klass, test.sourceFile);
            result = TestExecutionResult.successful();
        } catch (Throwable t) {
            result = TestExecutionResult.failed(t);
        }
        listener.executionFinished(test, result);
    }
}
