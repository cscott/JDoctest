# JDoctest

[![Build Status](https://travis-ci.org/cscott/JDoctest.png)](https://travis-ci.org/cscott/JDoctest)

JDoctest is an implementation of Python's [doctest] for Java, based on the
ideas in [`doctestj`] and [Rhino's doctests].

Unlike doctestj, JDoctest is a javadoc [`Taglet`], rather than a [`Doclet`].
It doesn't replace the standard javadoc, it just adds a new `@doc.test`
tag.  We also use a multiline format similar to Python's doctest
(based on Rhino's implementation) rather than insist on a single
evaluated expression, as doctestj does.

Doctest blocks are written in Javascript.  [Calling from Javascript into Java]
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
 *     >   print(e.javaException.getMessage())
 *     > }
 *   String index out of range: 3
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
 * You must have a JDK 1.5 or greater.  In JDoctest version 1.5 I removed
   support for pre-1.5 JDKs.
   * In older JDKs, you might need to copy `sample.build.properties`
   to `build.properties` and edit it to properly reflect that path to
   your JDK, so that we can find the JDK's `tools.jar` file.
 * Run `ant javadoc`, which will build the source code and then run its
   doctests.
 * One (ignored) failure is to be expected as the tests are run; it is
   included to show the results of a failing test in the output javadoc.

To install:

 * Move the generated `jdoctest-*.jar` as well as `lib/rhino*/js.jar` to
   a directory in your project.  Below, we will assume
   that you've placed both these files in `lib/jdoctest` in your project.
 * If you intend to use the JUnit test support, you wil also need
   `lib/junit-4.6.jar` and the `tools.jar` from your JDK release in your
   classpath (see junit rules for ant below).

## Command-line usage

To invoke from the command line:

```sh
 javadoc -taglet net.cscott.jdoctest.JDoctest \
         -tagletpath lib/jdoctest/jdoctest.jar:lib/jdoctest/js.jar:bin \
	 -J-ea \
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

Add `-J-Dnet.cscott.jdoctest.output=<dir>` to emit the javascript doctests
into files in `<dir>` where they can be re-run standalone.  This is
helpful for debugging failing tests.

## Ant integration

Ant rule:
```xml
  <javadoc failonerror="true" ...>
    <taglet name="net.cscott.jdoctest.JDoctest"
            path="lib/jdoctest/jdoctest.jar:lib/jdoctest/js.jar:bin" />
    <arg value="-J-ea" />
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

There are two ways to hook up doctests to JUnit.

The simplest uses the
`net.cscott.jdoctest.output` option described above: running
`net.cscott.jdoctest.RerunJDoctests` as a JUnit test will rerun all
emitted standalone tests.  It assumes these tests were emitted into
`api/tests`; if you prefer them someplace else, subclass `RerunJDoctests`
and reimplement the `listTests()` method to call `listTests(String dirName)`
with the appropriate alternate directory name.

The following ant rule implements this option:
```xml
    <target name="retest" ...>
      <junit>
	<formatter type="plain"/>
	<test name="net.cscott.jdoctest.RerunJDoctests" />
	<classpath refid="classpath.path" />
	<assertions>
	  <enable/>
	</assertions>
      </junit>
    </target>
```

A more elegant (but slower) mechanism invokes Javadoc on each of your
source files to extract and execute the doctests.  Simply annotate each
of your doctest-containing classes with the JUnit annotation:
```java
    @RunWith(value=JDoctestRunner.class)
```
The [`src/net/cscott/jdoctest/JDoctest.java`] file demonstrates how this is
done.  JUnit can then directly execute the doctests for your class.
`JDoctestRunner` assumes that your sources are found below a directory
named `src`; if your sources are elsewhere you can use the annotation:
```java
    @SrcRoot(value="src/dir/here")
```
If you need to use the `@SrcRoot` annotation extensively, you might find it
easier to subclass `JDoctestRunner` and override its `defaultSrcRoot()` method.

The following ant rule implements this option:
```xml
    <target name="test" ...>
      <junit printsummary="yes" fork="yes" forkmode="once" haltonfailure="yes">
	<formatter type="plain"/>
	<test name="...your source files here..." />
	<classpath refid="classpath.path" />
	<assertions>
	  <enable />
	</assertions>
      </junit>
    </target>
```
The `fork="yes"` option is necessary here, or else the embedded javadoc doesn't
get the correct classpath: ant seems to use a classloader which knows the
correct classpath but leaves the system property `java.class.path` set
arbitrarily (ie, wrong), and the embedded javadoc looks at `java.class.path`,
not its classloader, when resolving source references.

## See also

*  http://java.sun.com/j2se/1.5.0/docs/guide/javadoc/taglet/overview.html

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

[doctest]:              https://docs.python.org/3/library/doctest.html
[`doctestj`]:           http://code.google.com/p/doctestj/
[Rhino's doctests]:     http://blog.norrisboyd.com/2008/03/doctest-in-rhino.html
[Calling from JavaScript into Java]: https://developer.mozilla.org/en-US/docs/Rhino/Scripting_Java
[`Taglet`]:             http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/taglet/overview.html
[`Doclet`]:             http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/doclet/overview.html
[google-code-prettify]: http://code.google.com/p/google-code-prettify/
[`build.xml`]:          ./build.xml
[`src/net/cscott/jdoctest/JDoctest.java`]: ./src/net/cscott/jdoctest/JDoctest.java
