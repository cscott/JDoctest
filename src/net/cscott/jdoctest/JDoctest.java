/*
 * JDocTaglet.
 * Copyright (c) 2009 C. Scott Ananian <cscott@cscott.net>
 *
 * Licensed under the terms of the GNU GPL v2 or later; see COPYING for details.
 */
package net.cscott.jdoctest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.InaccessibleObjectException;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.tools.Diagnostic;

import name.fraser.neil.plaintext.diff_match_patch;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.WrapFactory;
import org.mozilla.javascript.lc.type.TypeInfo;
import org.mozilla.javascript.tools.shell.Global;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.doclet.StandardDoclet;
import jdk.javadoc.doclet.Taglet;

/**
 * JDoctest implementing doctests via a @doc.test taglet.  This tag can be
 * used in any kind of {@link javax.lang.model.element.Element}.  It is not an
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
 * @doc.test
 *   Doctests are evaluated using the most recent version of ECMAScript
 *   that Rhino supports, so modern syntax may be used:
 *   js> let squares = [1, 2, 3].map(x => x * x)
 *   js> `squares: ${squares.join(", ")}`
 *   squares: 1, 4, 9
 * @doc.test
 *   We can even access private members!  (On JDK 16 and later, this
 *   requires <code>--add-opens java.base/java.lang=ALL-UNNAMED</code>.)
 *   js> new JDoctest().testsExpectedPass
 *   0
 */
@Doctests
public class JDoctest implements Taglet {
    private Reporter reporter = null;
    private DocTrees docTrees = null;
    private static boolean versionPrinted = false;
    // JavaScript context factory
    private ContextFactory contextFactory = new JDoctestContextFactory();

    /**
     * Return the name of this custom tag.
     * @doc.test
     *  js> new JDoctest().getName()
     *  doc.test
     */
    public String getName() { return "doc.test"; }
    /**
     * Will return all locations, since <code>@doc.test</code>
     * can be used in overview, module, package, type, constructor,
     * method, and field documentation.
     * @doc.test
     *  js> new JDoctest().getAllowedLocations()
     *  [OVERVIEW, MODULE, PACKAGE, TYPE, CONSTRUCTOR, METHOD, FIELD]
     */
    public Set<Location> getAllowedLocations() {
        return EnumSet.allOf(Location.class);
    }
    /**
     * Will return false since <code>@doc.test</code>
     * is not an inline tag.
     * @doc.test
     *  js> new JDoctest().isInlineTag()
     *  false
     */
    public boolean isInlineTag() {
        return false;
    }

    /**
     * Initialize this Taglet.  We fetch the {@link Reporter} from the
     * standard doclet (if that is the doclet in use) so that doctest
     * failures can be reported as javadoc errors.
     * @param env the doclet environment
     * @param doclet the doclet which instantiated this taglet
     */
    @Override
    public void init(DocletEnvironment env, Doclet doclet) {
        this.docTrees = env.getDocTrees();
        if (doclet instanceof StandardDoclet)
            this.reporter = ((StandardDoclet)doclet).getReporter();
        // if reporter is still null, we'll do the compatible
        // thing and just emit errors to stderr
        if (!versionPrinted) {
            if (reporter != null)
                reporter.print(Diagnostic.Kind.NOTE,
                               Version.PACKAGE_STRING+"; "+
                               "Bug reports to "+Version.PACKAGE_BUGREPORT);
            versionPrinted = true;
        }
    }

    /**
     * Given a list of <code>DocTree</code>s representing this custom
     * tag, return its string representation.
     * @param tags  the list of <code>DocTree</code>s representing this
     *              custom tag.
     * @param element the element to which the enclosing comment belongs
     */
    public String toString(List<? extends DocTree> tags, Element element) {
	if (tags.isEmpty()) return "";
	StringBuilder sb = new StringBuilder();
	sb.append("<dt>Tests:</dt><dd>");
	for (int i=0; i<tags.size(); i++) {
	    DocTree tag = tags.get(i);
	    SourcePosition sp = position(element, tag);
	    String pkg = getPackage(element);
	    doOne(pkg, sp, i, text(tag, sp), sb);
	}
	sb.append("</dd>");
	return sb.toString();
    }
    private static final Pattern P_comment_star =
	Pattern.compile("\\A[ \\t]*\\*+");
    private static final Pattern P_leading_ws =
	Pattern.compile("\\A[ \\t]*");
    private String text(DocTree tag, SourcePosition sp) {
	if (!(tag instanceof UnknownBlockTagTree))
	    return "";
	List<? extends DocTree> content =
	    ((UnknownBlockTagTree)tag).getContent();
	StringBuilder sb = new StringBuilder();
	for (DocTree t : content)
	    sb.append(t.toString());
	String body = sb.toString();
	if (content.isEmpty()) return body;
	// The doc tree omits the newline and indentation between the tag
	// name and its content, but we need them to properly strip
	// indentation when the test begins on the line following the tag.
	String[] lines = body.split("\n", -1);
	if (sp.docTreePath == null) {
	    // No source available (package.html, etc): guess that the
	    // first line is indented like the least-indented line which
	    // follows it.
	    String indent = null;
	    for (int i=1; i<lines.length; i++) {
		if (lines[i].trim().isEmpty()) continue;
		String ws = leadingWhitespace(lines[i]);
		if (indent == null || ws.length() < indent.length())
		    indent = ws;
	    }
	    return (indent == null) ? body : ("\n" + indent + body);
	}
	// Recover the indentation from the source.  Javadoc may have
	// stripped some indentation from each line of the comment (JDK 23
	// and later strip the indentation common to all lines) so we find
	// how much was stripped from a later line and adjust to match.
	CompilationUnitTree cu =
	    sp.docTreePath.getTreePath().getCompilationUnit();
	DocCommentTree dc = sp.docTreePath.getDocComment();
	LineMap lm = cu.getLineMap();
	long tagPos = docTrees.getSourcePositions()
	    .getStartPosition(cu, dc, tag);
	long bodyPos = docTrees.getSourcePositions()
	    .getStartPosition(cu, dc, content.get(0));
	CharSequence src;
	try {
	    src = cu.getSourceFile().getCharContent(true);
	} catch (IOException e) {
	    return body;
	}
	if (tagPos < 0 || bodyPos < tagPos || bodyPos > src.length())
	    return body;
	long firstLine = lm.getLineNumber(bodyPos);
	if (firstLine == lm.getLineNumber(tagPos))
	    return body; // test starts on the same line as the tag
	String indent = commentLineIndent
	    (src.subSequence((int) lm.getStartPosition(firstLine),
			     (int) bodyPos).toString());
	for (int i=1; i<lines.length; i++) {
	    if (lines[i].trim().isEmpty()) continue;
	    long start = lm.getStartPosition(firstLine + i);
	    if (start < 0) break;
	    int end = (int) start;
	    while (end < src.length() && src.charAt(end) != '\n') end++;
	    String raw = commentLineIndent
		(src.subSequence((int) start, end).toString());
	    int stripped = raw.length()-leadingWhitespace(lines[i]).length();
	    if (stripped > 0)
		indent = indent.substring(Math.min(stripped, indent.length()));
	    break;
	}
	return "\n" + indent + body;
    }
    private static String leadingWhitespace(String line) {
	Matcher m = P_leading_ws.matcher(line);
	m.find();
	return m.group();
    }
    /** Returns the indentation of a doc comment line, following the
     *  leading asterisks (if any). */
    private static String commentLineIndent(String line) {
	return leadingWhitespace(P_comment_star.matcher(line).replaceFirst(""));
    }
    private static String getPackage(Element e) {
	// overview documentation has no element
	while (e != null && !(e instanceof PackageElement))
	    e = e.getEnclosingElement();
	if (e == null || ((PackageElement)e).isUnnamed())
	    return null; // "unnamed package"
	return ((PackageElement)e).getQualifiedName().toString();
    }

    /** The location of a tag in a source file. */
    private static class SourcePosition {
	/** Path to the tag, for error reporting; may be null. */
	final DocTreePath docTreePath;
	/** The element containing the tag; may be null. */
	final Element element;
	/** Path to the source file. */
	final String path;
	/** Line number of the tag, or 0 if unknown. */
	final int line;
	SourcePosition(DocTreePath docTreePath, Element element,
		       String path, int line) {
	    this.docTreePath = docTreePath;
	    this.element = element;
	    this.path = path;
	    this.line = line;
	}
	/** Basename of the source file. */
	String fileName() { return new File(path).getName(); }
	@Override
	public String toString() {
	    return (line > 0) ? (path + ":" + line) : path;
	}
    }
    private SourcePosition position(Element element, DocTree tag) {
	TreePath tp = (docTrees == null || element == null) ? null :
	    docTrees.getPath(element);
	DocCommentTree dc = (tp == null) ? null :
	    docTrees.getDocCommentTree(tp);
	if (dc == null) {
	    // package.html, overview.html, etc.
	    String name = (element == null) ? "overview" : element.toString();
	    return new SourcePosition(null, element, name, 0);
	}
	CompilationUnitTree cu = tp.getCompilationUnit();
	long pos = docTrees.getSourcePositions()
	    .getStartPosition(cu, dc, tag);
	int line = (pos < 0) ? 0 : (int) cu.getLineMap().getLineNumber(pos);
	DocTreePath dtp = new DocTreePath(new DocTreePath(tp, dc), tag);
	return new SourcePosition(dtp, element,
				  cu.getSourceFile().toUri().getPath(), line);
    }
    private void report(Diagnostic.Kind kind, SourcePosition sp, String msg) {
	if (sp.docTreePath != null)
	    reporter.print(kind, sp.docTreePath, msg);
	else if (sp.element != null)
	    reporter.print(kind, sp.element, msg);
	else
	    reporter.print(kind, msg);
    }

    private int testsExpectedPass = 0, testsExpectedFail = 0;
    private int testsUnexpectedPass = 0, testsUnexpectedFail = 0;
    private static final Pattern P_initial_ws =
	Pattern.compile("\\n[ \\t]*?(?=(js|  )>)");
    private static final Pattern P_test_descr =
	Pattern.compile("(?sm)\\A(.*?)(^js&gt;)");
    private static final Pattern P_test_results =
	Pattern.compile("doctest failure running:\n(.*?)\n?expected: (.*?)\n?actual: (.*)\n(?: \\((.*#\\d+)\\))?", Pattern.DOTALL);
    private void doOne(String packageName, SourcePosition sp, int tagNum,
		       String test_text, StringBuilder sb) {
	// strip consistent indentation from all lines (based on first line)
	Matcher m = P_initial_ws.matcher(test_text);
	if (m.find()) {
	    String prefix = m.group();
	    test_text = test_text.replaceAll(Pattern.quote(prefix), "\n");
	}
	// ignore the newline following the tag name
	if (test_text.startsWith("\n"))
	    test_text = test_text.substring(1);
	// look for EXPECT FAIL in the test.
        boolean expect_fail = Patterns.expectFail(test_text);

	String fail = null;
	// Create Javascript context.
	String prologue = (packageName == null) ? null :
	    ("importPackage(Packages."+packageName+");");
	Context cx = contextFactory.enterContext();
	try {
	    Global global = new Global(cx); // this is also a scope.
	    // import the package.
	    if (prologue!=null)
		cx.evaluateString(global, prologue, "<init>", 1, null);
	    // okay, evaluate the doctest.
	    // if the tests fail, we will throw an exception here.
	    int testsRun = global.runDoctest(cx, global, test_text,
					     sp.fileName(), sp.line);
	    if (expect_fail) {
		testsUnexpectedPass += testsRun;
		fail = "doctest unexpectedly passed.";
		if (reporter!=null)
		    report(Diagnostic.Kind.ERROR, sp, fail);
		else {
		    System.err.println("DOCTEST UNEXPECTED PASS at "+sp);
		    System.exit(2);
		}
	    } else {
		testsExpectedPass += testsRun;
		if (reporter!=null && false /* too noisy */)
		    report(Diagnostic.Kind.NOTE, sp, testsRun+" tests passed.");
	    }
	} catch (AssertionError e) {
	    fail = e.getMessage();
	    if (fail==null) fail="<unknown assertion failure>";
	} catch (RhinoException e) {
	    fail = e.getMessage();
	    if (fail==null) fail="<unknown failure>";
	} finally {
	    Context.exit();
	}
	if (fail != null) {
	    // hack layout a bit
	    Matcher failmsg = P_test_results.matcher(fail);
	    if (failmsg.matches()) {
		final String testname = failmsg.group(1);
		final String expected = failmsg.group(2);
		final String actual = failmsg.group(3);
		final String srcloc = failmsg.group(4);
		// run Diff!
		final String diff = diffString(expected, actual);
		fail = "Doctest failure:\n"+
		    html_escape(testname) + "\n"+
		    "Expected:\n" + html_escape(expected)+"\n"+
		    "Actual:\n" + html_escape(actual)+"\n"+
		    "Diff:\n" + diff +"\n";
		if (srcloc!=null) { fail+='('+html_escape(srcloc)+')'; }
	    } else {
		fail = fail.replaceAll("(?m)^(expected|actual): ", "$0\n");
		fail = html_escape(fail);
	    }
	    if (expect_fail) {
		testsExpectedFail += 1;
		if (reporter!=null)
		    report(Diagnostic.Kind.WARNING, sp, "Doctest failed as expected at "+sp);
		else {
		    System.err.println("DOCTEST EXPECTED FAIL at "+sp);
		}
	    } else {
		testsUnexpectedFail += 1;
		if (reporter!=null)
		    report(Diagnostic.Kind.ERROR, sp, html_unescape(fail));
		else {
		    System.err.println("DOCTEST UNEXPECTED FAIL at "+sp);
		    System.err.println(html_unescape(fail));
		    System.exit(1);
		}
	    }
	}
	// emit the test text to a file, if requested
	String test_path = System.getProperty("net.cscott.jdoctest.output");
	if (test_path != null) {
	    File outdir = (packageName == null) ? new File(test_path) :
		new File(test_path, packageName);
	    String baseName=sp.fileName().replaceFirst("\\..*","");
	    File outf = new File
		(outdir, "test-"+baseName+"-"+sp.line+"-"+(tagNum+1)+".js");
	    try {
		outdir.mkdirs(); // ensure directory exists
		Writer w = new OutputStreamWriter
		    (new FileOutputStream(outf),"utf-8");
		w.write(sp.toString()+"\n\n");
		w.write(prologue == null ? test_text :
			test_text.replaceFirst("js>","js> "+prologue+"\njs>"));
		w.close();
	    } catch (IOException e) {
		if (reporter!=null)
		    report(Diagnostic.Kind.ERROR, sp, "Couldn't write to "+outf);
		else {
		    System.err.println("ERROR: Couldn't write to "+outf);
		    System.exit(3);
		}
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
	    sb.append(fail); // already html-escaped
	    sb.append("</pre>\n");
	}
    }

    private static final diff_match_patch differ = new diff_match_patch() {{
        Diff_Timeout = 0;
    }};
    private static String diffString(String a, String b) {
        // Use diff implementation from:
        // http://code.google.com/p/google-diff-match-patch/
        StringBuffer sb = new StringBuffer();
        LinkedList<diff_match_patch.Diff> diffs = differ.diff_main(a, b, false);
        differ.diff_cleanupSemantic(diffs);
        for (diff_match_patch.Diff d : diffs) {
            switch (d.operation) {
            case EQUAL:
                sb.append(html_escape(d.text));
                break;
            case DELETE:
                sb.append("<del>");
                sb.append(html_escape(d.text));
                sb.append("</del>");
                break;
            case INSERT:
                sb.append("<ins>");
                sb.append(html_escape(d.text));
                sb.append("</ins>");
                break;
            }
        }
        return sb.toString();
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
    private static final Pattern P_unhtml =
        Pattern.compile("&\\w+;|</?(ins|del)>");
    // change <del>...</del> to [-...-] and <ins>...</ins> to {+...+}
    // as well as replacing &...; sequences
    private static String html_unescape_group(String s) {
        s = s.intern();
        if (s == "<del>")  return "[-";
        if (s == "</del>") return "-]";
        if (s == "<ins>")  return "{+";
        if (s == "</ins>") return "+}";
        if (s == "&lt;")   return "<";
        if (s == "&gt;")   return ">";
        if (s == "&amp;")  return "&";
        if (s == "&quot;") return "\"";
        assert false; return s;
    }
    private static String html_unescape(String s) {
        Matcher m = P_unhtml.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find())
            m.appendReplacement(sb, html_unescape_group(m.group()));
        m.appendTail(sb);
        return sb.toString();
    }

    /** JavaScript context with privileged access to Java. */
    static class JDoctestContextFactory extends ContextFactory {
	protected Context makeContext() {
	    Context cx = new JDoctestContext(this);
	    cx.setWrapFactory(new JDoctestWrapFactory());
	    return cx;
	}
	// enable privileged access to Java
	public boolean hasFeature(Context cx, int featureIndex) {
	    switch (featureIndex) {
	    case Context.FEATURE_ENHANCED_JAVA_ACCESS:
		return !(cx instanceof JDoctestContext &&
			 ((JDoctestContext)cx).restrictedAccess > 0);
	    case Context.FEATURE_RESERVED_KEYWORD_AS_IDENTIFIER:
	    case Context.FEATURE_LOCATION_INFORMATION_IN_ERROR:
		return true;
	    default:
		return super.hasFeature(cx, featureIndex);
	    }
	}
    }
    static class JDoctestContext extends Context {
	/** When positive, FEATURE_ENHANCED_JAVA_ACCESS is disabled. */
	int restrictedAccess = 0;
	JDoctestContext(ContextFactory factory) {
	    super(factory);
	    // the most recent ECMAScript version supported by Rhino
	    setLanguageVersion(Context.VERSION_ECMASCRIPT);
	}
    }
    /**
     * Wrap factory which falls back to public-only access for classes
     * whose private members can't be made accessible.  Since JDK 16,
     * the JDK's modules are strongly encapsulated, and Rhino's
     * FEATURE_ENHANCED_JAVA_ACCESS fails when it calls
     * <code>setAccessible()</code> on (for instance) the private
     * members of <code>java.lang.Object</code>.  Use (for example)
     * <code>--add-opens java.base/java.lang=ALL-UNNAMED</code> to allow
     * access to the private members of your own classes.
     */
    static class JDoctestWrapFactory extends WrapFactory {
	private interface Wrapper { Object wrap(); }
	private static Object withFallback(Context cx, Wrapper w) {
	    try {
		return w.wrap();
	    } catch (InaccessibleObjectException e) {
		if (!(cx instanceof JDoctestContext)) throw e;
		// try again with public-only access
		JDoctestContext jcx = (JDoctestContext) cx;
		jcx.restrictedAccess++;
		try {
		    return w.wrap();
		} finally {
		    jcx.restrictedAccess--;
		}
	    }
	}
	@Override
	public Object wrap(final Context cx, final Scriptable scope,
			   final Object obj, final TypeInfo staticType) {
	    return withFallback(cx, () -> super.wrap(cx, scope, obj, staticType));
	}
	@Override
	public Scriptable wrapNewObject(final Context cx, final Scriptable scope,
					final Object obj) {
	    return (Scriptable) withFallback
		(cx, () -> super.wrapNewObject(cx, scope, obj));
	}
	@Override
	public Scriptable wrapAsJavaObject(final Context cx,
					   final Scriptable scope,
					   final Object javaObject,
					   final TypeInfo staticType) {
	    return (Scriptable) withFallback
		(cx, () -> super.wrapAsJavaObject(cx, scope, javaObject,
						  staticType));
	}
	@Override
	public Scriptable wrapJavaClass(final Context cx, final Scriptable scope,
					@SuppressWarnings("rawtypes")
					final Class javaClass) {
	    return (Scriptable) withFallback
		(cx, () -> super.wrapJavaClass(cx, scope, javaClass));
	}
    }
}
