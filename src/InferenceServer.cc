#include "PopulateRamCloud.h"

int main(int argc, char** argv) {
  if (!PopulateRamCloud()) {
    return 1;
  }
}
