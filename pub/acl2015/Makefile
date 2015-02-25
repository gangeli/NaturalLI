gabor_openie.pdf: $(wildcard *.tex) ref.bib Makefile ../figures.tex ../macros.tex std-macros.tex
	@pdflatex gabor_openie
	@bibtex gabor_openie
	@pdflatex gabor_openie
	@pdflatex gabor_openie

clean:
	rm -f *.aux *.log *.bbl *.blg gabor_openie.pdf *.bak *.ps *.dvi

fast:
	@pdflatex gabor_openie

default: naturalli.pdf
