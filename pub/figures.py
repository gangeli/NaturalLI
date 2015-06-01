#!/usr/bin/env python
#

from numpy import *
from pylab import *

def initFigure(w, h):
  figure(figsize=(w, h), dpi=80)
  subplot(1, 1, 1)
  tight_layout(pad=4.00)
  font = {'size'   : 16}
  matplotlib.rc('font', **font)

def setSpines():
  ax = gca()
  ax.spines['right'].set_color('none')
  ax.spines['top'].set_color('none')
  ax.xaxis.set_ticks_position('bottom')
  ax.yaxis.set_ticks_position('left')

def monotonicity_math(path):
  initFigure(8, 5)
  setSpines()
  # Plot a monotone and antitone function
  X = linspace(0, 12, 256, endpoint=True)
  EmX = exp(-X)
  EX = exp(X) - 1
  plot(X, EmX, color='blue', linewidth=3.0, linestyle='-', label='$e^{-x}$')
  plot(X, EX, color='green', linewidth=3.0, linestyle=':', label='$e^x - 1$')
  # Label the axes
  xlabel('$x$')
  xlim(-0.1, 2.1)
  ylabel('Value of operator')
  ylim(-0.1, 5.1)
  # Create a legend
  legend()
  # Save
  savefig(path)

def monotonicity_lex_all(path):
  initFigure(8, 5)
  setSpines()
  # Plot 'all' and 'some'
  plot([0, 3, 6, 7, 7, 9, 12],
       [1, 1, 1, 1, 0, 0, 0], color='blue', linewidth=3.0, linestyle='-', label='all $x$ drink milk')
  plot([0, 3, 6, 8, 8, 9, 12],
      [0, 0, 0, 0, 1, 1, 1], color='green', linewidth=3.0, linestyle=':', label='some $x$ bark')
  # Label the axes
  xlabel('Denotation of word $x$')
  xlim(-1.0, 13.0)
  xticks([0, 3, 6, 9, 12],
         ['$x=$ Felix', 'kitten', 'cat', 'animal', 'thing'])
  ylabel('Truth Value of Sentence')
  ylim(-0.1, 1.7)
  yticks([0, 1], ['False', 'True'])
  # Create a legend
  legend()
  # Save
  savefig(path)

def monotonicity_lex_meronymy(path):
  initFigure(8, 5)
  setSpines()
  # Plot 'is pretty' and 'was born in'
  plot([0, 3, 5, 5, 6, 9, 12],
       [0, 0, 0, 1, 1, 1, 1], color='blue', linewidth=3.0, linestyle='-', label='Obama was born in $x$')
  plot([0, 2, 2, 3, 4, 4, 6, 9, 12],
      [0, 0, 1, 1, 1, 0, 0, 0, 0], color='green', linewidth=3.0, linestyle=':', label='$x$ is an island')
  # Label the axes
  xlabel('Denotation of word $x$')
  xlim(-1.0, 13.0)
  xticks([0, 3, 6, 9, 12],
         ['$x=$ Hilo', 'Big Island', 'Hawaii', 'USA', 'North America'])
  ylabel('Truth Value of Sentence')
  ylim(-0.1, 1.7)
  yticks([0, 1], ['False', 'True'])
  # Create a legend
  legend()
  # Save
  savefig(path)

if __name__ == "__main__":
  monotonicity_lex_all('img/monotonicity_lex_all.pdf')
  monotonicity_math('img/monotonicity_math.pdf')
  monotonicity_lex_meronymy('img/monotonicity_lex_meronymy.pdf')
  
#  show()
