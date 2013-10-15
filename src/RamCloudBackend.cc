#include <stdio.h>
#include <stdlib.h>

#include "Buffer.h"

#include "RamCloudBackend.h"
#include "Postgres.h"

const char* ramcloudConnectionString() {
  char* conn = (char*) malloc(128 * sizeof(char));
  snprintf(conn, 127, "fast+udp:host=%s,port=%d",
           RAMCLOUD_HOST.c_str(), RAMCLOUD_PORT);
  printf("connecting to %s\n", conn);
  return conn;
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
  const uint16_t chunkSize = 1000;

  // Create a Postgres connection
  char factQuery[127];
  snprintf(factQuery, 127, "SELECT left_arg, rel, right_arg, weight FROM %s LIMIT 10000000;", PG_TABLE_FACT.c_str());
  PGIterator iter = PGIterator(factQuery);
  uint64_t totalRead = 0;

  while (iter.hasNext()) {
    RAMCloud::MultiWriteObject requestObjects[chunkSize];
    RAMCloud::MultiWriteObject* requests[chunkSize];
    float confidences[chunkSize];
    uint32_t numRequests = 0;
    for (uint16_t i = 0; i < chunkSize; ++i) {
      // If no more to write, break;
      if (!iter.hasNext()) { break; }

      // Get data from Postgres
      PGRow row = iter.next();
      word fact[256];
      uint8_t factLength = 0;
      for (int k = 0; k < 3; ++k) {  // for each argument...
        if (row[k] == NULL) { continue; }
        // ...read it as a string
        uint32_t stringLength = strlen(row[k]);
        char stringWithoutBrackets[stringLength - 2 + 1];
        snprintf(stringWithoutBrackets, stringLength - 2, "%s", &row[k][1]);

        // ...tokenize it
        const char* token = strtok(stringWithoutBrackets, ",");
        while (token != NULL) {
          char* end;
          fact[factLength] = strtol(token, &end, 10);
          if (end == token || *end != '\0') {
            printf("Could not convert string to integer: %s\n (end is '%c')",
                   token, *end);
            std::exit(1);
          }
          factLength += 1;  // append the word to the buffer
          token = strtok(NULL, ",");
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
      totalRead += 1;
    }
    // Write request
    ramcloud.multiWrite(requests, numRequests);
    if (totalRead % 1000000 == 0) {
      printf("loaded %luM facts\n", totalRead / 1000000);
    }
  }

}
  
const bool RamCloudFactDB::contains(const word* key, const uint8_t wordLength) {
  RAMCloud::Tub<RAMCloud::Buffer> buffer;
  RAMCloud::MultiReadObject* requests[1];
  RAMCloud::MultiReadObject requestObject(factTableId, key, uint16_t(wordLength), &buffer);
  requests[0] = &requestObject;

  ramcloud.multiRead(requests, 1);

  if (statusToSymbol(requests[0]->status) == "STATUS_OBJECT_DOESNT_EXIST")
    return false;
  else
    return true;
}

// Clean up 
RamCloudFactDB::~RamCloudFactDB() {
//  dropTable(factTableName);  // TODO(gabor) drop me somewhere!
}

FactDB* ReadRamCloudFactDB() {
  printf("Reading facts...\n");
  return new RamCloudFactDB();
}