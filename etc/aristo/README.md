The following was used to create the Turk CSV, and then the training data from
that.
All of these should be run from the project root.
Also, note that the pre-turk pipeline is basically guaranteed to not work unless
you're me running on one of my machines.

  1. __pipeline_preturk.sh__: This runs the requisite queries from the input 
     sentences, and generates the turk CSV. The CSV is generated into
     `turk_30_input.X.csv`, which should be renamed to the appropriate versioning.

  2. __pipeline_postturk.sh__: This runs the processing on 
     sentences, and generates the turk CSV.
     This produces a file `turk_90_trainset.tab`, which corresponds to the training
     set to be used for the classifier.

