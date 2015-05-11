current_dir := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
root_dir := "${current_dir}/.."
etc := "${root_dir}/etc"

SUBDIRS = fnv knheap
bin_PROGRAMS=hash_tree write_kb naturalli_search naturalli
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
	echo "${root_dir}"
	echo "// DO NOT MODIFY THIS FILE!" > ${current_dir}/Models.h
	echo "// It was automatically generated from the Makefile." >> ${current_dir}/Models.h
	echo "// You can change it either in src/Makefile.am, or in the" >> ${current_dir}/Models.h
	echo "// scripts in rc." >> ${current_dir}/Models.h
	echo "" >> ${current_dir}/Models.h
	echo "#ifndef MODELS_H" >> ${current_dir}/Models.h
	echo "#define MODELS_H" >> ${current_dir}/Models.h
	echo "" >> ${current_dir}/Models.h
	echo "#include <stdint.h>" >> ${current_dir}/Models.h
	echo "#include <string>" >> ${current_dir}/Models.h
	echo "" >> ${current_dir}/Models.h
	echo "#include <config.h>" >> ${current_dir}/Models.h
	echo '#include "Types.h"' >> ${current_dir}/Models.h
	echo "" >> ${current_dir}/Models.h
	source ${root_dir}/rc && cd ${root_dir} && dep2header >> ${current_dir}/Models.h
	echo "" >> ${current_dir}/Models.h
	echo '/**' >> ${current_dir}/Models.h
	echo ' * Index a dependency arc to an integer.' >> ${current_dir}/Models.h
	echo ' */' >> ${current_dir}/Models.h
	echo 'uint8_t indexDependency(const std::string& dependencyAsString);' >> ${current_dir}/Models.h
	echo '' >> ${current_dir}/Models.h
	source ${root_dir}/rc && cd ${root_dir} && edgeTypes >> ${current_dir}/Models.h
	echo '' >> ${current_dir}/Models.h
	echo 'uint8_t indexEdgeType(const std::string& edgeAsString);' >> ${current_dir}/Models.h
	echo '' >> ${current_dir}/Models.h
	echo '' >> ${current_dir}/Models.h
	source ${root_dir}/rc && cd ${root_dir} && dummyVocab >> ${current_dir}/Models.h
	echo '' >> ${current_dir}/Models.h
	echo '/**' >> ${current_dir}/Models.h
	echo ' * Get the String gloss for an indexed dependency arc.' >> ${current_dir}/Models.h
	echo ' */' >> ${current_dir}/Models.h
	echo 'std::string dependencyGloss(const uint8_t& indexed);' >> ${current_dir}/Models.h
	echo '/**' >> ${current_dir}/Models.h
	echo ' * Characterize a quantifier (as an indexed word) into its monotonicities ' >> ${current_dir}/Models.h
	echo ' */' >> ${current_dir}/Models.h
	echo "void characterizeQuantifier(word w, quantifier_type* subjType, quantifier_type* objType, monotonicity* subjMono, monotonicity* objMono);" >> ${current_dir}/Models.h
	echo '/**' >> ${current_dir}/Models.h
	echo ' * A large value that we can safely assume will never be a known word.' >> ${current_dir}/Models.h
	echo ' */' >> ${current_dir}/Models.h
	echo "#define INVALID_WORD 33554431" >> ${current_dir}/Models.h
	echo "#endif" >> ${current_dir}/Models.h

Models.cc: ../rc ../etc/depInsert2rel.tab ../etc/vocab.tab.gz Makefile.am
	echo "// DO NOT MODIFY THIS FILE!" > ${current_dir}/Models.cc
	echo "// It was automatically generated from the Makefile." >> ${current_dir}/Models.cc
	echo "// You can change it either in src/Makefile.am, or in the" >> ${current_dir}/Models.cc
	echo "// scripts in rc." >> ${current_dir}/Models.cc
	echo '#include "Models.h"' >> ${current_dir}/Models.cc
	echo '' >> ${current_dir}/Models.cc
	echo '#include <algorithm>' >> ${current_dir}/Models.cc
	echo '#include "btree_map.h"' >> ${current_dir}/Models.cc
	echo '' >> ${current_dir}/Models.cc
	echo 'using namespace std;' >> ${current_dir}/Models.cc
	echo 'using namespace btree;' >> ${current_dir}/Models.cc
	echo '' >> ${current_dir}/Models.cc
	source ${root_dir}/rc && cd ${root_dir} && dep2string >> ${current_dir}/Models.cc
	echo "" >> ${current_dir}/Models.cc
	source ${root_dir}/rc && cd ${root_dir} && depInsert2relC >> ${current_dir}/Models.cc
	echo "" >> ${current_dir}/Models.cc
	source ${root_dir}/rc && cd ${root_dir} && edgeType2String >> ${current_dir}/Models.cc
	echo "" >> ${current_dir}/Models.cc
	bash -c 'source ${root_dir}/rc && cd ${root_dir} && characterizeQuantifiers >> ${current_dir}/Models.cc'

BUILT_SOURCES = Models.h Models.cc

naturalli_SOURCES = GZip.cc Models.cc FactDB.cc \
										NaturalLIIO.cc Utils.cc Graph.cc SynSearch.cc \
                 		SynSearchSingleThreaded.cc JavaBridge.cc \
                 		NaturalLIIO.h Graph.h Utils.h Types.h  SynSearch.h \
										JavaBridge.h GZip.h Models.h FactDB.h \
                 		btree.h btree_container.h btree_map.h btree_set.h \
									  NaturalLIStandalone.cc
naturalli_search_SOURCES = GZip.cc Models.cc FactDB.cc \
													 NaturalLIIO.cc Utils.cc Graph.cc SynSearch.cc \
                 					 SynSearchSingleThreaded.cc JavaBridge.cc \
                 					 NaturalLIIO.h Graph.h Utils.h Types.h  SynSearch.h \
													 GZip.h Models.h FactDB.h JavaBridge.h \
                 					 btree.h btree_container.h btree_map.h btree_set.h \
									         NaturalLISearch.cc
naturalli_DEPENDENCIES =	naturalli_preprocess.jar


naturalli_CXXFLAGS=-std=c++0x -pthread ${OPENMP_CFLAGS}
naturalli_search_CXXFLAGS=-std=c++0x -pthread ${OPENMP_CFLAGS}
naturalli_LDADD=-Lfnv -lfnv32 -lfnv64 -Lknheap -lknheap
naturalli_search_LDADD=-Lfnv -lfnv32 -lfnv64 -Lknheap -lknheap

if HAVE_TCMALLOC
  naturalli_LDADD += -ltcmalloc
  naturalli_search_LDADD += -ltcmalloc
if DEBUG
  naturalli_LDADD += -lprofiler
  naturalli_search_LDADD += -lprofiler
if IS_GCC
  naturalli_CXXFLAGS += -fprofile-arcs -ftest-coverage -fPIC -fstack-protector-all
  naturalli_search_CXXFLAGS += -fprofile-arcs -ftest-coverage -fPIC -fstack-protector-all
  naturalli_LDADD += -lgcov
  naturalli_search_LDADD += -lgcov
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
