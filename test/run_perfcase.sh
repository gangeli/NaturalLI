#!/bin/bash
#
MYDIR=`dirname $0`
OUT=`mktemp`

make -C "$MYDIR/../" all
if [ $? != 0 ]; then exit 1; fi
make -C "$MYDIR/../" src/naturalli_preprocess.jar
if [ $? != 0 ]; then exit 1; fi

time cat "$1" | $MYDIR/../src/naturalli > $OUT
STATUS=$?
echo ""
echo "^^^ INFERENCE"
echo ""
echo "vvv RESULTS"
echo ""

echo "Examples run:"
cat $OUT | wc | awk '{ print $1 }'
echo "Examples failed:"
cat $OUT | egrep "^FAIL" | wc | awk '{ print $1 }'
echo "Failures:"
export GREP_COLOR="1;31"
if [ $STATUS != 0 ]; then
  echo ""
  cat $OUT | egrep "^FAIL"
else
  echo "(none)"
fi
echo ""

rm $OUT
exit $STATUS
