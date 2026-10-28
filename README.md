# JDoctest

[![Build Status](https://travis-ci.org/cscott/JDoctest.png)](https://travis-ci.org/cscott/JDoctest)

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
   taglet API; the old `com.sun.javadoc` API it used through version 1.7
   was removed in JDK 13.  If you are upgrading from JDoctest 1.x,
   see [HISTORY.md] for changes which may affect your doctests.
 * Run `ant javadoc`, which will build the source code and then run its
   doctests.
 * One (ignored) failure is to be expected as the tests are run; it is
   included to show the results of a failing test in the output javadoc.

To install:

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

There are two ways to hook up doctests to [JUnit] 6.  Both provide
[dynamic tests] which you return from a `@TestFactory` method.

The simplest uses the
`net.cscott.jdoctest.output` option described above: running
`net.cscott.jdoctest.RerunJDoctests` as a JUnit test will rerun all
emitted standalone tests.  It assumes these tests were emitted into
`api/tests`; if you prefer them someplace else, subclass `RerunJDoctests`
and override its `testDir()` method to return the appropriate alternate
directory name.  Alternatively, return
`RerunJDoctests.doctests("your/dir/here")` from a `@TestFactory` method
of your own.

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

A more elegant (but slower) mechanism invokes Javadoc on each of your
source files to extract and execute the doctests.  Write a test class
which returns `JDoctestRunner.doctests()` for each of your
doctest-containing classes:
```java
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import net.cscott.jdoctest.JDoctestRunner;

class MyClassDoctests {
    @TestFactory
    Stream<DynamicTest> doctests() {
        return JDoctestRunner.doctests(MyClass.class);
    }
}
```
The [`src/net/cscott/jdoctest/JDoctestSelfTest.java`] file demonstrates
how this is done.  `JDoctestRunner` assumes that your sources are found
below a directory named `src`; if your sources are elsewhere you can
annotate the tested class with:
```java
    @SrcRoot(value="src/dir/here")
```
or pass the source directory explicitly, as
`JDoctestRunner.doctests(MyClass.class, "src/dir/here")`.

The following ant rule implements this option:
```xml
    <target name="test" ...>
      <junitlauncher printsummary="true" haltonfailure="true">
	<classpath refid="classpath.path" />
	<testclasses>
	  <fileset dir="bin" includes="**/*Doctests.class" />
	  <fork>
	    <jvmarg value="--add-opens=java.base/java.lang=ALL-UNNAMED" />
	    <jvmarg value="-ea" />
	  </fork>
	  <listener type="legacy-plain" />
	</testclasses>
      </junitlauncher>
    </target>
```
The `<fork>` element is necessary here, or else the embedded javadoc doesn't
get the correct classpath: ant seems to use a classloader which knows the
correct classpath but leaves the system property `java.class.path` set
arbitrarily (ie, wrong), and the embedded javadoc looks at `java.class.path`,
not its classloader, when resolving source references.

## See also

*  https://docs.oracle.com/en/java/javase/21/docs/api/jdk.javadoc/jdk/javadoc/doclet/Taglet.html

## LICENSE

    JDoctest, a doctest module for javadoc.
    Copyright (C) 2009,2014 C. Scott Ananian <cscott@cscott.net>

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

[HISTORY.md]:           HISTORY.md
[Rhino]:                https://github.com/mozilla/rhino
[JUnit]:                https://junit.org/
[dynamic tests]:        https://docs.junit.org/current/user-guide/#writing-tests-dynamic-tests
[doctest]:              https://docs.python.org/3/library/doctest.html
[`doctestj`]:           http://code.google.com/p/doctestj/
[Rhino's doctests]:     http://blog.norrisboyd.com/2008/03/doctest-in-rhino.html
[Calling from JavaScript into Java]: https://developer.mozilla.org/en-US/docs/Rhino/Scripting_Java
[`Taglet`]:             https://docs.oracle.com/en/java/javase/21/docs/api/jdk.javadoc/jdk/javadoc/doclet/Taglet.html
[`Doclet`]:             https://docs.oracle.com/en/java/javase/21/docs/api/jdk.javadoc/jdk/javadoc/doclet/Doclet.html
[google-code-prettify]: http://code.google.com/p/google-code-prettify/
[`build.xml`]:          ./build.xml
[`src/net/cscott/jdoctest/JDoctest.java`]: ./src/net/cscott/jdoctest/JDoctest.java
[`src/net/cscott/jdoctest/JDoctestSelfTest.java`]: ./src/net/cscott/jdoctest/JDoctestSelfTest.java
