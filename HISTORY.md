# JDoctest history

## 2.0

JDoctest 2.0 is a port to modern JDKs.  Upgraders from 1.x should be
aware of the following changes.

### Requirements

* JDK 17 or later is required.  JDoctest is now a
  `jdk.javadoc.doclet.Taglet`; the `com.sun.javadoc` taglet API which
  1.x used was removed in JDK 13.  `tools.jar` is no longer needed.
* The JavaScript engine is now [Rhino] 1.9.2, shipped as
  `lib/rhino-all-1.9.2.jar`.  Put it on your `-tagletpath` in place of
  the old `js.jar`.
* Doctests which access private members of your classes need
  `-J--add-opens=java.base/java.lang=ALL-UNNAMED` on the javadoc command
  line (or `--add-opens=java.base/java.lang=ALL-UNNAMED` for the JVM
  running your JUnit tests).  Without it, doctests only see public
  members.

### JavaScript language version

Doctests are now evaluated as the most recent version of ECMAScript
which Rhino supports (`Context.VERSION_ECMASCRIPT`), instead of
JavaScript 1.8.  This means:

* Modern syntax works: `let` and `const`, arrow functions, template
  literals, default parameters, spread, destructuring, `function*`
  generators, `**`, `??` and `?.`.  Classes and `async` functions are not
  yet supported by Rhino.
* ES6 built-ins such as `Symbol`, `Map`, `Set` and `Promise` are
  available, and some existing built-ins follow ES6 rules: for example,
  `Object.keys("ab")` now returns `["0", "1"]` instead of throwing a
  `TypeError`.
* Legacy JavaScript 1.7 generators (a plain `function` containing
  `yield`) are no longer generators.  Declare them with `function*`
  instead.
* Since the language version tracks Rhino, future Rhino upgrades may
  change it again.

The Java exception message format changed in recent JDKs too, so
doctests which print exception messages from the JDK may need updating.

### JUnit

JUnit support now uses [JUnit] 6 (Jupiter) instead of JUnit 4.
`lib/junit-platform-console-standalone-6.1.3.jar` bundles everything
needed to compile and run the tests.

* `@RunWith(JDoctestRunner.class)` is replaced by the `@Doctests`
  annotation, and `JDoctestRunner` has been removed.  JDoctest provides a
  JUnit Platform test engine, which JUnit finds automatically, and which
  runs the doctests of each `@Doctests` class that JUnit is asked to test;
  see the README.  `@Doctests` has no JUnit dependency, so (unlike
  `@RunWith`) it doesn't put JUnit on the classpath of your production
  code.
* `@SrcRoot("dir")` is replaced by `@Doctests(srcRoot="dir")`.  Instead of
  overriding `JDoctestRunner.defaultSrcRoot()`, set the
  `net.cscott.jdoctest.srcRoot` JUnit configuration parameter (for
  example, in `junit-platform.properties`).
* `RerunJDoctests` is now a `@TestFactory` instead of a `Parameterized`
  test.  Subclasses which changed the test directory should override
  `testDir()` instead of `listTests()`.  `listTests(String)` now returns a
  `List<File>`.
* `JsJUnitTestBridge.runDoctest()` and `JavadocJUnitTestBridge.runDoctest()`
  are no longer `@Test` methods.  Failures are reported as
  `org.opentest4j.AssertionFailedError`.
* In ant, use the `<junitlauncher>` task instead of `<junit>`; the README
  shows how.

### Other changes

* `JDoctest.register(Map)` and the `inField()`, `inMethod()` (etc)
  methods of the old taglet API were removed.
* Indentation is stripped consistently across JDK versions, and doctests
  in `package.html` files have their indentation stripped too.
* Fixed a `NullPointerException` emitting standalone tests
  (`-J-Dnet.cscott.jdoctest.output=...`) for classes in the unnamed
  package.

## 1.7 (2026-10-28)

The last release of the 1.x line, which runs on JDK 1.5 through 12.

* Doctests can access private and protected Java members.
* A failing doctest shows a word-by-word diff of the expected and actual
  output, using Neil Fraser's diff-match-patch.
* Fixed whitespace stripping when the second line of a doctest is a
  continuation (`>`) line.
* Updated Rhino to 1.7R5pre.

## 1.6 (2014-03-31)

* JavaScript 1.7 is the default language version.
* Fixed the default package import for packages with non-standard names
  (such as all-caps `EDU.` prefixes) by importing via `Packages.`.
* Failing doctests are reliably reported: works with JDK 1.7's new
  location for `DocErrorReporter`, and exits with an error code if no
  reporter can be found.
* `build.xml` uses the `java.home` property to find `tools.jar`.
* Travis CI integration; the README was converted to Markdown.

## 1.5 (2009-08-31)

* JUnit support: `RerunJDoctests` reruns the doctests emitted by
  `net.cscott.jdoctest.output` as a parameterized JUnit test, and
  annotating a class with `@RunWith(JDoctestRunner.class)` runs javadoc on
  its source to execute its doctests as a JUnit test.
* Fixed invoking javadoc several times in one process.
* JDK 1.5 or later is required.
* Eclipse project metadata.

## 1.4 (2009-03-09)

* `(EXPECT FAIL)` doctests report a warning instead of an error, for a
  test-first style of development.
* Setting the `net.cscott.jdoctest.output` property writes each doctest
  out to a standalone `.js` file, named after its source position.
* Clearer expected/actual output in failure messages.
* Assertion errors no longer escape from a failing doctest.
* Simpler google-code-prettify hookup, using `{@docRoot}`.

## 1.3 (2009-02-10)

* No "Tests" section is generated for elements without `@doc.test`
  tags.

## 1.2 (2009-02-07)

* `@doc.test` tags work in package documentation.
* Optional syntax highlighting of doctests with google-code-prettify.
* A doctest whose first line starts with `js>` has no description.

## 1.1 (2009-02-05)

* Proper error messages when used with the standard doclet.
* Documented the javadoc `-J-ea` option, to enable assertions.

## 1.0 (2009-02-04)

* Initial release: a javadoc taglet which runs `@doc.test` doctests
  using Rhino 1.7R2.

[Rhino]: https://github.com/mozilla/rhino
[JUnit]: https://junit.org/
