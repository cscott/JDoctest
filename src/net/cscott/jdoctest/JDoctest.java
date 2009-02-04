/**
 * JDocTaglet.
 * Copyright (c) 2009 C. Scott Ananian <cscott@cscott.net>
 *
 * Licensed under the terms of the GNU GPL v2 or later; see COPYING for details.
 */
package net.cscott.jdoctest;

import com.sun.tools.doclets.Taglet;
import com.sun.javadoc.*;
import java.util.Map;

/**
 * JDoctest implementing doctests via a @test taglet.  This tag can be
 * used in any kind of {@link com.sun.javadoc.Doc}.  It is not an
 * inline tag.  A "@test" tag specifies an interactive javascript
 * session; the output of that session should match the output
 * provided.
 *
 * @author C. Scott Ananian
 */

public class JDoctest implements Taglet {
    /**
     * Return the name of this custom tag.
     */
    public String getName() { return "test"; }
    /**
     * Will return true since <code>@test</code>
     * can be used in field documentation.
     */
    public boolean inField() {
        return true;
    }
    /**
     * Will return true since <code>@test</code>
     * can be used in constructor documentation.
     */
    public boolean inConstructor() {
        return true;
    }
    /**
     * Will return true since <code>@test</code>
     * can be used in method documentation.
     */
    public boolean inMethod() {
        return true;
    }
    /**
     * Will return true since <code>@test</code>
     * can be used in method documentation.
     */
    public boolean inOverview() {
        return true;
    }
    /**
     * Will return true since <code>@test</code>
     * can be used in package documentation.
     */
    public boolean inPackage() {
        return true;
    }
    /**
     * Will return true since <code>@test</code>
     * can be used in type documentation (classes or interfaces).
     */
    public boolean inType() {
        return true;
    }
    /**
     * Will return false since <code>@test</code>
     * is not an inline tag.
     * @return false since <code>@todo</code>
     * is not an inline tag.
     */
    
    public boolean isInlineTag() {
        return false;
    }
    
    /**
     * Register this Taglet.
     * @param tagletMap  the map to register this tag to.
     */
    public static void register(Map tagletMap) {
       JDoctest taglet = new JDoctest();
       Taglet t = (Taglet) tagletMap.get(taglet.getName());
       if (t != null) {
           tagletMap.remove(taglet.getName());
       }
       tagletMap.put(taglet.getName(), taglet);
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
	StringBuilder sb = new StringBuilder();
	for (int i=0; i<tags.length; i++)
	    doOne(tags[i].text(), sb);
	return sb.toString();
    }

    private void doOne(String test_text, StringBuilder sb) {
    }
}

