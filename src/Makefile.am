SUBDIRS = fnv knheap
bin_PROGRAMS=hash_tree write_kb naturalli
EXTRA_DIST =  edu

clean-local:
	rm -f *.gcno *.gcda
	rm -f naturalli.war

../etc/vocab.tab.gz:
	${MAKE} -C .. etc/vocab.tab.gz

naturalli_preprocess.jar: $(wildcard edu/stanford/nlp/naturalli/*.java)
	@echo "Ensuring models..."
	${MAKE} -C .. etc/.have_models
	@echo "Compiling (${JDK_HOME}/bin/javac)..."
	@${JDK_HOME}/bin/javac -Xlint:unchecked -d . -cp ${CP} `find . -name "*.java"`
	@echo "Making Jar..."
	${JDK_HOME}/bin/jar cf naturalli_preprocess.jar -C . .
	bash -c 'for file in `find . -name "*.class"`; do rm -f $$file; done'

Models.h: ../rc ../etc/depInsert2rel.tab ../etc/vocab.tab.gz Makefile.am
	echo "// DO NOT MODIFY THIS FILE!" > Models.h
	echo "// It was automatically generated from the Makefile." >> Models.h
	echo "// You can change it either in src/Makefile.am, or in the" >> Models.h
	echo "// scripts in rc." >> Models.h
	echo "" >> Models.h
	echo "#ifndef MODELS_H" >> Models.h
	echo "#define MODELS_H" >> Models.h
	echo "" >> Models.h
	echo "#include <stdint.h>" >> Models.h
	echo "#include <string>" >> Models.h
	echo "" >> Models.h
	echo "#include <config.h>" >> Models.h
	echo '#include "Types.h"' >> Models.h
	echo "" >> Models.h
	source ../rc && cd .. && dep2header >> src/Models.h
	echo "" >> Models.h
	echo '/**' >> Models.h
	echo ' * Index a dependency arc to an integer.' >> Models.h
	echo ' */' >> Models.h
	echo 'uint8_t indexDependency(const std::string& dependencyAsString);' >> Models.h
	echo '' >> Models.h
	source ../rc && cd .. && edgeTypes >> src/Models.h
	echo '' >> Models.h
	source ../rc && cd .. && dummyVocab >> src/Models.h
	echo '' >> Models.h
	echo '/**' >> Models.h
	echo ' * Get the String gloss for an indexed dependency arc.' >> Models.h
	echo ' */' >> Models.h
	echo 'std::string dependencyGloss(const uint8_t& indexed);' >> Models.h
	echo '/**' >> Models.h
	echo ' * Characterize a quantifier (as an indexed word) into its monotonicities ' >> Models.h
	echo ' */' >> Models.h
	echo "void characterizeQuantifier(word w, quantifier_type* subjType, quantifier_type* objType, monotonicity* subjMono, monotonicity* objMono);" >> Models.h
	echo "#endif" >> Models.h

Models.cc: ../rc ../etc/depInsert2rel.tab ../etc/vocab.tab.gz Makefile.am
	echo "// DO NOT MODIFY THIS FILE!" > Models.cc
	echo "// It was automatically generated from the Makefile." >> Models.cc
	echo "// You can change it either in src/Makefile.am, or in the" >> Models.cc
	echo "// scripts in rc." >> Models.cc
	echo '#include "Models.h"' >> Models.cc
	echo '' >> Models.cc
	echo '#include <algorithm>' >> Models.cc
	echo '#include "btree_map.h"' >> Models.cc
	echo '' >> Models.cc
	echo 'using namespace std;' >> Models.cc
	echo 'using namespace btree;' >> Models.cc
	echo '' >> Models.cc
	source ../rc && cd .. && dep2string >> src/Models.cc
	echo "" >> Models.cc
	source ../rc && cd .. && depInsert2relC >> src/Models.cc
	echo "" >> Models.cc
	bash -c 'source ../rc && cd .. && characterizeQuantifiers >> src/Models.cc'

BUILT_SOURCES = Models.h Models.cc

naturalli_SOURCES = GZip.cc Models.cc FactDB.cc \
										NaturalLI.cc Utils.cc Graph.cc SynSearch.cc \
                 		SynSearchSingleThreaded.cc JavaBridge.cc \
                 		Graph.h Utils.h Types.h  SynSearch.h \
										JavaBridge.h GZip.h Models.h FactDB.h \
                 		btree.h btree_container.h btree_map.h btree_set.h
naturalli_DEPENDENCIES =	naturalli_preprocess.jar


naturalli_CXXFLAGS=-std=c++0x -pthread ${OPENMP_CFLAGS}
naturalli_LDADD=-Lfnv -lfnv32 -lfnv64 -Lknheap -lknheap

if HAVE_TCMALLOC
  naturalli_LDADD += -ltcmalloc
if DEBUG
  naturalli_LDADD += -lprofiler
if IS_GCC
  naturalli_CXXFLAGS += -fprofile-arcs -ftest-coverage -fPIC
  naturalli_LDADD += -lgcov
endif
endif
endif

hash_tree_SOURCES = HashTree.cc Utils.cc Graph.cc SynSearch.cc \
                    Models.cc GZip.cc \
                 		Graph.h Utils.h Types.h  SynSearch.h GZip.h Models.h \
                 		btree.h btree_container.h btree_map.h btree_set.h

hash_tree_CXXFLAGS=-std=c++0x -pthread ${OPENMP_CFLAGS}
hash_tree_LDADD=-Lfnv -lfnv32 -lfnv64 -Lknheap -lknheap

write_kb_SOURCES = FactDB.h FactDB.cc WriteKB.cc \
                   btree.h btree_container.h btree_map.h btree_set.h
write_kb_CXXFLAGS=-std=c++0x
write_kb_LDADD=

naturalli.war: naturalli_preprocess.jar
	@echo "Ensuring models..."
	${MAKE} -C .. etc/.have_models
	@echo "Creating WAR..."
	cd ../ && ant war
