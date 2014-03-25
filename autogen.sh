#!/bin/sh -e

aclocal
autoconf
automake --add-missing
chmod +x configure
./configure --enable-debug
