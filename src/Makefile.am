SUBDIRS = fnv knheap
bin_PROGRAMS=naturalli_mkkb naturalli
EXTRA_DIST = Messages.proto edu

Messages.pb.h Messages.pb.cc: Messages.proto
	${PROTOC} -I=. --cpp_out=. Messages.proto

org/goobs/naturalli/Messages.java: Messages.proto
	${PROTOC} -I=. --java_out=. Messages.proto

clean-local:
	rm -f Messages.pb.cc Messages.pb.h edu/stanford/nlp/naturalli/Messages.java
	rm -f *.gcno *.gcda
	rm -f naturalli.war

BUILT_SOURCES = Messages.proto


naturalli_SOURCES = Messages.pb.cc \
										NaturalLI.cc Postgres.cc Utils.cc Graph.cc SynSearch.cc \
                 		SynSearchSingleThreaded.cc \
										Messages.pb.h \
                 		Graph.h Utils.h Types.h  SynSearch.h Postgres.h \
                 		btree.h btree_container.h btree_map.h btree_set.h

naturalli_CXXFLAGS=-std=c++0x -pthread ${POSTGRESQL_CFLAGS} ${OPENMP_CFLAGS}
naturalli_LDADD=${POSTGRESQL_LDFLAGS} ${PROTO_LDFLAGS} -lprotobuf -Lfnv -lfnv32 -lfnv64 -Lknheap -lknheap

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

naturalli_mkkb_SOURCES = CreateKnowledgeBase.cc Postgres.cc Utils.cc Graph.cc SynSearch.cc \
                 		Graph.h Utils.h Types.h  SynSearch.h Postgres.h \
                 		btree.h btree_container.h btree_map.h btree_set.h

naturalli_mkkb_CXXFLAGS=-std=c++0x -pthread ${POSTGRESQL_CFLAGS} ${OPENMP_CFLAGS}
naturalli_mkkb_LDADD=${POSTGRESQL_LDFLAGS} -Lfnv -lfnv32 -lfnv64 -Lknheap -lknheap

naturalli_preprocess.jar: $(wildcard org/goobs/naturalli/*.scala) $(wildcard edu/stanford/nlp/naturalli/*.java) $(wildcard org/goobs/naturalli/*.java) $(wildcard org/goobs/naturalli/scripts/*.scala) $(wildcard org/goobs/naturalli/demo/*.java) $(wildcard org/goobs/natualli/conf/*.conf) org/goobs/naturalli/Messages.java
	@echo "Compiling (${JDK_HOME}/bin/javac)..."
	@${JDK_HOME}/bin/javac -Xlint:unchecked -d . -cp ${CP}:${SCALA_HOME}/lib/scala-library.jar:${SCALA_HOME}/lib/config-1.2.0.jar `find . -name "*.java" -not -path "./org/goobs/naturalli/demo/*.java"  -not -path "./org/goobs/naturalli/scripts/*.java"`
	@echo "Making Jar..."
	${JDK_HOME}/bin/jar cf naturalli_preprocess.jar -C . .
	bash -c 'for file in `find . -name "*.class"`; do rm -f $$file; done'

naturalli.war: naturalli_preprocess.jar $(wildcard org/goobs/naturalli/demo/*.java) org/goobs/naturalli/demo/WEB-INF/naturalli.xml
	@echo "Creating WAR..."
	cd ../ && ant war
