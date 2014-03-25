AC_PREREQ([2.68])
AC_INIT([hello], [0.1], [angeli@stanford.edu])
AM_INIT_AUTOMAKE([1.10 -Wall no-define foreign])

AC_CONFIG_MACRO_DIR([m4])
m4_include([m4/ax_lib_postgresql.m4])

AC_CONFIG_HEADERS(config.h)

echo "---CHECKS FOR COMPILERS---"
AC_PROG_CXX
AC_LANG(C++)
LT_INIT([disable-shared])

echo "---CHECKS FOR LIBRARIES---"
# postgresql
AX_LIB_POSTGRESQL([8.4.9])
# protobuf
PKG_CHECK_MODULES([PROTOBUF], [libprotobuf], [have_protobuf=yes], [have_protobuf=no])
AM_CONDITIONAL([HAVE_PROTOBUF],  [test "$have_protobuf" = "yes"])
if test -z "$have_protobuf"; then
  AC_MSG_ERROR([fatal: could not find the protobuf library])
fi

echo "---CHECKS FO HEADERS---"
AC_CHECK_HEADER([algorithm], [], [echo "could not find required header!"; exit -1])
AC_CHECK_HEADERS([cstdlib stdlib.h])
AC_FUNC_MALLOC

#include <stdlib.h>

#include <algorithm>
#include <arpa/inet.h>
#include <assert.h>
#include "Bloom.h"
#include <cmath>
#include "Config.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <exception>
#include "FactDB.h"
#include "fnv/fnv.h"
#include <google/protobuf/descriptor.h>
#include <google/protobuf/extension_set.h>
#include <google/protobuf/generated_message_reflection.h>
#include <google/protobuf/generated_message_util.h>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/reflection_ops.h>
#include <google/protobuf/repeated_field.h>
#include <google/protobuf/stubs/common.h>
#include <google/protobuf/stubs/once.h>
#include <google/protobuf/wire_format.h>
#include <google/protobuf/wire_format_lite_inl.h>
#include "Graph.h"
#include "Graph.h" 
#include "libpq-fe.h"
#include <map>
#include "Messages.pb.h"
#include "Messages.pb.h" 
#include <netinet/in.h>
#include "Postgres.h"
#include <queue>
#include "Search.h"
#include "Search.h" 
#include <set>
#include <sstream>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string>
#include <sys/errno.h>
#include <sys/resource.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <thread>
#include "Trie.h"
#include "Trie.h" 
#include <unordered_map>
#include "Utils.h"
#include "Utils.h" 
#include <vector>

echo "---CUSTOM FLAGS---"
# optionally enable debugging
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

AC_CONFIG_FILES([Makefile src/fnv/Makefile])
AC_OUTPUT
