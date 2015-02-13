#include "GZip.h"

#include <cstdlib>
#include <assert.h>
#include <sstream>
  
using namespace std;

//
// zfatal()
//
void zfatal(int ret) {
  fputs("GZip.cc: ", stderr);
  switch (ret) {
    case Z_ERRNO:
      if (ferror(stdin))
        fputs("error reading stdin\n", stderr);
      if (ferror(stdout))
        fputs("error writing stdout\n", stderr);
      std::exit(1);
      break;
    case Z_STREAM_ERROR:
      fputs("invalid compression level\n", stderr);
      std::exit(1);
      break;
    case Z_DATA_ERROR:
      fputs("invalid or incomplete deflate data\n", stderr);
      std::exit(1);
      break;
    case Z_MEM_ERROR:
      fputs("out of memory\n", stderr);
      std::exit(1);
      break;
    case Z_VERSION_ERROR:
      fputs("zlib version mismatch!\n", stderr);
      std::exit(1);
  }
}
  
//
// GZIterator::GZIterator
//
GZIterator::GZIterator() : bufferSize(0), source(NULL), lastLine(NULL) { }

//
// GZIterator::GZIterator
//
GZIterator::GZIterator(const char* filename, const uint64_t& bufferSize)
    : source(fopen(filename, "r")), bufferSize(bufferSize), lastLine(NULL) {
  // Error check
  if (source == NULL) {
    fprintf(stderr, "Could not find file: %s\n", filename);
    exit(1);
  }
  // Initialize the buffers
  this->in = (uint8_t*) malloc(bufferSize * sizeof(uint8_t));
  this->out = (uint8_t*) malloc(bufferSize * sizeof(uint8_t));
  // Initialize the stream
  this->strm.zalloc = Z_NULL;
  this->strm.zfree = Z_NULL;
  this->strm.opaque = Z_NULL;
  this->strm.avail_in = 0;
  this->strm.next_in = Z_NULL;
  this->strm.avail_out = 0;
  this->strm.next_out = Z_NULL;
  this->status = inflateInit2(&(this->strm), 16+MAX_WBITS);
  this->outConsumed = 0;
  this->outHave = 0;
  // Make sure we're ok
  if (this->status != Z_OK) {
    zfatal(this->status);
  }
  // Prime the iterator
  fillOut();
}

//
// GZIterator::~GZIterator
//
GZIterator::~GZIterator() {
  if (source != NULL) {
    fclose(source);
    free(this->in);
    free(this->out);
  }
}

//
// GZIterator::fillIn
//
bool GZIterator::fillIn() {
  strm.avail_in = fread(in, sizeof(uint8_t), bufferSize, source);
  if (ferror(source)) {
    (void)inflateEnd(&strm);
    zfatal(Z_ERRNO);
  }
  if (strm.avail_in == 0) {
    return false;
  }
  strm.next_in = in;
  return true;
}

//
// GZIterator::fillOut
//
bool GZIterator::fillOut() {
  // Read from the input
  strm.avail_out = bufferSize;
  strm.next_out = out;
  status = inflate(&strm, Z_NO_FLUSH);
  assert(status != Z_STREAM_ERROR);  /* state not clobbered */
  switch (status) {
    case Z_NEED_DICT:
        status = Z_DATA_ERROR;     /* and fall through */
    case Z_DATA_ERROR:
    case Z_MEM_ERROR:
    case Z_STREAM_ERROR:
        (void) inflateEnd(&strm);
        zfatal(status);
  }
  this->outHave = bufferSize - strm.avail_out;
  this->outConsumed = 0;
  // Return
  if (this->outHave != 0) {
    // Case: something was read.
    return true;
  } else {
    // Case: have to read more input
    if (!fillIn()) {
      return false;
    }
    return fillOut();
  }
}

//
// GZIterator::hasNext
//
bool GZIterator::hasNext() {
  // Shortcuts
  if (source == NULL) {
    std::exit(1);
  }
  if (this->lastLine != NULL) {
    return true;
  }
  if (this->status == Z_STREAM_END &&
      this->outConsumed >= this->outHave) {
    return false;
  }

  // Create line
  this->lastLine = (char*) malloc(bufferSize * sizeof(char));
  uint32_t lineIndex = 0;
  while (out[this->outConsumed] != '\n') {  // assumption: we always start with output
    // Set character
    this->lastLine[lineIndex] = out[this->outConsumed];
    // Update indices
    lineIndex += 1;
    this->outConsumed += 1;
    // Check if we have more output
    if (this->outConsumed >= this->outHave) {
      bool canFillOut = fillOut();
      if (!canFillOut) {
        // Base case: no more input to read
        this->lastLine[lineIndex] = '\0';
        return true;
      }
      this->outConsumed = 0;
    }
  }
  this->lastLine[lineIndex] = '\0';

  // Skip newline
  this->outConsumed += 1;
  if (this->outConsumed >= this->outHave) {
    fillOut();
  }

  // Return success
  return true;
}
  
//
// GZRow::GZRow
//
GZRow::GZRow(const std::vector<std::string>& elems) : elems(elems) { }

//
// GZRow::GZRow
//
GZRow::GZRow(const char** elems, const uint32_t& size) { 
  for (uint32_t i = 0; i < size; ++i) {
    this->elems.push_back(std::string(elems[i]));
  }
}

//
// GZRow::GZRow
//
GZRow::GZRow(const GZRow& other) : elems(other.elems) { } 
  
//
// GZRow::operator[]
//
const char* GZRow::operator[] (const uint64_t& index) {
  return elems[index].c_str();
}

//
// GZIterator::next
//
GZRow GZIterator::next() {
  // Prime the element
  if (!hasNext()) {
    fprintf(stderr, "Called next() on GZIterator with no elements remaining!\n");
    exit(1);
  }
  // Split the string
  istringstream iss(this->lastLine);
  string s;
  vector<string> elems;
  while ( getline( iss, s, '\t' ) ) {
    elems.push_back(s);
  }
  // Return
  free(this->lastLine);
  this->lastLine = NULL;
  return GZRow(elems);
}
