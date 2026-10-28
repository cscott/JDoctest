package net.cscott.jdoctest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

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
import org.junit.platform.engine.support.descriptor.FilePosition;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.discovery.EngineDiscoveryRequestResolver;
import org.junit.platform.engine.support.discovery.SelectorResolver;
import org.opentest4j.TestAbortedException;

/**
 * JUnit Platform test engine which runs the doctests of classes annotated
 * with {@link Doctests}.  It is registered as a service, so it is found
 * automatically whenever JDoctest is on the test classpath.  Its engine ID
 * is {@value #ENGINE_ID}.
 * <p>
 * Each annotated class becomes a container, with one test for each of the
 * <code>@doc.test</code> tags in its documentation and the documentation of
 * its members (and of any nested classes which are not annotated
 * themselves).  The tests of a class are run together, by running javadoc
 * on the class's source file; a doctest marked <code>(EXPECT FAIL)</code>
 * which fails is reported as aborted.  The engine handles
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
    private static final String DOCTEST_SEGMENT = "doctest";

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
            return resolveClass(selector.getJavaClass(), context);
        }

        private Resolution resolveClass(Class<?> klass, Context context) {
            if (!hasDoctests(klass))
                return Resolution.unresolved();
            return context.addToParent(parent -> Optional.of(
                new ClassDescriptor(parent.getUniqueId(), klass,
                                    defaultSrcRoot)))
                .map(d -> Resolution.match(Match.exact(
                    d, ((ClassDescriptor) d)::doctestSelectors)))
                .orElse(Resolution.unresolved());
        }

        @Override
        public Resolution resolve(UniqueIdSelector selector, Context context) {
            UniqueId id = selector.getUniqueId();
            UniqueId.Segment last = id.getLastSegment();
            if (CLASS_SEGMENT.equals(last.getType()))
                return ReflectionSupport.tryToLoadClass(last.getValue())
                    .toOptional()
                    .map(klass -> resolveClass(klass, context))
                    .orElse(Resolution.unresolved());
            if (!DOCTEST_SEGMENT.equals(last.getType()))
                return Resolution.unresolved();
            // resolving the parent this way doesn't select its other tests
            return context.addToParent(
                () -> DiscoverySelectors.selectUniqueId(id.removeLastSegment()),
                parent -> (parent instanceof ClassDescriptor) ?
                    ((ClassDescriptor) parent).doctest(last.getValue()) :
                    Optional.empty())
                .map(d -> Resolution.match(Match.exact(d)))
                .orElse(Resolution.unresolved());
        }
    }

    /** The doctests of a single class. */
    static class ClassDescriptor extends AbstractTestDescriptor {
        final Class<?> klass;
        final File sourceFile;
        /** The tags in the source file, or null if it couldn't be read. */
        final DoctestTags tags;
        /** Why the source file couldn't be read, or null. */
        final Throwable error;
        ClassDescriptor(UniqueId parentId, Class<?> klass,
                        String defaultSrcRoot) {
            super(parentId.append(CLASS_SEGMENT, klass.getName()),
                  klass.getName(), ClassSource.from(klass));
            this.klass = klass;
            String srcRoot = klass.getAnnotation(Doctests.class).srcRoot();
            this.sourceFile = sourceFile
                (klass, srcRoot.isEmpty() ? defaultSrcRoot : srcRoot);
            DoctestTags tags = null;
            Throwable error = null;
            if (!sourceFile.isFile())
                error = new AssertionError("Can't find source for "+klass+
                                           " at "+sourceFile);
            else try {
                tags = DoctestTags.parse(sourceFile, klass.getClassLoader());
            } catch (IOException | RuntimeException e) {
                error = e;
            }
            this.tags = tags;
            this.error = error;
        }
        @Override
        public Type getType() {
            // if we couldn't find the doctests, report that as a failing
            // test, instead of as an (unreported) empty container
            return (error != null) ? Type.TEST : Type.CONTAINER;
        }
        /** Returns the tags which belong to this class. */
        private Stream<DoctestTags.Tag> ownTags() {
            return (tags == null) ? Stream.empty() :
                tags.tags.stream().filter(t -> t.owner == klass);
        }
        /** Selects all the doctests of this class. */
        Set<UniqueIdSelector> doctestSelectors() {
            return ownTags()
                .map(t -> DiscoverySelectors.selectUniqueId
                     (getUniqueId().append(DOCTEST_SEGMENT, t.name)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        /** Creates the descriptor for the named doctest of this class. */
        Optional<DoctestDescriptor> doctest(String name) {
            return ownTags().filter(t -> t.name.equals(name)).findFirst()
                .map(t -> new DoctestDescriptor(this, t));
        }
    }

    /** A single <code>@doc.test</code> tag. */
    static class DoctestDescriptor extends AbstractTestDescriptor {
        final int line;
        DoctestDescriptor(ClassDescriptor parent, DoctestTags.Tag tag) {
            super(parent.getUniqueId().append(DOCTEST_SEGMENT, tag.name),
                  tag.name, FileSource.from(parent.sourceFile,
                                            FilePosition.from(tag.line)));
            this.line = tag.line;
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
        // nested classes share a source file; only run javadoc on it once
        Map<File, List<Diagnostic<? extends JavaFileObject>>> javadocRuns =
            new HashMap<>();
        for (TestDescriptor child : engine.getChildren())
            execute((ClassDescriptor) child, listener, javadocRuns);
        listener.executionFinished(engine, TestExecutionResult.successful());
    }

    private void execute(ClassDescriptor test,
                         EngineExecutionListener listener,
                         Map<File, List<Diagnostic<? extends JavaFileObject>>>
                         javadocRuns) {
        listener.executionStarted(test);
        if (test.error != null) {
            listener.executionFinished(test,
                                       TestExecutionResult.failed(test.error));
            return;
        }
        List<DoctestDescriptor> doctests = new ArrayList<>();
        for (TestDescriptor child : test.getChildren())
            doctests.add((DoctestDescriptor) child);
        doctests.sort(Comparator.comparingInt(d -> d.line));
        TestExecutionResult result = TestExecutionResult.successful();
        if (!doctests.isEmpty()) try {
            List<Diagnostic<? extends JavaFileObject>> diagnostics =
                javadocRuns.get(test.sourceFile);
            if (diagnostics == null) {
                diagnostics = JavadocJUnitTestBridge.runJavadoc
                    (test.klass, test.sourceFile);
                javadocRuns.put(test.sourceFile, diagnostics);
            }
            // errors which aren't from any doctest fail the whole class
            // (and may have kept the doctests from running)
            Set<Long> tagLines = test.tags.tags.stream()
                .map(t -> (long) t.line).collect(Collectors.toSet());
            StringBuilder err = new StringBuilder();
            long errLine = -1;
            for (Diagnostic<? extends JavaFileObject> d : diagnostics) {
                if (d.getKind() != Diagnostic.Kind.ERROR ||
                    tagLines.contains(d.getLineNumber()))
                    continue;
                if (errLine < 0 && d.getLineNumber() != Diagnostic.NOPOS)
                    errLine = d.getLineNumber();
                err.append(JavadocJUnitTestBridge.format(d));
            }
            // the taglet reports an error, warning (for an expected
            // failure) or note (for a pass) for each doctest it runs
            Map<Long, Diagnostic<? extends JavaFileObject>> byLine =
                new HashMap<>();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics)
                if (d.getKind() == Diagnostic.Kind.ERROR ||
                    d.getKind() == Diagnostic.Kind.WARNING ||
                    d.getKind() == Diagnostic.Kind.NOTE)
                    byLine.merge(d.getLineNumber(), d, (a, b) ->
                                 (rank(b) > rank(a)) ? b : a);
            for (DoctestDescriptor doctest : doctests) {
                listener.executionStarted(doctest);
                listener.executionFinished(doctest, result(
                    test, doctest, byLine.get((long) doctest.line), err));
            }
            if (err.length() > 0)
                result = TestExecutionResult.failed
                    (JavadocJUnitTestBridge.failure(err.toString(), test.klass,
                                                    test.sourceFile, errLine));
        } catch (Throwable t) {
            result = TestExecutionResult.failed(t);
        }
        listener.executionFinished(test, result);
    }

    private static int rank(Diagnostic<?> d) {
        switch (d.getKind()) {
        case ERROR: return 2;
        case WARNING: return 1;
        default: return 0;
        }
    }

    /** Returns the result of a doctest, given the most severe diagnostic
     *  which javadoc reported for it (if any) and the errors javadoc
     *  reported for the class as a whole. */
    private static TestExecutionResult result
        (ClassDescriptor test, DoctestDescriptor doctest,
         Diagnostic<? extends JavaFileObject> d, CharSequence classErrors) {
        if (d == null)
            return TestExecutionResult.failed(JavadocJUnitTestBridge.failure
                ("Doctest was not run" + (classErrors.length() == 0 ? "" :
                                          ":\n" + classErrors),
                 test.klass, test.sourceFile, doctest.line));
        String msg = d.getMessage(null);
        switch (d.getKind()) {
        case NOTE:
            return TestExecutionResult.successful();
        case WARNING:
            return TestExecutionResult.aborted(new TestAbortedException(msg));
        default:
            return TestExecutionResult.failed(JavadocJUnitTestBridge.failure
                (msg, test.klass, test.sourceFile, doctest.line));
        }
    }
}
