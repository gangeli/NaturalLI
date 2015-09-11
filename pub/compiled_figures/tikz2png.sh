#!/bin/bash
#
set -o nounset
set -o errexit
set -o xtrace

TEX_FILE="`mktemp`.tex"
PDF_FILE=`echo "$TEX_FILE" | sed -r -e 's/.tex$/.pdf/g'`
FILE_BASE=`echo "$(basename $TEX_FILE)" | sed -r -e 's/.tex$//g'`
TMP_DIR=`dirname "$TEX_FILE"`
function cleanup {
#  if [ -f "$TEX_FILE" ]; then
#    rm "$TEX_FILE"
#  fi
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
\usepackage{times,latexsym,amsfonts,amssymb,amsmath,graphicx,url,bbm,rotating,datetime}
\usepackage{enumitem,multirow,hhline,stmaryrd,bussproofs,mathtools,siunitx,arydshln}
\usepackage{../tikz-dependency,pifont}
\usepackage{tikz}
\usetikzlibrary{shapes.arrows,chains,positioning,automata,trees,calc}
\usetikzlibrary{patterns}
\usetikzlibrary{decorations.pathmorphing,decorations.markings}

\input ../macros.tex
\input ../std-macros.tex
\input ../figures.tex

\begin{document}
\\$1
\end{document}
eof

pdflatex "$TEX_FILE"
mv "$FILE_BASE.pdf" "$2"
convert -density 300 "$2" -quality 90 "`echo "$(basename $2)" | sed -r -e 's/.pdf$/.png/g'`"
