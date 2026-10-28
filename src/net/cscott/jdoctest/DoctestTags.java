package net.cscott.jdoctest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;

/** Finds the <code>@doc.test</code> tags in a Java source file, without
 *  running them, so that each can be reported as a separate test. */
class DoctestTags {
    /** A <code>@doc.test</code> tag. */
    static class Tag {
        /** Name of the documented element, relative to {@link #owner}, with
         *  an index if the element has more than one doctest. */
        final String name;
        /** Line number of the tag. */
        final int line;
        /** The innermost {@link Doctests} class enclosing the tag, or null
         *  if there is none. */
        final Class<?> owner;
        Tag(String name, int line, Class<?> owner) {
            this.name = name;
            this.line = line;
            this.owner = owner;
        }
    }
    /** All the tags in the file, in source order. */
    final List<Tag> tags = new ArrayList<Tag>();

    private final ClassLoader loader;
    private DocTrees docTrees;
    private CompilationUnitTree cu;

    private DoctestTags(ClassLoader loader) {
        this.loader = loader;
    }

    /** Parses the given source file.  Nested classes are loaded using the
     *  given class loader, to see which have the {@link Doctests}
     *  annotation. */
    static DoctestTags parse(File sourceFile, ClassLoader loader)
        throws IOException {
        DoctestTags result = new DoctestTags(loader);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null)
            throw new IllegalStateException
                ("No java compiler available; is this a full JDK?");
        // syntax errors will be reported when javadoc runs
        DiagnosticCollector<JavaFileObject> diagnostics =
            new DiagnosticCollector<JavaFileObject>();
        try (StandardJavaFileManager fm =
             javac.getStandardFileManager(diagnostics, null, null)) {
            JavacTask task = (JavacTask) javac.getTask(
                null, fm, diagnostics, List.of("-proc:none"), null,
                fm.getJavaFileObjects(sourceFile));
            result.docTrees = DocTrees.instance(task);
            for (CompilationUnitTree cu : task.parse()) {
                result.cu = cu;
                String pkg = (cu.getPackageName() == null) ? "" :
                    cu.getPackageName().toString() + ".";
                for (Tree t : cu.getTypeDecls())
                    if (t instanceof ClassTree)
                        result.scanClass(new TreePath(new TreePath(cu), t),
                                         pkg, null, "");
            }
        }
        return result;
    }

    /** Collects the tags of a class and its members.
     *  @param binaryPrefix prefix of the class's binary name
     *  @param owner the innermost enclosing {@link Doctests} class
     *  @param namePrefix prefix of the names of the class's tags */
    private void scanClass(TreePath path, String binaryPrefix,
                           Class<?> owner, String namePrefix) {
        ClassTree ct = (ClassTree) path.getLeaf();
        String simpleName = ct.getSimpleName().toString();
        String binaryName = binaryPrefix + simpleName;
        Class<?> klass = load(binaryName);
        if (klass != null && klass.isAnnotationPresent(Doctests.class)) {
            owner = klass;
            namePrefix = "";
        }
        String self = namePrefix + simpleName;
        String memberPrefix = (owner == klass) ? "" : (self + ".");
        addTags(path, self, owner);
        for (Tree member : ct.getMembers()) {
            TreePath mp = new TreePath(path, member);
            if (member instanceof ClassTree)
                scanClass(mp, binaryName + "$", owner, memberPrefix);
            else if (member instanceof MethodTree) {
                MethodTree mt = (MethodTree) member;
                String name = mt.getName().contentEquals("<init>") ?
                    simpleName : mt.getName().toString();
                String params = mt.getParameters().stream()
                    .map(p -> p.getType().toString())
                    .collect(Collectors.joining(", "));
                addTags(mp, memberPrefix + name + "(" + params + ")", owner);
            } else if (member instanceof VariableTree)
                addTags(mp, memberPrefix +
                        ((VariableTree) member).getName(), owner);
        }
    }

    private void addTags(TreePath path, String name, Class<?> owner) {
        DocCommentTree dc = docTrees.getDocCommentTree(path);
        if (dc == null) return;
        List<Integer> found = new ArrayList<Integer>();
        for (DocTree tag : dc.getBlockTags()) {
            if (!(tag instanceof UnknownBlockTagTree) ||
                !((UnknownBlockTagTree) tag).getTagName().equals("doc.test"))
                continue;
            long pos = docTrees.getSourcePositions()
                .getStartPosition(cu, dc, tag);
            found.add((int) cu.getLineMap().getLineNumber(pos));
        }
        for (int i = 0; i < found.size(); i++)
            tags.add(new Tag(found.size() == 1 ? name :
                             (name + " [" + (i+1) + "]"),
                             found.get(i), owner));
    }

    private Class<?> load(String binaryName) {
        try {
            return Class.forName(binaryName, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
