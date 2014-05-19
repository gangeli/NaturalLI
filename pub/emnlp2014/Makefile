naturalli.pdf: $(wildcard *.tex) ref.bib Makefile ../macros.tex std-macros.tex
	@pdflatex naturalli
	@bibtex naturalli
	@pdflatex naturalli
	@pdflatex naturalli

clean:
	rm -f *.aux *.log *.bbl *.blg *.pdf *.bak *.ps *.dvi

default: naturalli.pdf
