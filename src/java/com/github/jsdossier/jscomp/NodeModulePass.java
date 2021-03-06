/*
Copyright 2013-2016 Jason Leyba

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.github.jsdossier.jscomp;

import static com.github.jsdossier.jscomp.Module.Type.ES6;
import static com.github.jsdossier.jscomp.Module.Type.NODE;
import static com.github.jsdossier.jscomp.Types.getModuleId;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.NodeTraversal.traverseEs6;
import static com.google.javascript.rhino.IR.call;
import static com.google.javascript.rhino.IR.declaration;
import static com.google.javascript.rhino.IR.exprResult;
import static com.google.javascript.rhino.IR.getprop;
import static com.google.javascript.rhino.IR.name;
import static com.google.javascript.rhino.IR.string;
import static com.google.javascript.rhino.IR.var;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Modules;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.Es6RewriteDestructuring;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/**
 * Processes all files flagged as CommonJS modules by renaming all variables so they may be safely
 * inserted into the global scope to be processed along with all other files. This pass will also
 * generate {@code goog.provide} statements for each module and replace all calls to {@code require}
 * with a direct reference to the required module's {@code exports} object.
 *
 * <p>For instance, suppose we had two modules, foo.js and bar.js:
 *
 * <pre><code>
 *   // foo.js
 *   exports.sayHi = function() {
 *     console.log('hello, world!');
 *   };
 *   exports.sayBye = function() {
 *     console.log('goodbye, world!');
 *   };
 *
 *   // bar.js
 *   var foo = require('./foo');
 *   foo.sayHi();
 * </code></pre>
 *
 * <p>Given the code above, this pass would produce:
 *
 * <pre><code>
 *   var module$foo = {exports: {}};
 *   module$foo.sayHi = function() {
 *     console.log('hello, world!');
 *   };
 *   module$foo.sayBye = function() {
 *     console.log('hello, world!');
 *   };
 *
 *   var module$bar = {exports: {}};
 *   var foo$$module$bar = module$foo;
 *   foo$$module$bar.sayHi();
 * </code></pre>
 */
final class NodeModulePass implements DossierCompilerPass {

  // NB: The following errors are forbid situations that complicate type checking.

  /** Reported when there are multiple assignments to module.exports. */
  private static final DiagnosticType MULTIPLE_ASSIGNMENTS_TO_MODULE_EXPORTS =
      DiagnosticType.error(
          "DOSSIER_INVALID_MODULE_EXPORTS_ASSIGNMENT",
          "Multiple assignments to module.exports are not permitted");

  private static final DiagnosticType REQUIRE_INVALID_MODULE_ID =
      DiagnosticType.error(
          "DOSSIER_REQUIRE_INVALID_MODULE_ID", "Invalid module ID passed to require()");

  private final TypeRegistry typeRegistry;
  private final FileSystem inputFs;
  private final ImmutableSet<Path> modulePaths;
  private final NodeLibrary nodeLibrary;

  // TODO(jleyba): Use Module.Id.
  private String currentModule = null;

  @Inject
  NodeModulePass(
      TypeRegistry typeRegistry,
      @Input FileSystem inputFs,
      @Modules ImmutableSet<Path> modulePaths,
      NodeLibrary nodeLibrary) {
    this.typeRegistry = typeRegistry;
    this.inputFs = inputFs;
    this.modulePaths = modulePaths;
    this.nodeLibrary = nodeLibrary;
  }

  @Override
  public void process(DossierCompiler compiler, Node root) {
    if (root.isFromExterns()) {
      return;
    }

    CommonJsModuleCallback callback = new CommonJsModuleCallback();
    traverseEs6(compiler, root, callback);
  }

  /** Main traversal callback for processing the AST of a CommonJS module. */
  private class CommonJsModuleCallback implements NodeTraversal.Callback {

    private final List<Node> moduleExportRefs = new ArrayList<>();
    private final Map<String, Node> googRequireExpr = new HashMap<>();

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        checkState(currentModule == null);

        String sourceName = n.getSourceFileName();
        Path path = inputFs.getPath(n.getSourceFileName());
        if (!nodeLibrary.isModulePath(sourceName)
            && (typeRegistry.isModule(path) || !modulePaths.contains(path))) {
          return false;
        }

        if (nodeLibrary.isModulePath(sourceName)) {
          currentModule = nodeLibrary.getIdFromPath(sourceName);
        } else {
          currentModule = ES6.newId(path).getCompiledName();
        }

        traverseEs6(t.getCompiler(), n, new SplitRequireDeclarations());
        traverseEs6(t.getCompiler(), n, new Es6RewriteDestructuring(t.getCompiler()));
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        visitScript(t, n);
      }

      if (isCall(n, "require")) {
        visitRequireCall(t, n, parent);
      }

      if (n.isGetProp() && n.matchesQualifiedName("module.exports")) {
        if (t.getScope().getVar("module") == null) {
          moduleExportRefs.add(n);
        }
      }
    }

    private void visitScript(NodeTraversal t, Node script) {
      if (currentModule == null) {
        return;
      }

      // Remove any 'use strict' directives. The compiler adds these by default to
      // closure modules and will generate a warning if specified directly.
      // TODO: remove when https://github.com/google/closure-compiler/issues/1263 is fixed.
      Set<String> directives = script.getDirectives();
      if (directives != null && directives.contains("use strict")) {
        // Directives is likely an immutable collection, so we need to make a copy.
        Set<String> newDirectives = new HashSet<>();
        newDirectives.addAll(directives);
        newDirectives.remove("use strict");
        script.setDirectives(newDirectives);
      }

      processModuleExportRefs(t);

      Node moduleBody = createModuleBody();
      moduleBody.srcrefTree(script);
      if (script.getChildCount() > 0) {
        moduleBody.addChildrenToBack(script.removeChildren());
      }
      script.addChildToBack(moduleBody);

      t.getInput().addProvide(currentModule);

      traverseEs6(t.getCompiler(), script, new TypeCleanup());

      googRequireExpr.clear();
      currentModule = null;

      t.reportCodeChange();
    }

    private Node createModuleBody() {
      Node moduleBody = new Node(Token.MODULE_BODY);
      moduleBody.addChildToBack(
          exprResult(call(getprop(name("goog"), string("module")), string(currentModule))));
      googRequireExpr.values().forEach(moduleBody::addChildToBack);
      return moduleBody;
    }

    private void processModuleExportRefs(NodeTraversal t) {
      Node moduleExportsAssignment = null;
      for (Node ref : moduleExportRefs) {
        if (isTopLevelAssignLhs(ref)) {
          if (moduleExportsAssignment != null) {
            t.report(ref, MULTIPLE_ASSIGNMENTS_TO_MODULE_EXPORTS);
            return;
          } else {
            moduleExportsAssignment = ref;
          }
        }
      }

      for (Node ref : moduleExportRefs) {
        Node parent = ref.getParent();
        if (parent != null) {
          parent.replaceChild(ref, name("exports").srcrefTree(ref));
        }
      }
    }

    private boolean isTopLevelAssign(Node n) {
      return n.isAssign()
          && n.getParent() != null
          && n.getParent().isExprResult()
          && n.getGrandparent() != null
          && n.getGrandparent().isScript();
    }

    private boolean isTopLevelAssignLhs(Node n) {
      return n.getParent() != null
          && n == n.getParent().getFirstChild()
          && isTopLevelAssign(n.getParent());
    }

    private void visitRequireCall(NodeTraversal t, Node require, Node parent) {
      Path currentFile = inputFs.getPath(t.getSourceName());

      String modulePath = require.getSecondChild().getString();

      if (modulePath.isEmpty()) {
        t.report(require, REQUIRE_INVALID_MODULE_ID);
        return;
      }

      String moduleId = null;

      if (modulePath.startsWith(".") || modulePath.startsWith("/")) {
        Path moduleFile = currentFile.getParent().resolve(modulePath).normalize();
        if (modulePath.endsWith("/")
            || isDirectory(moduleFile)
                && !modulePath.endsWith(".js")
                && !Files.exists(moduleFile.resolveSibling(moduleFile.getFileName() + ".js"))) {
          moduleFile = moduleFile.resolve("index.js");
        }
        moduleId = NODE.newId(moduleFile).getOriginalName();

      } else if (nodeLibrary.canRequireId(modulePath)) {
        moduleId = nodeLibrary.normalizeRequireId(modulePath);
      }

      if (moduleId != null) {
        // Only register the require statement on this module if it occurs at the global
        // scope. Assume other require statements are not declared at the global scope to
        // avoid create a circular dependency. While node can handle these, by returning
        // a partial definition of the required module, the cycle would be an error for
        // the compiler. For more information on how Node handles cycles, see:
        //     http://www.nodejs.org/api/modules.html#modules_cycles
        if (t.getScope().isGlobal()) {
          Node googRequire = call(getprop(name("goog"), string("require")), string(moduleId));

          // ClosureCheckModule enforces that goog.require statements are at the top level. To
          // compensate, if we have a require statement that is not at the top level, we introduce
          // a hidden variable at the top level that does the actual require. The compiler should
          // always inline the require making this effectively a no-op.
          //
          // Example:
          //    var Foo = require('./foo').Foo;
          //
          // Becomes:
          //    var _some_hidden_name = require('./foo');
          //    var Foo = _some_hidden_name.Foo;
          if (!parent.isName()) {
            String hiddenName = Types.toInternalVar(moduleId);

            JSDocInfoBuilder infoBuilder = new JSDocInfoBuilder(false);
            infoBuilder.recordConstancy();

            googRequireExpr.put(
                hiddenName, var(name(hiddenName).setJSDocInfo(infoBuilder.build()), googRequire));
            googRequire = name(hiddenName);
          }

          parent.replaceChild(require, googRequire.srcrefTree(require));
          t.getInput().addRequire(moduleId);

        } else {
          // For goog.module('foo'), ClosureRewriteModule produces module$exports$foo = {};, so
          // we use the transformed name in the direct reference.
          parent.replaceChild(require, name("module$exports$" + moduleId).srcrefTree(require));
        }

        t.reportCodeChange();
      }

      // Else we have an unrecognized module ID. Do nothing, leaving it to the
      // type-checking gods.
    }
  }

  /**
   * Rewrites all compound require statements into multiple statements to aid type checking.
   *
   * <p>That is, splits
   *
   * <p>var a = require('a'), b = require('b');
   *
   * <p>into
   *
   * <p>var a = require('a'); var b = require('b');
   */
  private final class SplitRequireDeclarations implements NodeTraversal.Callback {
    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, final Node n, final Node parent) {
      if (NodeUtil.isNameDeclaration(n)) {
        RequireDetector detector = new RequireDetector();
        traverseEs6(t.getCompiler(), n, detector);

        if (detector.foundRequire) {
          Node addAfter = n;
          for (Node last = n.getLastChild();
              last != null && last != n.getFirstChild();
              last = n.getLastChild()) {
            n.removeChild(last);

            Node newDecl = declaration(last, n.getToken()).srcrefTree(last);
            parent.addChildAfter(newDecl, addAfter);
            addAfter = newDecl;
            t.reportCodeChange();
          }
        }
      }
    }
  }

  private static final class RequireDetector implements NodeTraversal.Callback {
    private boolean foundRequire = false;

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return !foundRequire;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      foundRequire = foundRequire || isCall(n, "require") || isCall(n, "goog.require");
    }
  }

  private class TypeCleanup extends NodeTraversal.AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo info = n.getJSDocInfo();
      if (info != null) {
        for (Node node : info.getTypeNodes()) {
          fixTypeNode(t, node);
        }
      }
    }

    private void fixTypeNode(NodeTraversal t, Node typeNode) {
      if (typeNode.isString()) {
        typeNode.putProp(Node.ORIGINALNAME_PROP, typeNode.getString());

        if (typeNode.getString().startsWith("./") || typeNode.getString().startsWith("../")) {
          Path currentFile = inputFs.getPath(t.getSourceName());
          String newName = resolveModuleTypeReference(currentFile, typeNode.getString());
          typeNode.setString(newName);

        } else if (typeNode.getString().startsWith("exports.")) {
          String newName = currentModule + typeNode.getString().substring("exports".length());
          typeNode.setString(newName);
        }
      }

      for (Node child = typeNode.getFirstChild(); child != null; child = child.getNext()) {
        fixTypeNode(t, child);
      }
    }
  }

  /**
   * Attempts to resolve a type name that contains a relative path to a type exported by another
   * module (e.g. "./foo/bar.Baz" refers to "Baz" exported by the module at "./foo/bar"). This
   * method <em>does not</em> verify that the referenced type is actually defined.
   *
   * @param referencePath path of the module to resolve the type name relative to.
   * @param relativePath the type name containing a relative path.
   * @return the resolved type name, or the original {@code relativePath} if it could not be
   *     resolved.
   */
  @VisibleForTesting
  static String resolveModuleTypeReference(Path referencePath, String relativePath) {
    checkArgument(
        relativePath.startsWith("./") || relativePath.startsWith("../"),
        "Relative path must start with ./ or ../ (%s)",
        relativePath);

    // First check if the path resolves to a module.
    Optional<Path> path = maybeResolvePath(referencePath, relativePath);
    if (path.isPresent()) {
      return getModuleId(path.get());
    }

    // Otherwise, check if the path resolves to a module's exported type.
    int index = relativePath.lastIndexOf('/');
    if (index != -1 && relativePath.lastIndexOf('.') > index) {
      String dirPath = relativePath.substring(0, index + 1);
      String name = relativePath.substring(index + 1);

      index = name.indexOf('.');
      if (index == -1 || index == name.length() - 1) {
        return relativePath; // throw AssertionError?
      }

      String exportedType = name.substring(index + 1);
      name = name.substring(0, index);
      while (true) {
        path = maybeResolvePath(referencePath, dirPath + name);
        if (path.isPresent()) {
          return getModuleId(path.get()) + "." + exportedType;
        }

        index = exportedType.indexOf('.');
        if (index == -1) {
          break;
        }
        name += "." + exportedType.substring(0, index);
        exportedType = exportedType.substring(index + 1);
      }
    }

    return relativePath;
  }

  private static Optional<Path> maybeResolvePath(Path reference, String pathStr) {
    // 1. Path resolves to another module exactly.
    Path path = reference.resolveSibling(pathStr + ".js");
    if (exists(path)) {
      return Optional.of(path);
    }

    // 2. Path resolves to a directory with an index.js file.
    path = reference.resolveSibling(pathStr);
    if (isDirectory(path) && exists(path.resolve("index.js"))) {
      return Optional.of(path.resolve("index"));
    }

    return Optional.empty();
  }

  private static boolean isCall(Node n, String name) {
    return Nodes.isCall(n, name) && n.getSecondChild() != null && n.getSecondChild().isString();
  }
}
