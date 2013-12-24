package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * A specialized version of the {@link Compiler Closure Compiler} that preserves CommonJS module
 * structure so as to properly extract JSDoc info.
 */
public class DossierCompiler extends Compiler {

  private final ImmutableSet<Path> commonJsModules;
  private boolean hasParsed = false;

  /**
   * Creates a new compiler that reports errors and warnings to an output stream.
   *
   * @param stream the output stream.
   * @param commonJsModules the inputs that should be parsed as CommonJS modules.
   */
  public DossierCompiler(PrintStream stream, Iterable<Path> commonJsModules) {
    super(stream);
    this.commonJsModules = ImmutableSet.copyOf(commonJsModules);
  }

  @Override
  public void parse() {
    checkState(!hasParsed,
        "%s can only parse its inputs once! Create a new instance if you must re-parse",
        getClass());
    hasParsed = true;

    // First we transform the CommonJS modules. This ensures input sources are properly re-ordered
    // based on the goog.provide/require statements we generate for the modules. This is necessary
    // since the compiler does its final input ordering before invoking any custom passes
    // (otherwise, we could just process the modules as a custom pass).
    DossierProcessCommonJsModules cjs = new DossierProcessCommonJsModules(this, commonJsModules);
    // TODO(jleyba): processCommonJsModules(cjs, getExternsInOrder());
    processCommonJsModules(cjs, getInputsInOrder());

    // Now we can proceed with the normal parsing.
    super.parse();
  }

  private void processCommonJsModules(
      DossierProcessCommonJsModules compilerPass, Iterable<CompilerInput> inputs) {
    for (CompilerInput input : inputs) {
      Node root = input.getAstRoot(this);
      if (root == null) {
        continue;
      }
      compilerPass.process(null, root);
    }
  }
}