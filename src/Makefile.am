SUBDIRS = fnv knheap
bin_PROGRAMS=naturalli_mkkb naturalli
EXTRA_DIST =  edu

clean-local:
	rm -f *.gcno *.gcda
	rm -f naturalli.war

Models.h: ../rc ../etc/depInsert2rel.tab
	echo "// DO NOT MODIFY THIS FILE!" > Models.h
	echo "// It was automatically generated from the Makefule" >> Models.h
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
	cd .. && source rc && dep2header >> src/Models.h
	echo "" >> Models.h
	echo '/**' >> Models.h
	echo ' * Index a dependency arc to an integer.' >> Models.h
	echo ' */' >> Models.h
	echo 'uint8_t indexDependency(const std::string& dependencyAsString);' >> Models.h
	echo '' >> Models.h
	cd .. && source rc && edgeTypes >> src/Models.h
	echo '' >> Models.h
	cd .. && source rc && dummyVocab >> src/Models.h
	echo '' >> Models.h
	echo '/**' >> Models.h
	echo ' * Get the String gloss for an indexed dependency arc.' >> Models.h
	echo ' */' >> Models.h
	echo 'std::string dependencyGloss(const uint8_t& indexed);' >> Models.h
	echo "#endif" >> Models.h

Models.cc: ../rc ../etc/depInsert2rel.tab
	echo '#include "Models.h"' > Models.cc
	echo '' >> Models.cc
	echo '#include <algorithm>' >> Models.cc
	echo '' >> Models.cc
	echo 'using namespace std;' >> Models.cc
	echo '' >> Models.cc
	cd .. && source rc && dep2string >> src/Models.cc
	echo "" >> Models.cc
	cd .. && source rc && depInsert2relC >> src/Models.cc

naturalli_SOURCES = GZip.cc Models.cc \
										NaturalLI.cc Utils.cc Graph.cc SynSearch.cc \
                 		SynSearchSingleThreaded.cc JavaBridge.cc \
                 		Graph.h Utils.h Types.h  SynSearch.h \
										JavaBridge.h GZip.h Models.h \
                 		btree.h btree_container.h btree_map.h btree_set.h


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

naturalli_mkkb_SOURCES = CreateKnowledgeBase.cc Utils.cc Graph.cc SynSearch.cc \
												 Models.cc GZip.cc \
                 		Graph.h Utils.h Types.h  SynSearch.h GZip.h Models.h \
                 		btree.h btree_container.h btree_map.h btree_set.h

naturalli_mkkb_CXXFLAGS=-std=c++0x -pthread ${OPENMP_CFLAGS}
naturalli_mkkb_LDADD=-Lfnv -lfnv32 -lfnv64 -Lknheap -lknheap

naturalli_preprocess.jar: $(wildcard edu/stanford/nlp/naturalli/*.java)
	@echo "Ensuring models..."
	${MAKE} -C .. .have_models
	@echo "Compiling (${JDK_HOME}/bin/javac)..."
	@${JDK_HOME}/bin/javac -Xlint:unchecked -d . -cp ${CP} `find . -name "*.java"`
	@echo "Making Jar..."
	${JDK_HOME}/bin/jar cf naturalli_preprocess.jar -C . .
	bash -c 'for file in `find . -name "*.class"`; do rm -f $$file; done'

naturalli.war: naturalli_preprocess.jar
	@echo "Ensuring models..."
	${MAKE} -C .. .have_models
	@echo "Creating WAR..."
	cd ../ && ant war
