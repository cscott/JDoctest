# JDoctest

JDoctest is an implementation of Python's [doctest] for Java, based on the
ideas in [`doctestj`] and [Rhino's doctests].

Unlike doctestj, JDoctest is a javadoc [`Taglet`], rather than a [`Doclet`].
It doesn't replace the standard javadoc, it just adds a new `@doc.test`
tag.  We also use a multiline format similar to Python's doctest
(based on Rhino's implementation) rather than insist on a single
evaluated expression, as doctestj does.

Doctest blocks are written in Javascript, using the most recent version of
ECMAScript that [Rhino] supports.  [Calling from Javascript into Java]
is straightforward. An example from the top of the
`net.cscott.jdoctest.JDoctest` class:

```
/**
 * A "@doc.test" tag specifies an interactive javascript
 * session, in an environment where the class' package has been imported.
 * The output of the javascript session should match the output
 * provided.
 *
 * @author C. Scott Ananian
 * @doc.test
 *   This is an example of a test which passes:
 *   js> "a".equals("a")
 *   true
 * @doc.test
 *   This is an example of a test which fails:
 *   js> 1+2
 *   5
 * @doc.test
 *   We can write tests which expect exceptions to be thrown, although
 *   it's a little clunky (implementing doctest.ELLIPSIS or
 *   doctest.IGNORE_EXCEPTION_DETAIL would make this nicer):
 *   js> try {
 *     >   java.lang.String("hi").charAt(3);
 *     > } catch (e) {
 *     >   print(e.javaException.getClass().getName())
 *     > }
 *   java.lang.StringIndexOutOfBoundsException
 * @doc.test
 *   This demonstrates that the current package has been imported:
 *   js> JDoctest
 *   [JavaClass net.cscott.jdoctest.JDoctest]
 *   js> Version.PACKAGE_NAME
 *   jdoctest
 * @doc.test
 *   Note that results referencing object hashes are properly handled in
 *   the output comparison function, even when Rhino gives a different hash
 *   at runtime:
 *   js> o = new java.lang.Object()
 *   java.lang.Object@1d2068d
 *   js> o
 *   java.lang.Object@1d2068d
 *   js> new java.lang.Object()
 *   java.lang.Object@1ac2f9c
 */
public class JDoctest implements Taglet { ... }
```
## Build and install

To build:
 * You must have JDK 17 or greater.  JDoctest uses the `jdk.javadoc.doclet`
   taglet API; the old `com.sun.tools.doclets` taglet API it used
   through version 1.7 was removed in JDK 10.  (JDoctest 1.7 supports
   JDK 1.5 through 9.)  If you are upgrading from JDoctest 1.x,
   see [HISTORY.md] for changes which may affect your doctests.
 * Run `ant javadoc`, which will build the source code and then run its
   doctests.
 * One (ignored) failure is to be expected as the tests are run; it is
   included to show the results of a failing test in the output javadoc.
 * `ant test` runs the doctests as JUnit tests; `ant clean release` builds
   the release tarball, the `jdoctest` and `jdoctest-annotations` jars,
   and a bundle of both for upload to Maven Central (signed with `gpg`;
   add `-Dgpg.keyname=...` to choose the key, or `-Dskip.sign=true` to
   skip signing).

To install:

 * If your project uses Maven or Gradle, add the `net.cscott:jdoctest`
   artifacts from Maven Central instead; see [Maven and Gradle] below.
 * Move the generated `jdoctest-*.jar` as well as `lib/rhino-all-*.jar` (Rhino 1.9.2,
   including its shell) to
   a directory in your project.  Below, we will assume
   that you've placed both these files in `lib/jdoctest` in your project.
 * If you intend to use the JUnit test support, you wil also need
   JUnit 6 (Jupiter) in your classpath; `lib/` contains
   `junit-platform-console-standalone-6.1.3.jar`, which bundles
   everything needed (see junit rules for ant below).
   The javadoc tool is found automatically; no `tools.jar` is needed.

## Command-line usage

To invoke from the command line:

```sh
 javadoc -taglet net.cscott.jdoctest.JDoctest \
         -tagletpath lib/jdoctest/jdoctest.jar:lib/jdoctest/rhino-all-1.9.2.jar:bin \
	 -J-ea -J--add-opens=java.base/java.lang=ALL-UNNAMED \
         <your other javadoc options go here>
```

The above command-line assumes that your source code has been compiled
into `bin`; change that part of the `-tagletpath` if your classes are
elsewhere.  Your compiled `.class` files must be included on the
`tagletpath` so that we can run the doctests.

Note that you need to use the `-J-ea` option if you want assertions to
be enabled during the evaluation of the doctests.  You can use
`-J-ea:<your package>...` to only enable assertions in your code (not in
the entire javadoc tool).

Doctests can access the private members of your classes.  On JDK 16 and
later, this requires the `-J--add-opens=java.base/java.lang=ALL-UNNAMED`
option, since Rhino needs to make the members of `java.lang.Object`
accessible as well.  Without it, doctests only see public members.
JDK classes are always accessed through their public members, unless
you `--add-opens` their packages as well.

Add `-J-Dnet.cscott.jdoctest.output=<dir>` to emit the javascript doctests
into files in `<dir>` where they can be re-run standalone.  This is
helpful for debugging failing tests.
Add `-J-Dnet.cscott.jdoctest.verbose=true` to report a note for each
doctest which passes, as well as for those which fail.

## Ant integration

Ant rule:
```xml
  <javadoc failonerror="true" ...>
    <taglet name="net.cscott.jdoctest.JDoctest"
            path="lib/jdoctest/jdoctest.jar:lib/jdoctest/rhino-all-1.9.2.jar:bin" />
    <arg value="-J-ea" />
    <arg value="-J--add-opens=java.base/java.lang=ALL-UNNAMED" />
    <classpath> ...your classpath here... </classpath>
  </javadoc>
```

As before, replace `bin` with the appropriate path to your compiled
project classes.  The `-J-ea` option ensures assertions are enabled
in your code when doctests are evaluated. Add:
```xml
    <arg value="-J-Dnet.cscott.jdoctest.output=your/dir/here" />
```
to dump the discovered doctests into `your/dir/here` for re-running
standalone.

## Pretty output

You may want to add css rules to make the output prettier.  JDoctest has
hooks to allow the use of [google-code-prettify],
which will do syntax highlighting on the client side.  The [`build.xml`]
file for JDoctest shows how this might be hooked up.  The files in
`src/doc-files` should be copied to a `doc-files` subdirectory of the top-level
source directory of your project.

## JUnit test support

There are two ways to run doctests as [JUnit] 6 tests: annotate the
classes whose javadoc contains doctests with `@Doctests`, or rerun the
standalone tests emitted by the `net.cscott.jdoctest.output` option.
Neither needs a stub test file for each tested class:

| Setup | Stub files needed |
|-------|-------------------|
| `@Doctests`, run by a tool which looks at your compiled classes (ant's `<junitlauncher>`, the JUnit console launcher, your IDE) | none |
| `@Doctests`, run by Maven or Gradle (which only look at your *test* classes) | one `@Suite` class per project, selecting your top-level package |
| `RerunJDoctests` | none, or one subclass per output directory if it isn't `api/tests` |

### The `@Doctests` annotation

Annotate each class whose javadoc contains doctests:
```java
import net.cscott.jdoctest.Doctests;

/** ...
 * @doc.test
 *   js> MyClass.twice(3)
 *   6
 */
@Doctests
public class MyClass { ... }
```
The [`src/net/cscott/jdoctest/JDoctest.java`] file demonstrates this.
The annotation has no dependencies, so your production code doesn't need
JUnit on its classpath.

JDoctest includes a JUnit Platform test engine (with engine ID
`jdoctest`), which JUnit finds automatically when JDoctest is on the test
classpath.  Whenever JUnit is asked to test a class, a package, or a
directory of classes, this engine runs javadoc on the source file of each
`@Doctests` class among them.  Other classes are ignored.  Each class is
reported as a container with one test per `@doc.test` tag, named after
the documented member (such as `twice(int)`, or `MyClass [2]` for the
second doctest of the class itself) and linked to the tag's line in the
source file, so your IDE can jump to a failing doctest or rerun just one.
A doctest marked `(EXPECT FAIL)` which fails is reported as aborted, and
one which unexpectedly passes is reported as a failure.  The doctests of a
nested class belong to its enclosing class, unless the nested class is
annotated with `@Doctests` itself.  Since the
annotation marks the classes to test explicitly, the engine ignores the
usual test class name patterns (such as `*Test`), although it honors
package filters.

The engine expects your sources to be found below a directory named
`src`, relative to the current directory.  If your sources are elsewhere,
set the `net.cscott.jdoctest.srcRoot` [configuration parameter], for
example with a `junit-platform.properties` file on your test classpath:
```
net.cscott.jdoctest.srcRoot=src/main/java
```
or override it for a single class with `@Doctests(srcRoot="src/dir/here")`.

The following ant rule tests all the `@Doctests` classes in `bin`, with no
stub files needed:
```xml
    <target name="test" ...>
      <junitlauncher printsummary="true" haltonfailure="true">
	<classpath refid="classpath.path" />
	<testclasses>
	  <fileset dir="bin" includes="**/*.class" />
	  <fork>
	    <jvmarg value="--add-opens=java.base/java.lang=ALL-UNNAMED" />
	    <jvmarg value="-ea" />
	  </fork>
	  <listener type="legacy-plain" />
	</testclasses>
      </junitlauncher>
    </target>
```
Similarly, the JUnit console launcher will test every `@Doctests` class it
finds with `--scan-classpath`, or beneath a package with
`--select-package com.example`.

The `<fork>` element is necessary here, or else the embedded javadoc doesn't
get the correct classpath.  The engine gives javadoc the `java.class.path`
system property, together with the classpath of the class loader which
loaded your class, if it is a `URLClassLoader` (as the console launcher's
is), but ant's own class loader is neither.

### Maven and Gradle

Maven and Gradle only look for tests among the classes in your test source
tree, so they will not find `@Doctests` classes in your main source tree
by themselves.  Add a single [suite] class to your test sources which
selects your top-level package (subpackages are included):
```java
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages("com.example")
@IncludeEngines("jdoctest")
class AllDoctestsTest { }
```
Maven's Surefire only runs test classes whose names match its patterns
(`Test*`, `*Test`, `*Tests` or `*TestCase`, case-sensitively, so
`AllDoctests` would be skipped).  Don't use a suite like this with tools
that already see your compiled classes, or your doctests will run twice.

JDoctest is published on Maven Central as two artifacts:
`net.cscott:jdoctest-annotations`, which holds just the `@Doctests`
annotation and has no dependencies, for your production code; and
`net.cscott:jdoctest`, which brings in Rhino and the JUnit Platform, for
your tests.  (The `jdoctest` jar contains the annotation too, so you only
need the `jdoctest` jar alone if you don't use Maven or Gradle.)  Add
both, and the JUnit suite engine, to your build, set the source
directory in `src/test/resources/junit-platform.properties` as shown
above (`net.cscott.jdoctest.srcRoot=src/main/java`), and add
`--add-opens` if your doctests access private members.  For Maven:
```xml
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.junit</groupId>
        <artifactId>junit-bom</artifactId>
        <version>6.1.3</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>net.cscott</groupId>
      <artifactId>jdoctest-annotations</artifactId>
      <version>2.0</version>
    </dependency>
    <dependency>
      <groupId>net.cscott</groupId>
      <artifactId>jdoctest</artifactId>
      <version>2.0</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.junit.platform</groupId>
      <artifactId>junit-platform-suite</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.4</version>
        <configuration>
          <argLine>--add-opens=java.base/java.lang=ALL-UNNAMED</argLine>
        </configuration>
      </plugin>
    </plugins>
  </build>
```
For Gradle (in `build.gradle.kts`):
```kotlin
repositories { mavenCentral() }
dependencies {
    implementation("net.cscott:jdoctest-annotations:2.0")
    testImplementation("net.cscott:jdoctest:2.0")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.platform:junit-platform-suite")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}
```
The `jdoctest` artifact depends on Rhino's `org.mozilla:rhino-tools`
artifact (which depends on `org.mozilla:rhino`).  If you add Rhino to
your build yourself, use the same artifacts, rather than `rhino-all`:
`rhino-all` on Maven Central depends on those jars as well, so it would
put each Rhino class on the classpath twice.

### Rerunning emitted tests

Running javadoc on each class is slow.  A faster alternative reruns the
standalone tests written by the `net.cscott.jdoctest.output` option
described above: running `net.cscott.jdoctest.RerunJDoctests` as a JUnit
test reruns all the tests it finds beneath `api/tests`, as one test per
doctest.  Of course, these tests are only as up to date as your last
javadoc run.  If your tests are someplace else, subclass `RerunJDoctests`
and override its `testDir()` method to return the alternate directory
name, or return `RerunJDoctests.doctests("your/dir/here")` from a
`@TestFactory` method of your own.

The following ant rule implements this option:
```xml
    <target name="retest" ...>
      <junitlauncher printsummary="true" haltonfailure="true">
	<classpath refid="classpath.path" />
	<test name="net.cscott.jdoctest.RerunJDoctests">
	  <fork>
	    <jvmarg value="--add-opens=java.base/java.lang=ALL-UNNAMED" />
	    <jvmarg value="-ea" />
	  </fork>
	  <listener type="legacy-plain" />
	</test>
      </junitlauncher>
    </target>
```

## See also

*  https://docs.oracle.com/en/java/javase/21/docs/api/jdk.javadoc/jdk/javadoc/doclet/Taglet.html

## LICENSE

    JDoctest, a doctest module for javadoc.
    Copyright (C) 2009-2026 C. Scott Ananian <cscott@cscott.net>

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

[Maven and Gradle]: #maven-and-gradle
[HISTORY.md]:           HISTORY.md
[Rhino]:                https://github.com/mozilla/rhino
[JUnit]:                https://junit.org/
[configuration parameter]: https://docs.junit.org/current/user-guide/#running-tests-config-params
[suite]:                https://docs.junit.org/current/user-guide/#junit-platform-suite-engine
[doctest]:              https://docs.python.org/3/library/doctest.html
[`doctestj`]:           http://code.google.com/p/doctestj/
[Rhino's doctests]:     http://blog.norrisboyd.com/2008/03/doctest-in-rhino.html
[Calling from JavaScript into Java]: https://developer.mozilla.org/en-US/docs/Rhino/Scripting_Java
[`Taglet`]:             https://docs.oracle.com/en/java/javase/21/docs/api/jdk.javadoc/jdk/javadoc/doclet/Taglet.html
[`Doclet`]:             https://docs.oracle.com/en/java/javase/21/docs/api/jdk.javadoc/jdk/javadoc/doclet/Doclet.html
[google-code-prettify]: http://code.google.com/p/google-code-prettify/
[`build.xml`]:          ./build.xml
[`src/net/cscott/jdoctest/JDoctest.java`]: ./src/net/cscott/jdoctest/JDoctest.java
