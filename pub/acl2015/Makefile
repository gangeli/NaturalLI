angeli_openie.pdf: $(wildcard *.tex) ref.bib Makefile ../figures.tex ../macros.tex std-macros.tex
	@pdflatex angeli_openie
	@bibtex angeli_openie
	@pdflatex angeli_openie
	@pdflatex angeli_openie

clean:
	rm -f *.aux *.log *.bbl *.blg angeli_openie.pdf *.bak *.ps *.dvi

fast:
	@pdflatex angeli_openie

default: naturalli.pdf
