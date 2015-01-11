SUBDIRS = fnv knheap
bin_PROGRAMS=naturalli_mkkb naturalli
EXTRA_DIST =  edu

clean-local:
	rm -f *.gcno *.gcda
	rm -f naturalli.war


naturalli_SOURCES = GZip.cc \
										NaturalLI.cc Utils.cc Graph.cc SynSearch.cc \
                 		SynSearchSingleThreaded.cc JavaBridge.cc \
                 		Graph.h Utils.h Types.h  SynSearch.h \
										JavaBridge.h GZip.h \
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

naturalli_mkkb_SOURCES = CreateKnowledgeBase.cc Utils.cc Graph.cc SynSearch.cc GZip.cc \
                 		Graph.h Utils.h Types.h  SynSearch.h GZip.h \
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
