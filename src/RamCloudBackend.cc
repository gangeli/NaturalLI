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
