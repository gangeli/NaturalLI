#include "Types.h"
#include "Models.h"

float priority[NUM_MUTATION_TYPES];
bool priorityInititalized = false;

//
// Set the priorities for various edge types (for edge::<)
//
void initPriority() {
  for (uint8_t i = 0; i < NUM_MUTATION_TYPES; ++i) {
    priority[i] = 0.0f;
  }
  priority[ANTONYM] = 10.0f;
  priority[HOLONYM] = 50.0f;
  priority[HYPERNYM] = 20.0f;
  priority[HYPONYM] = 30.0f;
  priority[MERONYM] = 40.0f;
  priority[NN] = 120.0f;
  priority[QUANTUP] = 60.0f;
  priority[QUANTDOWN] = 70.0f;
  priority[QUANTREWORD] = 80.0f;
  priority[SIMILAR] = 90.0f;
  priority[SYNONYM] = 100.0f;
  priority[VENTAIL] = 110.0f;
  priorityInititalized = true;
}


//
// edge::<
//
bool edge::operator<(const edge& other) const {
  if (!priorityInititalized) {
    initPriority();
  }
  return priority[this->type] + this->cost < priority[other.type] + other.cost;
}
