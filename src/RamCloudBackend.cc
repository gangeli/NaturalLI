#include <stdio.h>
#include <stdlib.h>

#include "Buffer.h"

#include "RamCloudBackend.h"
#include "Postgres.h"

char* ramcloudConnectionString() {
  char* conn = (char*) malloc(128 * sizeof(char));
  snprintf(conn, 127, "fast+udp: host=%s, port=%d",
           RAMCLOUD_HOST.c_str(), RAMCLOUD_PORT);
  return conn;
}

RamCloudGraph::RamCloudGraph() 
    : ramcloud(ramcloudConnectionString()),
      wordIndexerTable(ramcloud.createTable("word_indexer")),
      edgesTable(ramcloud.createTable("edges")) { }

RamCloudGraph::RamCloudGraph(const Graph& copy) 
    : ramcloud(ramcloudConnectionString()),
      wordIndexerTable(ramcloud.createTable("word_indexer")),
      edgesTable(ramcloud.createTable("edges")) {

  vector<word> keys = copy.keys();
  // Copy Graph
  RAMCloud::MultiWriteObject* indexerRequests[keys.size()];
  RAMCloud::MultiWriteObject* edgesRequests[keys.size()];
  for (int i = 0; i < keys.size(); ++i) {
    word key = keys[i];
    string gloss = copy.gloss(key);
    vector<edge> edges = copy.outgoingEdges(key);
    // Indexer
    indexerRequests[i] = new RAMCloud::MultiWriteObject(
          wordIndexerTable, &key, sizeof(word),
          gloss.c_str(), gloss.size()
        );
    // Edges
    uint32_t blockSize = (sizeof(word) + sizeof(edge_type) + sizeof(float));
    uint8_t* edgesBlob = (uint8_t*) malloc( keys.size() * blockSize );
    for (int k = 0; k < edges.size(); ++k) {
      *(edgesBlob + (k * blockSize + 0)) = edges[k].sink;
      *(edgesBlob + (k * blockSize + sizeof(word))) = edges[k].type;
      *(edgesBlob + (k * blockSize + sizeof(word) + sizeof(edge_type)))
          = edges[k].cost;
    }
    indexerRequests[i] = new RAMCloud::MultiWriteObject(
          wordIndexerTable, &key, sizeof(word),
          edgesBlob, keys.size() * blockSize
        );
  }
  // Commit to RamCloud
  ramcloud.multiWrite(indexerRequests, keys.size());
  ramcloud.multiWrite(edgesRequests, keys.size());
}


RamCloudGraph::~RamCloudGraph() {
  ramcloud.dropTable("word_indexer");
  ramcloud.dropTable("edges");
}

// TODO(gabor) I haven't even made an attempt at making this yet
const vector<edge>& RamCloudGraph::outgoingEdges(word source) const {
  return vector<edge>();
}

// TODO(gabor) Finish me (commented line complains about const)
const string RamCloudGraph::gloss(word id) const {
  RAMCloud::Buffer buffer;
//  ramcloud.read(wordIndexerTable, &id, sizeof(word), &buffer);
  char gloss[buffer.getTotalLength()];
  buffer.copy(0, buffer.getTotalLength(), &gloss);
  return string(gloss);
}

// TODO(gabor) in theory this would be nice to implement, but I can't forsee
//             anyone actually using it in the near future...
const vector<word> RamCloudGraph::keys() const {
  printf("FATAL: keys() not implemented for RamCloudGraph");
  std::exit(1);
}


Graph* ReadRamCloudGraph() {
  // Read from Postgres
  Graph* inMemoryGraph = ReadGraph();
  Graph* ramcloud = new RamCloudGraph(*inMemoryGraph);
  delete inMemoryGraph;
  return ramcloud;
}

//
// RamCloudFactDB
//
  
// Construct the requisite tables for storing facts
// in RamCloud, if they don't already exist.
RamCloudFactDB::RamCloudFactDB()
    : ramcloud(ramcloudConnectionString()),
      factTableName("Facts")
    {

  factTableId = ramcloud.createTable(factTableName);

  // Create a Postgres connection
  char factQuery[127];
  snprintf(factQuery, 127, "SELECT left_arg, rel, right_arg, weight FROM %s LIMIT 10000000;", PG_TABLE_FACT.c_str());
  PGIterator iter = PGIterator(factQuery);

  while (iter.hasNext()) {
    RAMCloud::MultiWriteObject requestObjects[100];
    RAMCloud::MultiWriteObject* requests[100];
    float confidences[100];
    uint32_t numRequests;
    for (uint8_t i = 0; i++; i < 100) {
      // If no more to write, break;
      if (!iter.hasNext()) { break; }

      // Get data from Postgres
      PGRow row = iter.next();
      word fact[256];
      uint8_t factLength = 0;
      for (int i = 0; i < 3; ++i) {  // for each argument...
        // ...read it as a string
        uint32_t stringLength = strlen(row[i]);
        char stringWithoutBrackets[stringLength];
        memcpy( stringWithoutBrackets, &row[i][1], stringLength - 2 );
        // ...tokenize it
        const char* token = strtok(stringWithoutBrackets, ",");
        while (token != NULL) {
          fact[factLength] = atoi(token);
          factLength += 1;  // append the word to the buffer
        }
        // sanity check to prevent too long of a fact
        if (factLength >= 256) { break; }
      }
      confidences[i] = atof(row[3]);
      
      // Create request object
      requestObjects[i] =
        RAMCloud::MultiWriteObject(factTableId, fact, factLength,
          &confidences[i], sizeof(float));
      requests[i] = &requestObjects[i];
      numRequests += 1;
    }
    // Write request
    ramcloud.multiWrite(requests, numRequests);
  }

}
  
const bool RamCloudFactDB::contains(const word* key, const uint8_t wordLength) {
  RAMCloud::Buffer buffer;
  ramcloud.read(factTableId, key, wordLength, &buffer);
  if (buffer.getTotalLength() > 0)
    return true;
  else
    return false;
}

// Clean up 
RamCloudFactDB::~RamCloudFactDB() {
//  dropTable(factTableName);  // TODO(gabor) drop me somewhere!
}

FactDB* ReadRamCloudFactDB() {
  return new RamCloudFactDB();
}
