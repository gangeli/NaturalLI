gabor_openie.pdf: $(wildcard *.tex) ref.bib Makefile ../figures.tex ../macros.tex std-macros.tex
	python pr_plot.py
	@pdflatex gabor_openie
	@bibtex gabor_openie
	@pdflatex -interaction batchmode gabor_openie
	@pdflatex -interaction batchmode gabor_openie

clean:
	rm -f *.aux *.log *.bbl *.blg gabor_openie.pdf *.bak *.ps *.dvi

fast:
	@pdflatex -interaction batchmode  gabor_openie

default: naturalli.pdf
