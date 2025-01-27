package com.tyron.completion.java.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.openjdk.javax.lang.model.element.Element;
import org.openjdk.javax.lang.model.element.ExecutableElement;
import org.openjdk.javax.lang.model.type.DeclaredType;
import org.openjdk.javax.lang.model.type.TypeMirror;
import org.openjdk.source.doctree.ThrowsTree;
import org.openjdk.source.tree.BlockTree;
import org.openjdk.source.tree.CompilationUnitTree;
import org.openjdk.source.tree.EnhancedForLoopTree;
import org.openjdk.source.tree.ExpressionStatementTree;
import org.openjdk.source.tree.ForLoopTree;
import org.openjdk.source.tree.IfTree;
import org.openjdk.source.tree.ImportTree;
import org.openjdk.source.tree.MethodInvocationTree;
import org.openjdk.source.tree.NewClassTree;
import org.openjdk.source.tree.ParenthesizedTree;
import org.openjdk.source.tree.TryTree;
import org.openjdk.source.tree.WhileLoopTree;
import org.openjdk.source.util.JavacTask;
import org.openjdk.source.util.TreePath;
import org.openjdk.source.util.Trees;
import org.openjdk.tools.javac.tree.JCTree;

public class ActionUtil {

    public static boolean canIntroduceLocalVariable(@NonNull TreePath path) {
        TreePath parent = path.getParentPath();
        if (parent == null) {
            return false;
        }
        if (parent.getLeaf() instanceof JCTree.JCVariableDecl) {
            return false;
        }

        TreePath grandParent = parent.getParentPath();
        if (parent.getLeaf() instanceof ParenthesizedTree) {
            if (grandParent.getLeaf() instanceof IfTree) {
                return false;
            }
            if (grandParent.getLeaf() instanceof WhileLoopTree) {
                return false;
            }
            if (grandParent.getLeaf() instanceof ForLoopTree) {
                return false;
            }
        }

        // can't introduce a local variable on a lambda expression
        // eg. run(() -> something());
        if (parent.getLeaf() instanceof JCTree.JCLambda) {
            return false;
        }

        if (parent.getLeaf() instanceof JCTree.JCBlock) {
            // run(() -> { something(); });
            if (grandParent.getLeaf() instanceof JCTree.JCLambda) {
                return true;
            }
        }

        if (path.getLeaf() instanceof NewClassTree) {
            // run(new Runnable() { });
            if (parent.getLeaf() instanceof MethodInvocationTree) {
                return false;
            }
        }

        if (parent.getLeaf() instanceof JCTree.JCThrow) {
            return false;
        }
        return !(parent.getLeaf() instanceof ThrowsTree);
    }

    public static TreePath findSurroundingPath(TreePath path) {
        TreePath parent = path.getParentPath();
        TreePath grandParent = parent.getParentPath();

        if (parent.getLeaf() instanceof JCTree.JCVariableDecl) {
            return parent;
        }
        // inside if parenthesis
        if (parent.getLeaf() instanceof ParenthesizedTree) {
            if (grandParent.getLeaf() instanceof IfTree)  {
                return grandParent;
            }
            if (grandParent.getLeaf() instanceof WhileLoopTree) {
                return grandParent;
            }
            if (grandParent.getLeaf() instanceof ForLoopTree) {
                return grandParent;
            }
            if (grandParent.getLeaf() instanceof EnhancedForLoopTree) {
                return grandParent;
            }
        }

        if (grandParent.getLeaf() instanceof BlockTree) {
            // try catch statement
            if (grandParent.getParentPath().getLeaf() instanceof TryTree) {
                return grandParent.getParentPath();
            }

            if (grandParent.getParentPath().getLeaf() instanceof JCTree.JCLambda) {
                return parent;
            }
        }

        if (parent.getLeaf() instanceof ExpressionStatementTree) {
            if (grandParent.getLeaf() instanceof ThrowsTree) {
                return null;
            }
            return parent;
        }
        return null;
    }

    public static TypeMirror getReturnType(JavacTask task, TreePath path, ExecutableElement element) {
        return path.getLeaf() instanceof NewClassTree ?
                Trees.instance(task).getTypeMirror(path) :
                ((ExecutableElement) element).getReturnType();
    }

    public static boolean hasImport(CompilationUnitTree root, String className) {
        if (className.endsWith("[]")) {
            className = className.substring(0, className.length() - 2);
        }
        String packageName = className.substring(0, className.lastIndexOf("."));

        // if the package name of the class is java.lang, we dont need
        // to check since its already imported
        if (packageName.equals("java.lang")) {
            return true;
        }

        for (ImportTree imp : root.getImports()) {
            String name = imp.getQualifiedIdentifier().toString();
            if (name.equals(className)) {
                return true;
            }

            // if the import is a wildcard, lets check if they are on the same package
            if (name.endsWith("*")) {
                String first = name.substring(0, name.lastIndexOf("."));
                String end = className.substring(0, className.lastIndexOf("."));
                if (first.equals(end)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String getSimpleName(String className) {
        int dot = className.lastIndexOf('.');
        if (dot == -1) return className;
        return className.substring(dot + 1, className.length());
    }

    /**
     * @return null if type is an anonymous class
     */
    @Nullable
    public static String guessNameFromType(TypeMirror type) {
        if (type instanceof DeclaredType) {
            DeclaredType declared = (DeclaredType) type;
            Element element = declared.asElement();
            String name = element.getSimpleName().toString();
            // anonymous class, guess from class name
            if (name.length() == 0) {
                name = declared.toString();
                name = name.substring("<anonymous ".length(), name.length() - 1);
                name = ActionUtil.getSimpleName(name);
            }
            return "" + Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return null;
    }

    public static String guessNameFromMethodName(String methodName) {
        if (methodName == null) {
            return null;
        }
        if (methodName.startsWith("get")) {
            methodName = methodName.substring("get".length());
        }
        if (methodName.isEmpty()) {
            return null;
        }
        return  Character.toLowerCase(methodName.charAt(0)) + methodName.substring(1);
    }
}