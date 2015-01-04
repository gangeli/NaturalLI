#!/bin/bash
#

echo ">>> C++"
cat etc/depInsert2rel.tab |\
  awk -F'	' '{ print toupper($1) "\t" toupper($2) "\t" $3 }' |\
  sed -r -e 's/^([^\t]+)\t([^\t]+)\t(.*)$/    case DEP_\1: return FUNCTION_\2;  \/\/  \3/g'
echo "<<< C++"
echo ">>> Java (insertions)"
cat etc/depInsert2rel.tab |\
  awk -F'	' '{ print $1 "\t" toupper($2) "\t" $3 }' |\
  sed -r -e 's/^([^\t]+)\t([^\t]+)\t(.*)$/    put("\1", NaturalLogicRelation.\2);  \/\/ \3/g'
echo "<<< Java (insertions)"
