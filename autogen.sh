#!/bin/sh -e

libtoolize --force
aclocal --force
autoheader
autoconf
automake --add-missing
chmod +x configure
