# Files in this directory Jun 21, 1999:
knheap.C/h: sequence heaps
heap2.h: fast binary heaps
heap4.h: aligned 4-ary heaps
heap-CLR.h: textbook-like Binary heap
knupdown3.C: program used for the measurements
  of the sequence 
  (insert deleteMin insert)^N(deleteMin insert deleteMin)^N
  uncomment one of KNH, H2, H4, or HSLOW to try
  one of the above four queue algorithms
knwiggle.C: program used for the measurements of the sequence
  (insert (insert delete)^k)^N (delete (insert delete)^k)^N
multiMergeUnrolled.C: unrolled mulit-way merging
  using loser trees. Included by knheap.C
util.h basic uttilities included by all codes

compilation is simply done with g++ or the native compiler.
No make file needed, e.g.,
"g++ knupdown3.C"
