AC_PREREQ([2.62])
AC_INIT([NaturalLI], [1.0.0], [angeli@stanford.edu])
AM_INIT_AUTOMAKE([-Wall no-define foreign])
AC_LANG_DEFINES_PROVIDED

AC_CONFIG_MACRO_DIR([m4])
m4_include([m4/ax_lib_postgresql.m4])
m4_include([m4/ax_compile_stdcxx_0x.m4])
m4_include([m4/ax_cxx_header_unordered_map.m4])
m4_include([m4/ax_openmp.m4])
m4_include([m4/ax_boost_base.m4])
m4_include([m4/ax_boost_thread.m4])

AC_CONFIG_HEADERS(config.h)

# (c++)
AC_PROG_CXX
AC_LANG(C++)
LT_INIT([disable-shared])

# (java)
AC_SUBST(JDK_HOME)
AC_ARG_WITH([java],
  [AS_HELP_STRING([--with-java=dir],[The JDK home directory])],
  [JDK_HOME="$with_java"],
  [AC_MSG_NOTICE([a custom java path can be specified using --with-java])]
)
AC_CHECK_PROG([javac_found],["javac"],[yes],[no],["$JDK_HOME/bin"])
if test "x$javac_found" != "xyes"; then
  AC_MSG_ERROR([could not find javac compiler])
fi
AM_CONDITIONAL(SCALA_2_11, `${SCALA_HOME}/bin/scalac -version | grep "2.11"`)

# (scala)
AC_SUBST(SCALA_HOME)
AC_ARG_WITH([scala],
  [AS_HELP_STRING([--with-scala=dir],[The scala home directory])],
  [SCALA_HOME="$with_scala"],
  [AC_MSG_NOTICE([a custom scala path can be specified using --with-scala])]
)
AC_CHECK_PROG([scalac_found],["scalac"],[yes],[no],["$SCALA_HOME/bin"])
if test "x$scalac_found" != "xyes"; then
  AC_MSG_ERROR([could not find scalac compiler])
fi


# (ant)
AC_CHECK_PROG([ant_found],["ant"],[yes],[no])
AM_CONDITIONAL(HAVE_ANT, test x"$ant_found" = x"yes")

# (postgresql)
AX_LIB_POSTGRESQL([8.4.9])

# (tcmalloc -- if available)
AC_CHECK_LIB([tcmalloc], [malloc], [have_tcmalloc=yes], [have_tcmalloc=no])
AM_CONDITIONAL([HAVE_TCMALLOC],  [test "$have_tcmalloc" = "yes"])

# (protobuf)
PKG_CHECK_MODULES([PROTOBUF], [libprotobuf], [have_protobuf=yes], [have_protobuf=no])
AM_CONDITIONAL([HAVE_PROTOBUF],  [test "$have_protobuf" = "yes"])
if test -z "$have_protobuf"; then
  AC_MSG_ERROR([fatal: could not find the protobuf library])
fi
AC_ARG_WITH([protoc],
    [AS_HELP_STRING([--with-protoc=PATH],
        [location of the protocol buffer home; protoc should be located at bin/protoc ])],
    [PROTO_CFLAGS="-I$withval/include"
     PROTO_LDFLAGS="-L$withval/lib"
     PROTOC="$withval/bin/protoc"],
    [PROTO_CFLAGS=
     PROTO_LDFLAGS=
     PROTOC='protoc'])
AC_SUBST([PROTO_CFLAGS])
AC_SUBST([PROTO_LDFLAGS])
AC_SUBST([PROTOC])

# (gtest)
GTEST_VERSION="1.7.0"
if test "x$have_pthread" = "xyes"; then
  GTEST_CPPFLAGS="-DGTEST_HAS_PTHREAD=1"
  GTEST_CXXFLAGS="$PTHREAD_CFLAGS"
  GTEST_LDFLAGS=
  GTEST_LIBS="$PTHREAD_LIBS"
else
  GTEST_CPPFLAGS="-DGTEST_HAS_PTHREAD=0"
  GTEST_CXXFLAGS=
  GTEST_LDFLAGS=
  GTEST_LIBS=
fi
AC_SUBST([GTEST_VERSION])
AC_SUBST([GTEST_CPPFLAGS])
AC_SUBST([GTEST_CXXFLAGS])
AC_SUBST([GTEST_LDFLAGS])
AC_SUBST([GTEST_LIBS])

# (CoreNLP jar)
AC_SUBST(CORENLP)
CORENLP=$JAVANLP_HOME/projects/core/classes
AC_ARG_WITH([corenlp],
  [AS_HELP_STRING([--with-corenlp=/path/to/jar/file],[The path to the CoreNLP jar file])],
  [CORENLP="$with_corenlp"],
  [AC_MSG_NOTICE([a custom location for CoreNLP can be specified using --with-corenlp])]
)

# (CoreNLP models)
AC_SUBST(CORENLP_MODELS)
CORENLP_MODELS=/u/nlp/data/StanfordCoreNLPModels/stanford-corenlp-models-current.jar
AC_ARG_WITH([corenlp-models],
  [AS_HELP_STRING([--with-corenlp-models=/path/to/jar/file],[The path to the CoreNLP models jar file])],
  [CORENLP_MODELS="$with_corenlp_models"],
  [AC_MSG_NOTICE([a custom location for the CoreNLP models can be specified using --with-corenlp-models])]
)

# (CoreNLP caseless models)
AC_SUBST(CORENLP_CASELESS_MODELS)
CORENLP_CASELESS_MODELS=/u/nlp/data/StanfordCoreNLPModels/stanford-corenlp-caseless-models-current.jar
AC_ARG_WITH([corenlp-caseless-models],
  [AS_HELP_STRING([--with-corenlp-caseless-models=/path/to/jar/file],[The path to the CoreNLP caseless models jar file])],
  [CORENLP_CASELESS_MODELS="$with_corenlp_caseless_models"],
  [AC_MSG_NOTICE([a custom location for the CoreNLP caseless models can be specified using --with-corenlp-models])]
)

# (check headers)
AC_CHECK_HEADERS([algorithm arpa/inet.h assert.h cmath cstdio \
                  cstdlib cstring ctime exception map netinet/in.h \
                  queue set sstream stdio.h stdlib.h string \
                  sys/errno.h sys/resource.h sys/socket.h sys/types.h \
                  vector ])

# (unordered map)
AX_CXX_HEADER_UNORDERED_MAP
# (openmp)
AX_OPENMP
# (boost)
#AX_BOOST_BASE([1.54.0])
#AX_BOOST_THREAD()

# Set some useful variables
# (compiler info)
AM_CONDITIONAL(IS_GCC, test x"$CXX" = x"gcc" || test x"$CXX" = x"g++")
# (functionality availability)
AM_CONDITIONAL(HAVE_DEMO, ls lib/demo &> /dev/null)
AM_CONDITIONAL(HAVE_SCRIPTS, ls lib/scripts &> /dev/null)
# (cache line size)
AC_DEFINE_UNQUOTED(CACHE_LINE_SIZE, `getconf LEVEL1_DCACHE_LINESIZE 2> /dev/null || echo "64"`, [The cache line size of the current machine])

# Optionally enable debugging
AC_ARG_ENABLE(debug,
AS_HELP_STRING([--enable-debug],
               [enable debugging, default: no]),
[case "${enableval}" in
             yes) debug=true ;;
             no)  debug=false ;;
             *)   AC_MSG_ERROR([bad value ${enableval} for --enable-debug]) ;;
esac],
[debug=false])
AM_CONDITIONAL(DEBUG, test x"$debug" = x"true")
AM_CONDITIONAL(NDEBUG, test x"$debug" = x"false")
if test x"$debug" = x"false"; then  
  AC_DEFINE(NDEBUG, 1, [Disable debugging (for efficiency)])
fi

# User defined variables (or environment variables)
AC_DEFINE_UNQUOTED(PG_HOST,         "${PGHOST:=jonsson.stanford.edu}", [The hostname of the database to connect to])
AC_DEFINE_UNQUOTED(PG_PORT,         ${PGPORT:=5432}, [The port on which postgres is running])
AC_DEFINE_UNQUOTED(PG_DATABASE,     "${DBNAME:=naturalli}", [The database to connect to])
AC_DEFINE_UNQUOTED(PG_USER,         "${PGUSER:=angeli}", [The username to use for the database connection])
AC_DEFINE_UNQUOTED(PG_PASSWORD,     "${PGPASSWORD:=}", [The password to use for the database connection])

AC_DEFINE_UNQUOTED(PG_TABLE_WORD,      "${PG_TABLE_WORD:=word}", [The table in which the word indexer is stored])
AC_DEFINE_UNQUOTED(PG_TABLE_EDGE,      "${PG_TABLE_EDGE:=edge}", [The table in which the graph edges are stored])
AC_DEFINE_UNQUOTED(PG_TABLE_FACT,      "${PG_TABLE_FACT:=fact}", [The table in which the facts are stored])
AC_DEFINE_UNQUOTED(PG_TABLE_PRIVATIVE, "${PG_TABLE_PRIVATIVE:=privative}", [The table storing all deletable word+sense items])

AC_DEFINE_UNQUOTED(MAX_FACT_LENGTH, ${MAX_FACT_LENGTH:=255}, [The maximum length of a fact; this is used in various fixed-length arrays])
AC_DEFINE_UNQUOTED(MAX_QUERY_LENGTH, 26, [The maximum length of an input query fact. Must match SynSearch.h lengths.])
AC_DEFINE_UNQUOTED(MAX_COMPLETIONS, ${MAX_COMPLETIONS:=32},  [The maximum number of completions to propose when inserting words])

AC_DEFINE_UNQUOTED(SEARCH_TIMEOUT,  ${SEARCH_TIMEOUT:=100000},  [The maximum number of elements to pop off the queue for a search (if no such value is provided in the query)])
AC_DEFINE_UNQUOTED(MIN_FACT_COUNT,  ${MIN_FACT_COUNT:=1},  [The minimum number of times we should see a fact before we add it to the fact database])
AC_DEFINE_UNQUOTED(HIGH_MEMORY,     ${HIGH_MEMORY:=0},  [If true, use more memory in exchange for better performance.])
AC_DEFINE_UNQUOTED(FACT_MAP_SIZE,   ${FACT_MAP_SIZE:=32},  [The bitshift for the fact database partial completion map size. i.e., 32 = 2^32 elements])
AC_DEFINE_UNQUOTED(GREEDY_LOAD,     ${GREEDY_LOAD:=0},  [If true, greedily load the fact database, even if no one is requesting it.])
AC_DEFINE_UNQUOTED(OLD_TRIE,        ${OLD_TRIE:=0},  [If true, load the old high-memory Trie implementation])
AC_DEFINE_UNQUOTED(TWO_PASS_HASH,   ${TWO_PASS_HASH:=0},  [If true, pass each dependency arc through the fnv hash before XOR-ing it.])

AC_CONFIG_FILES([Makefile src/Makefile src/fnv/Makefile src/knheap/Makefile \
                 test/src/Makefile test/src/gtest/Makefile ])
AC_OUTPUT
