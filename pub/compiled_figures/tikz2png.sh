#!/bin/bash
#
# A quick and dirty bash script to convert a tikz image to a png.
# The necessary tweaks to the script to get it to work for your
# use case are as follows:
#
# 1. Change FIGURE_DEFINITIONS to point to your own figures.
#   This file should contain a series of \newcommand definitions,
#  one per figure.
#
# 2. Change HEADER to import the necessary libraries for your
#    figure to compile. This can be empty if it requires nothing
#    beyond vanilla tikz.
#
# The usage is:
#
#   tikz2png.sh <figure_function_name> <output_figure_name>
#
# For example:
#
#   tikz2png.sh myTikzFigure output_filename
#
# would create files:
# 
#   output_filename.pdf
#   output_filename.png
#
# based off of the LaTeX macro:
#
#   \myTikzFigure
#

set -o nounset
set -o errexit

FIGURE_DEFINITIONS="../figures.tex"

HEADER="
\usepackage{times,latexsym,amsfonts,amssymb,amsmath,graphicx,url,bbm,rotating,datetime}
\usepackage{enumitem,multirow,hhline,stmaryrd,bussproofs,mathtools,siunitx,arydshln,pifont}
\usepackage{../tikz-dependency}
\usetikzlibrary{shapes.arrows,chains,positioning,automata,trees,calc}
\usetikzlibrary{patterns}
\usetikzlibrary{decorations.pathmorphing,decorations.markings}

\input ../macros.tex
\input ../std-macros.tex
"

TEX_FILE="`mktemp`.tex"
PDF_FILE=`echo "$TEX_FILE" | sed -r -e 's/.tex$/.pdf/g'`
FILE_BASE=`echo "$(basename $TEX_FILE)" | sed -r -e 's/.tex$//g'`
TMP_DIR=`dirname "$TEX_FILE"`
function cleanup {
  if [ -f "$TEX_FILE" ]; then
    echo "$TEX_FILE"
#    rm "$TEX_FILE"
  fi
  if [ -f "$PDF_FILE" ]; then
    rm "$PDF_FILE"
  fi
  if [ -f "$FILE_BASE.log" ]; then
    rm "$FILE_BASE.log"
  fi
  if [ -f "$FILE_BASE.aux" ]; then
    rm "$FILE_BASE.aux"
  fi
}   
trap cleanup EXIT

cat >> "$TEX_FILE" <<eof
\documentclass{standalone}
\usepackage{tikz}

$HEADER
\input $FIGURE_DEFINITIONS

\newcommand\rawlatex[1]{#1}

\begin{document}
\\$1
\end{document}
eof

pdflatex "$TEX_FILE"
mv "$FILE_BASE.pdf" "$2.pdf"
convert -density 300 "$2.pdf" -trim -quality 90 "$2.png"
