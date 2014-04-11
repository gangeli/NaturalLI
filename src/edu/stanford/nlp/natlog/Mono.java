package edu.stanford.nlp.natlog;

import edu.stanford.nlp.trees.Tree;

/**
 * An interface for making the monotonicities on a sentence.
 *
 * @author Gabor Angeli
 */
public interface Mono {
  public Monotonicity[] annotate(Tree tree);
}
