AC_PREREQ([2.68])
AC_INIT([hello], [0.1], [angeli@stanford.edu])
AM_INIT_AUTOMAKE([1.10 -Wall no-define foreign])

AC_CONFIG_MACRO_DIR([m4])
m4_include([m4/ax_lib_postgresql.m4])
m4_include([m4/ax_compile_stdcxx_0x.m4])
m4_include([m4/ax_cxx_header_unordered_map.m4])

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

echo "---CHECKS FOR PROGRAMS---"
AC_ARG_WITH([protoc],
    [AS_HELP_STRING([--with-protoc=PATH],
        [location of the protoc protocol buffer compiler binary, defaults to protoc])],
    [PROTOC="$withval"],
    [PROTOC='protoc'])
AC_SUBST([PROTOC])

echo "---CHECKS FOR HEADERS---"
AC_CHECK_HEADERS([algorithm arpa/inet.h assert.h cmath cstdio \
                  cstdlib cstring ctime exception map netinet/in.h \
                  queue set sstream stdio.h stdlib.h string \
                  sys/errno.h sys/resource.h sys/socket.h sys/types.h \
                  vector ])
AC_FUNC_MALLOC
AX_CXX_HEADER_UNORDERED_MAP

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

AC_CONFIG_FILES([Makefile src/Makefile src/fnv/Makefile])
AC_OUTPUT
