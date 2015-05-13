
File Formats
------------

  * `graph.tab.gz` This is a TSV file, with columns:

        0  source word \in [0, |VOCAB|)
        1  source sense \in [0, 32)
        2  sink word \in [0, |VOCAB|)
        3  sink sense \in [0, |VOCAB|)
        4  edge type \in [0, |EDGE_TYPES|) -- 16 edge types currently
        5  cost \in [0, \infinity)
