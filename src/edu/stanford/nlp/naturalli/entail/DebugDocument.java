package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A class to write a LaTeX document with a bunch of useful debugging information (e.g., alignments).
 */
class DebugDocument {
  private final File outputFile;
  private StringBuilder alignments = new StringBuilder();
  private Set<EntailmentPair> registeredAlignments = new HashSet<>();

  public DebugDocument(File output) throws IOException {
    this.outputFile = output;
    if (!this.outputFile.exists() && !this.outputFile.createNewFile()) {
      throw new RuntimeIOException("Could not create file: " + this.outputFile);
    }
    if (!this.outputFile.exists() || !this.outputFile.canWrite()) {
      throw new RuntimeIOException("Cannot write to the debug file!");
    }
  }

  private List<String> segment(Sentence s, List<Span> spans) {
    if (spans.isEmpty()) {
      return s.words();
    }
    List<String> segments = new ArrayList<>();
    Collections.sort(spans, (a, b) -> a.start() - b.start());
    Iterator<Span> spanIter = spans.iterator();
    Span nextSpan = spanIter.next();
    int i = 0;
    while ( i < s.length() ) {
      if (nextSpan == null || i < nextSpan.start()) {
        segments.add(s.word(i));
        i += 1;
      } else {
        segments.add(StringUtils.join(s.words().subList(nextSpan.start(), nextSpan.end()), " "));
        i = nextSpan.end();
        if (spanIter.hasNext()) {
          nextSpan = spanIter.next();
        } else {
          nextSpan = null;
        }
      }
    }
    return segments;
  }

  public synchronized void registerAlignment(EntailmentPair ex, Collection<EntailmentFeaturizer.KeywordPair> alignment) {
    if (registeredAlignments.contains(ex)) {
      return;
    }
    registeredAlignments.add(ex);

    alignments.append("\\begin{tikzpicture}\n");
    alignments.append("\\matrix[column sep=-0.5em,row sep=1cm,matrix of nodes,row 2/.style={coordinate}] (m) {\n");
    alignments.append("% First sentence\n");
    alignments.append(
         StringUtils.join(
            segment(ex.premise, alignment.stream().map(x -> x.premiseSpan ).filter(x -> x.start() >= 0).collect(Collectors.toList()))
             .stream().map(x -> {
              if (alignment.stream().anyMatch(y -> y.premiseSpan.start() >= 0 && y.premiseChunk().equals(x))) {
                return "\\rnode{" + x + "}{$^p$}";
              } else {
                return "\\hnode{" + x + "}";
              }
            }),
         " & "));
    alignments.append("\\\\\n\\\\\n");
    alignments.append("% Second sentence\n");
    if (ex.truth.isTrue()) {
      alignments.append("\\textcolor{green}{\\textbf{True:}} & ");
    } else if (ex.truth.isFalse()) {
      alignments.append("\\textcolor{brown}{\\textbf{False:}} & ");
    } else {
      alignments.append("\\textcolor{yellow}{\\textbf{Unknown:}} & ");
    }
    alignments.append(
        StringUtils.join(
            segment(ex.conclusion, alignment.stream().map(x -> x.conclusionSpan ).filter(x -> x.start() >= 0).collect(Collectors.toList()))
            .stream().map(x -> {
              if (alignment.stream().anyMatch(y -> y.conclusionSpan.start() >= 0 && y.conclusionChunk().equals(x))) {
                return "\\rnode{" + x + "}{$^c$}";
              } else {
                return "\\hnode{" + x + "}";
              }
            }),
            " & "));
    alignments.append("\\\\\n};\n");
    alignments.append("\\begin{scope}[every path/.style={line width=4pt,white,double=black},every to/.style={arm}, arm angleA=-90, arm angleB=90, arm length=5mm]\n");
    alignment.stream().filter(EntailmentFeaturizer.KeywordPair::isAligned).forEach(pair -> alignments.append("  \\draw (").append(pair.premiseChunk()).append("$^p$) to (").append(pair.conclusionChunk()).append("$^c$);\n"));
    alignments.append("\\end{scope}\n");
    alignments.append("\\end{tikzpicture}\n");
    alignments.append("\n");
    alignments.append("\\noindent\\makebox[\\linewidth]{\\rule{\\paperwidth}{0.4pt}}\n");
    alignments.append("\n");
  }

  @SuppressWarnings("StringBufferReplaceableByString")
  public void write() throws IOException {
    StringBuilder output = new StringBuilder();
    // Write the header
    output.append(
        "\\documentclass{article}\n" +
            "\\usepackage[a1paper]{geometry}\n" +
            "%\\url{http://tex.stackexchange.com/q/25474/86}\n" +
            "\\usepackage{tikz,color}\n" +
            "\\usetikzlibrary{calc,matrix}\n" +
            "\\newcommand{\\hnode}[1]{|(#1)| #1}\n" +
            "\\newcommand{\\rnode}[2]{|(#1#2)| \\textcolor{red}{#1}#2}\n" +
            "\n" +
            "\\tikzset{\n" +
            "  arm angleA/.initial={0},\n" +
            "  arm angleB/.initial={0},\n" +
            "  arm lengthA/.initial={0mm},\n" +
            "  arm lengthB/.initial={0mm},\n" +
            "  arm length/.style={%\n" +
            "    arm lengthA=#1,\n" +
            "    arm lengthB=#1,\n" +
            "  },\n" +
            "  arm/.style={\n" +
            "    to path={%\n" +
            "      (\\tikztostart) -- ++(\\pgfkeysvalueof{/tikz/arm angleA}:\\pgfkeysvalueof{/tikz/arm lengthA}) -- ($(\\tikztotarget)+(\\pgfkeysvalueof{/tikz/arm angleB}:\\pgfkeysvalueof{/tikz/arm lengthB})$) -- (\\tikztotarget)\n" +
            "    }\n" +
            "  },\n" +
            "}\n" +
            "\\begin{document}\n\n"
    );

    // Write the alignments
    output.append("\\section{Keyword Alignments}\n");
    output.append(alignments);

    // Write the footer
    output.append("\\end{document}\n");
    // Write the file
    IOUtils.writeStringToFile(output.toString(), outputFile.getPath(), "utf-8");
  }
}
