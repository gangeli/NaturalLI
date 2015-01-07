#ifndef JAVA_BRIDGE_H
#define JAVA_BRIDGE_H

#include <mutex>
#include <vector>
#include <unistd.h>

#include "SynSearch.h"

/**
 * The interface for the Java pre-processor, responsible for
 * annotating the raw text of a sentence into a dependency parse, already
 * indexed and marked with quantifiers and quantifier properties.
 */
class JavaBridge {
 public:
  /**
   * Create a bidirectional pipe to the Java program, and start the program
   */
  JavaBridge();

  /**
   * Kill the subprocess program and close the pipes.
   */
  ~JavaBridge();

  /**
   * Annotate a given sentence into a list of tree objects, 
   * either a singleton (if mode == 'Q'), a list (if mode == 'P'),
   * or no results if there was an error (e.g., the sentence is too
   * long.
   */
  const std::vector<Tree*> annotate(const char* sentence, char mode);
  
  /**
   * Annotate the sentence as a premise (i.e., have multiple outputs).
   */
  const std::vector<Tree*> annotatePremise(const char* sentence) {
    return annotate(sentence, 'P');
  }
  
  /**
   * Annotate the sentence as a query (i.e., have one output)
   */
  const Tree* annotateQuery(const char* sentence) {
    std::vector<Tree*> outputs = annotate(sentence, 'Q');
    if (outputs.empty()) {
      return NULL;
    } else {
      return outputs[0];
    }
  }

 private:
  /** The file descriptor of stdin of the child process */
  int childIn;
  /** The file descriptor of stdout of the child process */
  int childOut;
  /** The process id of the child process */
  pid_t pid;
  /** A lock, to make sure we're not making concurrent calls to the annotator */
  std::mutex lock;
};

#endif
