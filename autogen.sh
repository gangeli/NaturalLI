#!/bin/sh -e

libtoolize --force
aclocal --force
autoconf
automake --add-missing
chmod +x configure
