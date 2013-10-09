#ifndef FACT_DB_H
#define FACT_DB_H

#include "Config.h"

class FactDB {
 public:
  virtual const bool contains(const vector<word>&) const = 0;
};

FactDB* ReadFactDB();

#endif
