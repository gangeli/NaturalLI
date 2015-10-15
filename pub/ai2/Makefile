
present.pdf: present.tex ../macros.tex ../figures.tex $(wildcard *.tex)
	pdflatex present

clean:
	rm -f *.aux *.log *.nav *.out *.snm *.toc present.pdf

dist:
	pdflatex present
	pdflatex present
