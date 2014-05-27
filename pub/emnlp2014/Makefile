naturalli.pdf: $(wildcard *.tex) ref.bib Makefile ../figures.tex ../macros.tex std-macros.tex
	@pdflatex naturalli
	@bibtex naturalli
	@pdflatex naturalli
	@pdflatex naturalli

clean:
	rm -f *.aux *.log *.bbl *.blg naturalli.pdf *.bak *.ps *.dvi

fast:
	@pdflatex naturalli

default: naturalli.pdf
