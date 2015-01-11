#ifndef GZIP_H
#define GZIP_H

#include <cstdio>
#include <string>
#include <vector>
#include <zlib.h>
#if defined(MSDOS) || defined(OS2) || defined(WIN32) || defined(__CYGWIN__)
#  include <fcntl.h>
#  include <io.h>
#  define SET_BINARY_MODE(file) setmode(fileno(file), O_BINARY)
#else
#  define SET_BINARY_MODE(file)
#endif

#include <config.h>

/**
 * Represents a single row of a database query result.
 */
class GZRow {
 public:
  GZRow(const std::vector<std::string>& elems);
  GZRow(const char** elems, const uint32_t& size);
  GZRow(const GZRow& other);
  
  virtual const char* operator[] (const uint64_t& index);

  inline uint32_t size() { return elems.size(); }

 private:
  std::vector<std::string> elems;
};

/**
 * An iterator for a particular query. 
 * Automatically manages the cursor, and creating + cleaning up
 * the connection and memory when constructed and deconstructed.
 */
class GZIterator {
 public:
  GZIterator(const char* filename, const uint64_t& bufferSize = (0x1l << 10));

  virtual ~GZIterator();

  virtual bool hasNext();
  virtual GZRow next();

 protected:
  /**
   * A dummy constructor which doesn't initialize the iterator at all,
   * but allows for other classes to subclass it
   */
  GZIterator();
 
 private:
  FILE* source;
  const uint64_t bufferSize;
  char* lastLine;
  
  // (internal variables for reading the stream)
  uint64_t have;
  z_stream strm;
  int32_t status;
  uint8_t* out;
  uint8_t* in;

  // (helper functions)
  bool fillIn();
  bool fillOut();

  uint32_t outConsumed;
  uint32_t outHave;
};

#endif
