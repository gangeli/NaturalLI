#!/bin/sh -e

libtoolize
aclocal
autoconf
automake --add-missing
chmod +x configure
