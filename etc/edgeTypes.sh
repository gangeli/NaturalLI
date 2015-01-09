#!/bin/bash
#
# Print out the edge relation indices in a way that can be copied
# into c++ / java

define ANTONYM                   0

echo ">>> C++"
cat etc/edgeTypes.tab |\
  awk -F'	' '{ print "#define " toupper($2) "     " toupper($1) }'
echo "<<< C++"
