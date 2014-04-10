package edu.stanford.nlp.natlog;

import edu.stanford.nlp.trees.Tree;

/**
 * TODO(gabor) JavaDoc
 *
 * @author Gabor Angeli
 */
public interface Mono {
  public Monotonicity[] annotate(Tree tree);
}
