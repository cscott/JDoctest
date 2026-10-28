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

* `@RunWith(JDoctestRunner.class)` is gone.  Instead, write a test class
  whose `@TestFactory` method returns `JDoctestRunner.doctests(MyClass.class)`;
  see the README.  The `@SrcRoot` annotation still works when placed on the
  tested class, and `JDoctestRunner.doctests(MyClass.class, "src/dir")`
  replaces overriding `defaultSrcRoot()`.
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

[Rhino]: https://github.com/mozilla/rhino
[JUnit]: https://junit.org/
