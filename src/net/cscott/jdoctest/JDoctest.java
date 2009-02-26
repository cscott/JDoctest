/*
 * JDocTaglet.
 * Copyright (c) 2009 C. Scott Ananian <cscott@cscott.net>
 *
 * Licensed under the terms of the GNU GPL v2 or later; see COPYING for details.
 */
package net.cscott.jdoctest;

import com.sun.tools.doclets.Taglet;
import com.sun.javadoc.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.regex.*;
import java.util.Map;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.tools.shell.Global;

/**
 * JDoctest implementing doctests via a @doc.test taglet.  This tag can be
 * used in any kind of {@link com.sun.javadoc.Doc}.  It is not an
 * inline tag.  A "@doc.test" tag specifies an interactive javascript
 * session, in an environment where the class' package has been imported.
 * The output of the javascript session should match the output
 * provided.
 *
 * @author C. Scott Ananian
 * @doc.test
 *   This is an example of a test which passes:
 *   js> "a".equals("a")
 *   true
 * @doc.test (EXPECT FAIL)
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
public class JDoctest implements Taglet {
    private DocErrorReporter docErrorReporter = null;
    private static boolean versionPrinted = false;
    /**
     * Return the name of this custom tag.
     * @doc.test
     *  js> new JDoctest().getName()
     *  doc.test
     */
    public String getName() { return "doc.test"; }
    /**
     * Will return true since <code>@doc.test</code>
     * can be used in field documentation.
     */
    public boolean inField() {
        return true;
    }
    /**
     * Will return true since <code>@doc.test</code>
     * can be used in constructor documentation.
     */
    public boolean inConstructor() {
        return true;
    }
    /**
     * Will return true since <code>@doc.test</code>
     * can be used in method documentation.
     */
    public boolean inMethod() {
        return true;
    }
    /**
     * Will return true since <code>@doc.test</code>
     * can be used in method documentation.
     */
    public boolean inOverview() {
        return true;
    }
    /**
     * Will return true since <code>@doc.test</code>
     * can be used in package documentation.
     */
    public boolean inPackage() {
        return true;
    }
    /**
     * Will return true since <code>@doc.test</code>
     * can be used in type documentation (classes or interfaces).
     */
    public boolean inType() {
        return true;
    }
    /**
     * Will return false since <code>@doc.test</code>
     * is not an inline tag.
     */
    
    public boolean isInlineTag() {
        return false;
    }
    
    /**
     * Register this Taglet.
     * @param tagletMap  the map to register this tag to.
     * @doc.test
     *  js> m = java.util.HashMap()
     *  {}
     *  js> JDoctest.register(m)
     *  js> m.get("doc.test")
     *  net.cscott.jdoctest.JDoctest@de1b8a
     */
    public static void register(Map tagletMap) {
       JDoctest taglet = new JDoctest();
       // this is an evil hack: try to fetch the rootDoc from the
       // standard HTML doclet, in order to get a DocErrorReporter
       try {
	   taglet.docErrorReporter =
	       com.sun.tools.doclets.standard.Standard.htmlDoclet
	       .configuration().root;
       } catch (Throwable t) {
	   /* ignore: we'll do the compatible thing and just emit
	    * errors to stderr */
       }
       Taglet t = (Taglet) tagletMap.get(taglet.getName());
       if (t != null) {
           tagletMap.remove(taglet.getName());
       }
       tagletMap.put(taglet.getName(), taglet);
       if (taglet.docErrorReporter!=null && !versionPrinted) {
	   taglet.docErrorReporter.printNotice
	       (Version.PACKAGE_STRING+"; "+
		"Bug reports to "+Version.PACKAGE_BUGREPORT);
	   versionPrinted = true;
       }
    }
    /**
     * Given the <code>Tag</code> representation of this custom
     * tag, return its string representation.
     * @param tag   the <code>Tag</code> representation of this custom tag.
     */
    public String toString(Tag tag) {
	return toString(new Tag[] { tag });
    }
    
    /**
     * Given an array of <code>Tag</code>s representing this custom
     * tag, return its string representation.
     * @param tags  the array of <code>Tag</code>s representing of this custom tag.
     */
    public String toString(Tag[] tags) {
	if (tags.length == 0) return "";
	StringBuilder sb = new StringBuilder();
	sb.append("<dt><b>Tests:</b></dt><dd>");
	for (int i=0; i<tags.length; i++) {
	    SourcePosition sp = tags[i].position();
	    String pkg = getPackage(tags[i].holder());
	    doOne(pkg, sp, tags[i].text(), sb);
	}
	sb.append("</dd>");
	return sb.toString();
    }
    private static String getPackage(Doc d) {
	if (d instanceof ProgramElementDoc)
	    return getPackage(((ProgramElementDoc)d).containingPackage());
	if (d instanceof PackageDoc)
	    return ((PackageDoc)d).name();
	if (d instanceof RootDoc)
	    return null; // "unnamed package"
	assert false : "unknown Doc type "+d;
	return null;
    }

    private int testsExpectedPass = 0, testsExpectedFail = 0;
    private int testsUnexpectedPass = 0, testsUnexpectedFail = 0;
    private static final Pattern P_initial_ws =
	Pattern.compile("\\n[ \\t]*");
    private static final Pattern P_expect_fail =
	Pattern.compile("\\bEXPECT\\s+FAIL\\b");
    private static final Pattern P_test_descr =
	Pattern.compile("(?sm)\\A(.*?)(^js&gt;)");
    private void doOne(String packageName, SourcePosition sp,
		       String test_text, StringBuilder sb) {
	// strip consistent indentation from all lines (based on first line)
	Matcher m = P_initial_ws.matcher(test_text);
	if (m.find()) {
	    String prefix = m.group();
	    test_text = test_text.replaceAll(Pattern.quote(prefix), "\n");
	}
	// look for EXPECT FAIL in the test.
	boolean expect_fail = P_expect_fail.matcher(test_text).find();

	String fail = null;
	// Create Javascript context.
	String prologue = (packageName == null) ? null :
	    ("importPackage("+packageName+");");
	Context cx = Context.enter();
	try {
	    Global global = new Global(); // this is also a scope.
	    global.init(cx);
	    // import the package.
	    if (prologue!=null)
		cx.evaluateString(global, prologue, "<init>", 1, null);
	    // okay, evaluate the doctest.
	    // if the tests fail, we will throw an exception here.
	    int testsRun = global.runDoctest(cx, global, test_text,
					     sp.file().getName(), sp.line());
	    if (expect_fail) {
		testsUnexpectedPass += testsRun;
		fail = "doctest unexpectedly passed.";
		if (docErrorReporter!=null)
		    docErrorReporter.printError(sp, fail);
		else {
		    System.err.println("DOCTEST UNEXPECTED PASS at "+sp);
		}
	    } else {
		testsExpectedPass += testsRun;
		if (docErrorReporter!=null && false /* too noisy */)
		    docErrorReporter.printNotice(sp,
						 testsRun+" tests passed.");
	    }
	} catch (RhinoException e) {
	    fail = e.getMessage();
	    if (expect_fail) {
		testsExpectedFail += 1;
		if (docErrorReporter!=null)
		    docErrorReporter.printWarning(sp, "Doctest failed as expected at "+sp);
		else {
		    System.err.println("DOCTEST EXPECTED FAIL at "+sp);
		}
	    } else {
		testsUnexpectedFail += 1;
		if (docErrorReporter!=null)
		    docErrorReporter.printError(sp, fail);
		else {
		    System.err.println("DOCTEST UNEXPECTED FAIL at "+sp);
		    System.err.println(fail);
		}
	    }
	} finally {
	    Context.exit();
	}
	// emit the test text to a file, if requested
	String test_path = System.getProperty("net.cscott.jdoctest.output");
	if (test_path != null) {
	    File outdir = new File(test_path, packageName);
	    File outf = new File(outdir, String.format
				 ("test-%08x.js", new Object[]
				     {Integer.valueOf(test_text.hashCode())}));
	    try {
		outdir.mkdirs(); // ensure directory exists
		Writer w = new OutputStreamWriter
		    (new FileOutputStream(outf),"utf-8");
		w.write(sp.toString()+"\n\n");
		w.write(prologue == null ? test_text :
			test_text.replaceFirst("js>","js> "+prologue+"\njs>"));
		w.close();
	    } catch (IOException e) {
		if (docErrorReporter!=null)
		    docErrorReporter.printError(sp, "Couldn't write to "+outf);
		else
		    System.err.println("ERROR: Couldn't write to "+outf);
	    }
	}
	// typeset the text.
	String s = html_escape(test_text);
	// text before the first js> is a test description.
	Matcher mm = P_test_descr.matcher(s);
	if (mm.find() && mm.end(1) > 0) {
	    sb.append("<div class=\"doctest-info\">");
	    sb.append(mm.group(1));
	    sb.append("</div>");
	    s = s.substring(mm.start(2));
	}
	sb.append("<pre class=\"prettyprint lang-js\">");
	// flag the js> and > prompts as "not code"
	s = s.replaceAll("(?m)^(js|  )&gt;",
			 "<span class=\"nocode doctest-prompt\">$0</span>");
	// any remaining lines are responses, "not code"
	s = s.replaceAll("(?m)^[^<].*$",
			 "<span class=\"nocode doctest-output\">$0</span>");
	sb.append(s);
	sb.append("</pre>\n");
	if (fail!=null) {
	    sb.append("<pre class=\"doctest-fail\" style=\"background:red;color:white;font-weight:bold;\">");
	    sb.append(html_escape(fail));
	    sb.append("</pre>\n");
	}
    }

    private static final Pattern P_html_special = Pattern.compile("[<>&\"]");
    private static String html_escape_char(String s) {
	assert s.length()==1;
	switch (s.charAt(0)) {
	case '<': return "&lt;";
	case '>': return "&gt;";
	case '&': return "&amp;";
	case '"': return "&quot;";
	default: return s;
	}
    }
    private static String html_escape(String s) {
	Matcher m = P_html_special.matcher(s);
	StringBuffer sb = new StringBuffer();
	while (m.find())
	    m.appendReplacement(sb, html_escape_char(m.group()));
	m.appendTail(sb);
	return sb.toString();
    }
}

