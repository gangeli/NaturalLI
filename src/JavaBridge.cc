#include "JavaBridge.h"

#include <iostream>
#include <iomanip>
#include <signal.h>
#include <sys/prctl.h>

#include "config.h"

using namespace std;

//
// JavaBridge::JavaBridge
//
JavaBridge::JavaBridge() {
  // Set up subprocess
  pid_t pid;
  int pipeIn[2];
  int pipeOut[2];
  pipe(pipeIn);
  pipe(pipeOut);
  pid = fork();
  if (pid == 0) {

    // --Child is running here--
    ::prctl(PR_SET_PDEATHSIG, SIGKILL);

    // Get directory of running executable
    char thisPathBuffer[256];
    memset(thisPathBuffer, 0, sizeof(thisPathBuffer));
    readlink("/proc/self/exe", thisPathBuffer, 255);
    string thisPath = string(thisPathBuffer);
    int lastSlash = thisPath.find_last_of("/");
    string thisDir = thisPath.substr(0, lastSlash);

    // Set up paths
    // (find java)
    char javaExecutable[128];
    snprintf(javaExecutable, 127, "%s/bin/java", JDK_HOME);
    // (the main class)
    std::string javaClass = "edu.stanford.nlp.naturalli.CBridge";
    // (environment variables)
    char wordnetEnv[256];
    snprintf(wordnetEnv, 255, "-Dwordnet.database.dir=%s", WORDNET_DICT);
    char vocabFile[256];
    snprintf(vocabFile, 255, "-DVOCAB_FILE=%s", VOCAB_FILE);
    char senseFile[256];
    snprintf(senseFile, 255, "-DSENSE_FILE=%s", SENSE_FILE);
    // (classpath)
    char classpath[1024];
    snprintf(classpath, 1024, "%s/naturalli_preprocess.jar:%s/jaws.jar:%s/../lib/jaws.jar:%s:%s", 
        thisDir.c_str(), thisDir.c_str(), thisDir.c_str(),
        CORENLP, CORENLP_MODELS);

    // Start program
    dup2(pipeIn[0], STDIN_FILENO);
    dup2(pipeOut[1], STDOUT_FILENO);
    execl(javaExecutable, javaExecutable, "-mx2g", 
        wordnetEnv, vocabFile, senseFile,
        "-cp", classpath,
        javaClass.c_str(), "true", (char*) NULL);

    // Should never reach here
    exit(1);

  } else {
    // --Parent is running here--
    this->childIn = pipeIn[1];
    this->childOut = pipeOut[0];
    this->pid = pid;
  }
}
  
//
// JavaBridge::~JavaBridge
//
JavaBridge::~JavaBridge() {
  close(childIn);
  close(childOut);
  kill(this->pid, SIGTERM);
}

//
// JavaBridge::annotate
//
const vector<Tree*> JavaBridge::annotate(const char* sentence, char mode) const {
  // Write sentence
  int sentenceLength = strlen(sentence);
  const_cast<JavaBridge*>(this)->lock.lock();
  write(this->childIn, &mode, sizeof(char));  // write the directive
  if (sentence[sentenceLength - 1] != '\n') {
    char buffer[sentenceLength + 2];
    memcpy(buffer, sentence, sentenceLength * sizeof(char));
    buffer[sentenceLength] = '\n';
    buffer[sentenceLength + 1] = '\0';
    write(this->childIn, buffer, strlen(buffer));
  } else {
    write(this->childIn, sentence, strlen(sentence));
  }

  // Read trees
  vector<Tree*> trees;
  int numNewlines = 0;
  string conll = "";
  char c;
  uint32_t numLines = 0;
  while (numNewlines < 3) {
    // Read within the line
    if (read(this->childOut, &c, 1) > 0) {
      conll.append(&c, 1);
      if (c == '\n') {
        numLines += 1;
        numNewlines += 1;
      } else {
        numNewlines = 0;
      }
    }
    // Handle a double newline
    if (numNewlines == 2) {
      if (numLines < MAX_FACT_LENGTH) {
        trees.push_back(new Tree(conll));
      }
      conll = "";
      numLines = 0;
    }
  }
  const_cast<JavaBridge*>(this)->lock.unlock();
  return trees;
}
