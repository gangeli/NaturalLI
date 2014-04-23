#!/usr/bin/ruby
# encoding: utf-8

require 'rfig/Presentation'
require '../include.rb'
require '../figlib.rb'

#--Set Slide Style
slideStylePlain = SlideStyle.new.
	border(1).
	borderColor(black).
	titleSpacing(u(0.2))
slideStyle = SlideStyle.new
	.border(1)
	.borderColor(black)
	.titleSpacing(u(0.2))

#--Init Presentation
initPresentation(
	:latexHeader => IO.readlines("../std-macros.tex") +
		IO.readlines("../macros.tex"),
	:edition => 'present',
	:fontSize => 32
)

################################################################################
# TITLE
################################################################################
slide!('',
	center,
	rtable(
		rtable('Natural Logic',
           'for Common Sense Inference').scale(2.0).color(darkred).center,
		ctable(
      author(_('Gabor Angeli').color(darkblue)),
    nil).cmargin(u(1.0)),
		image('../img/logo.jpg').scale(0.25),
	nil).rmargin(u(0.75)).cjustify('c'),
	left,
nil){ |slide| slide.label('intro_title').signature(3) }

################################################################################
# WORK IN PROGRESS
################################################################################
slide!('Work In Progress',
  h1('Want to avoid:'),
  center,
  image('../img/smbc_tellyouthings.png'),
  '',
  pause,
  image('../img/smbc_advice.png').scale(0.75),
  '',
  pause,
  left,
  h1('Results, methods preliminary and in flux'),
nil){ |slide| slide.label('intro_inprogress').signature(9) }

################################################################################
# MOTIVATION
################################################################################
slide!('Motivation',
  
  h1('Advantages of Knowledge Bases'),
  ind('Compact, clean information'),
  ind('Enables (or simplifies) applications: Q/A, semantic parsing, etc.'),
  ind('NSA loves them'),
  pause,
  ind('\blue{Convenient representation of common sense knowledge}'),
  pause,
  '',
  staggeredOverlay(true,
    rtable(
      ind(ind(w('cats have tails'))),
      ind(ind(w('coffee is in kitchens'))),
      ind(ind(ctable(w('cakes are eaten with forks'),
                     w('cakes contain cherries')).cmargin(u(0.5)))),
    nil),
    rtable(h1('But... \textit{``text is knowledge\'\'}'),
      ind('Knowledge bases are just caching information in text'),
      ind('Explicitly handle, e.g., contradiction resolution'),
      nil),
  nil),
  pause,

  '',
  staggeredOverlay(true,
    rtable(h1('Inference'),
           ind('Capture facts not explicit in text -- less trivial ``caching\'\'.'),
           nil),
    rtable('\textbf{\darkblue{Common Sense} \darkred{Inference}}',
           ind('Lots of easy, valuable, rarely explicit inferences'),
           ind('marry$(x,y)$, child$(z,x)$ $\rightarrow$ child$(z,y)$'),
           ind('\textit{all felines have claws} $\rightarrow$ \textit{cat has claw}'),
           nil),
    rtable('\textbf{\darkblue{Natural Logic} \darkred{for Common Sense Inference}}',
           ind('Weak, but computationally efficient inference'),
           ind('``Text is knowledge\'\' -- NatLog is inference over text'),
           nil),
    rtable('\textbf{\darkblue{Fuzzy} \darkred{Natural Logic for Common Sense Inference}}',
           ind('Say \textit{something} about every query'),
           ind('\textit{dogs have tail} $\rightarrow$ \textit{cats have tails}'),
           nil),
  nil),

nil){ |slide| slide.label('intro_motivation').slideStyle(slideStyle).signature(30) }

################################################################################
# TASK DESCRIPTION
################################################################################
slide!('Task Description',
  describe(['Input',  'A query common sense fact'],
           ['',       'A large (noisy) database of facts'],
           ['Output', 'True or false, with some confidence']),
  pause,
  '',
  '',
  
  h1('Viewed as inference'),
  ind('Classically: $\{$antecedent$\}$ $\rightarrow$ consequent'),
  pause,
  ind('Challenge: $|\{$antecedent$\}|$ $>$ 100M'),
  pause,
  '',
  '',

  h1('Viewed as Q/A'),
  ind('Classically: IR $\rightarrow$ $\{$antecedent$\}$ $\rightarrow$ consequent'),
  pause,
  ind('Challenge: IR is noisy'),
nil){ |slide| slide.label('intro_task').slideStyle(slideStyle).signature(8) }


################################################################################
# NATURAL LOGIC INTRO
################################################################################
slide!('Natural Logic: High Level',
  describe(['First-Order', 'text $\rightarrow$ formula$_1$ $\rightarrow$ formula$_2$ $\rightarrow$ text'],
           ['Natural Logic',     'text $\rightarrow$ text']),
  '',
  pause,
  
  h1('Noble goal'),
  ind('text $\rightarrow$ formula$_1$ is hard (semantic parsing)!'),
  ind('formula$_1$ $\rightarrow$ formula$_2$ is hard (theorem proving)!'),
  '',
  pause,
  
  h1('Proof theory over textual edits'),
  '',
  ind('All animals have tails $\leftarrow$ \textbf{antecedent}'),
  pause,
  staggeredOverlay(true,
    ind('All \darkblue{chordate} have tails'),
    ind('All \darkblue{vertebrate} have tails'),
    ind('All \darkblue{mammal} have tails'),
    ind('All \darkblue{placental} have tails'),
    ind('All \darkblue{carnivore} have tails'),
    ind('All \darkblue{feline} have tails'),
    ind('All \darkblue{cats} have tails'),
  nil),
  pause,
  ind('\darkblue{Some} cats have tails'),
  pause,
  ind('Some cats \darkblue{posess} tails'),
  pause,
  ind('Some cats posess \darkblue{appendages} $\leftarrow$ \textbf{consequent}'),
nil){ |slide| slide.label('natlog_intro').slideStyle(slideStyle).signature(11) }

################################################################################
# NATURAL LOGIC MONOTONICITY
################################################################################
slide!('Natural Logic: Monotonicity',
  h1('Every phrase has a denotation'),
  '',
  center,
  staggeredOverlay(true,
    rtable(vennDiagram('animal', 'everything', 'subset'), 
           'Denotation of \textit{animal}').rmargin(u(0.5)),
    rtable(vennDiagram('cat', 'animal', 'subset'), 
           '\textit{cat} \forward \textit{animal}').rmargin(u(0.5)),
    rtable(vennDiagram('cat', 'furry', 'intersect'), 
           '\textit{furry cat} \forward \textit{cat}').rmargin(u(0.5)),
  nil),
  left,
  pause,

  h1('Words have monotonicity'),
  ind('Upwards: if $w \forward y$ then can substitute $y$ for $w$'),
  ind('Downwards: if $w \reverse y$ then can substitute $y$ for $w$'),
  pause,

  h1('Quantifiers tag monotonicity'),
  staggeredOverlay(true,
   ind(mono('\textbf{all}_v animals_v have_^ tails_^')),
   ind(mono('\textbf{all}_v \blue{cats}_v have_^ tails_^')),
   ind(mono('\textbf{all}_v animals_v have_^ \blue{appendeges}_^')),
   ind(mono('\textbf{all}_v animals_v have_^ tails_^')),
   ind(mono('\textbf{some}_^ animals_^ have_^ tails_^')),
   ind(mono('\textbf{some}_^ \red{cats}_^ have_^ tails_^')),
  nil),
nil){ |slide| slide.label('natlog_monotone').slideStyle(slideStyle).signature(54) }

################################################################################
# NATURAL LOGIC MacCarntney
################################################################################
slide!('Natural Logic: MacCartney',
  center,
  h1('More to life than \forward and \reverse'),
  pause,

  staggeredOverlay(true,
    rtable(image('../img/alex_relations.png').scale(0.75)).cjustify('c'),
    rtable(
      '',
      '(\textit{cat} \forward \textit{living thing} \negate \textit{nonliving thing}) $\rightarrow$ (\textit{cat} \alternate \textit{nonliving thing})',
      image('../img/alex_join.png').scale(0.9),
    nil).cjustify('c').rmargin(u(0.5)),
  nil),
  left,
nil){ |slide| slide.label('natlog_bill').slideStyle(slideStyle).signature(31) }


################################################################################
# SIMPLIFY
################################################################################
slide!('Step 1: Simplify Natural Logic',
  describe(['Input',  'A query common sense fact'],
           ['Output', '\textbf{True} or \textbf{false} (ok, maybe sometimes \textbf{unknown})']),
  '',
  pause,
  staggeredOverlay(true,
    rtable(image('../img/alex_relations.png').scale(0.70)),
    rtable(
      '',
      h1('Can map to truth values'),
      '',
      ind(table(
        ['\equivalent', '\forward',   '',             '$\rightarrow$', 'True'],
        ['\negate',     '\alternate', '',             '$\rightarrow$', 'False'],
        ['\reverse',    '\cover',     '\independent', '$\rightarrow$', 'Unknown (or False)'],
      nil)),
      pause,
      '',
      h1('Let\'s try to reduce to three classes'),
    nil),
  nil),
nil){ |slide| slide.label('natlog_simplify').slideStyle(slideStyle).signature(17) }

################################################################################
# JOIN TABLE AS FSA
################################################################################
slide!('Join Table As FSA',
  center,
  staggeredOverlay(true,
    fsa(true),
    fsa(false),
  nil),
  left,
nil){ |slide| slide.label('natlog_fsa').slideStyle(slideStyle).signature(77) }

################################################################################
# MANY ANTECEDENTS
################################################################################
slide!('Step 2: Handle Many Antecedents',
  h1('Can no longer rely on alignment'),
  pause,
  staggeredOverlay(true,
    rtable(
      ind('\textit{An Italian became the world\'s greatest tenor.}'),
      ind('\textit{There was an Italian who became the world\'s greatest tenor.}'),
    nil),
    rtable(
      ind('\textit{\blue{An} \green{Italian} \orange{became} \purple{the world\'s greatest tenor.}}'),
      ind('\textit{\blue{There was an} \green{Italian} \orange{who became} \purple{the world\'s greatest tenor.}}'),
      '',
      ind('Easy: \blue{An} \equivalent \blue{There was an}'),
      ind('Easy: \orange{became} \equivalent \orange{who became}'),
    nil),
    rtable(
      ind('\textit{Several delegates published \red{in major national newspapers.}}'),
      ind('\textit{Several delegates published.}'),
      '',
      ind('Easy to recognize that \textit{in a major newspaper} is a PP'),
    nil),
  nil),

  pause,
  h1('Query candidates and align?'),
  ind('Not unreasonable'),
  pause,
  ind('Surface forms may be very different'),
  ind(ind('All animals have tails $\rightarrow$ Some cats posess appendages')),
  pause,
  ind(ctable(image('../img/calvin_whinning.png').scale(0.5), 'But I want joint inference!').rjustify('c')),
nil){ |slide| slide.label('search_motivation').slideStyle(slideStyle).signature(23) }

################################################################################
# INFERENCE AS SEARCH
################################################################################
slide!('Inference As Search',
  describe(
    ['Nodes', 
      overlay(
        _('Candidate fact').level(0, 2),
        _('Candidate fact \textbf{\darkblue{\& index}}').level(2, 4),
        _('Candidate fact \& index').level(4),
        _('Candidate fact \& index \textbf{\darkblue{\& infer state}}').level(12),
      nil)],
    ['Start State', 'The \textbf{consequent}: the query fact'],
    ['End States', 'The antecedents in the database'],
    ['Edges', 'A mutation of a fact into another fact'],
    [_('Edge costs').level(9), _('$f_e * c_{e,s}$').level(10)],
  nil),
  '',

  overlay(

    rtable(
      h1('Subtlety 1: Edge definition').level(1,4),
      ind('Wordnet hierarchy / antonyms / etc. are \textbf{word} mutation.').level(1,4),
      ind('Now trivial:').level(3,4),
      ind(ind('Mutate word at mutation index')).level(3,4),
      ind(ind('Delete word at mutation index')).level(3,4),
      ind(ind('\darkred{Insert word at mutation index} (err...)')).level(3,4),
    nil),
    
    rtable(
      h1('Subtlety 2: Insertions').level(4,9),
      ind('Store antecedents as a Trie').level(4,9),
      ind('Intuition: building an antecedent left-to-right').level(5,9),
      ind(ind('Lookup fact$[0\dots$mutation\_index$]$; add completions from Trie')).level(5,9),
      overlay(
        ind(ind(table(
          ['\textbf{Search State}:', '$($\textit{some cats have tails}, 2$)$'],
          ['\textbf{Know fact}:', '\textit{some cats have furry tails}'],
          ['\textbf{Propose Insertion}:', '\textit{furry}'],
        nil))).level(6,7),
        rtable(
          ind(ind(table(
            ['\textbf{Search State}:', '$($\textit{cats have tails}, \red{0}$)$'],
            ['\textbf{Know fact}:', '\textit{some cats have furry tails} $+$ 100M others'],
            ['\textbf{Propose Insertion}:', '\textit{some}'],
          nil))).level(7, 9),
          ind(ind('Propose anything that completes to \textit{cats $\dots$}')).level(8, 9),
        nil),
      nil),
    nil),
    
    rtable(
      h1('Subtlety 3: BFS from \textit{animal} to \textit{cat} is awful').level(9),
      ind('Not all edges should be weighted equally').level(9),
      ind('Really, 2 types of costs').level(10),
      ind(ind('$f_e$: How ``far\'\' the mutation is (e.g., \textit{cat} is close to \textit{feline})')).level(10),
      ind(ind('$c_{e,s}$: How ``expensive\'\' the mutation is (more on this later)')).level(10),
      ind('Looks a lot like features and weights -- they are').level(11),
      ind('$c_{e,\textbf{s}}$: cost depends on inference state').level(12),
    nil),
  nil),
nil){ |slide| slide.label('search_intro').slideStyle(slideStyle).signature(30) }

################################################################################
# SEARCH DETAILS
################################################################################
slide!('Search Details',
  h1('Some numbers'),
  ind(table(
    ['Vocabulary',  '188,298 words'],
    ['Edge types',  '36'],
    ['Edges',       '12,449,314 edges (\red{66 / word!})'],
    ['Ticks / sec', '200,000'],
  nil).cmargin(u(0.5)).rmargin(u(0.25))),
  pause,
  '',

  h1('Vocabulary'),
  ind('Linked to Wordnet and Richard\'s word vectors'),
  ind('Lemmatized; numbers abstracted'),
  '',
  pause,

  h1('Uniform Cost Search'),
  ind('Bloom filter cache over (fact gloss, index)'),
  ind('Trie for end state lookup'),
nil){ |slide| slide.label('search_details').slideStyle(slideStyle).signature(32) }

################################################################################
# SEARCH Quantifiers
################################################################################
slide!('Quantifiers?',
  h1('Hand-coded list'),
  ind('Special edges for inserting/deleting/mutating them'),
  '','',
  pause,

  h1('Open Question: Projectivity Change'),
  ind('Consider: \textit{all cats have tails} $\rightarrow$ \textit{some furry cats have tails}'),
  pause,
  ind(ind(ctable('\textbf{Search State}:', staggeredOverlay(true,
    ctable('$($', mono('some_^ furry_^ cats_^ have_^ tails_^'), '$,0)$').rjustify('r'),
    ctable('$($', mono('\blue{all}_^ furry_^ cats_^ have_^ tails_^'), '$,0)$').rjustify('r'),
    ctable('$($', mono('all_^ \red{furry}_^ cats_^ have_^ tails_^'), '$,0)$').rjustify('r'),
    ctable('$($', mono('\blue{all}_v \blue{furry}_v \blue{cats}_v have_^ tails_^'), '$,0)$').rjustify('r'),
    ctable('$($', mono('all_v cats_v have_^ tails_^'), '$,0)$').rjustify('r'),
  nil)).cmargin(u(0.25)).rjustify('c'))),
  pause,
  ind('Can we steal prior work?'),
  pause,
  ind('With alignment we could replace \textit{furry} first'),
  ind(ind('But this leads to duplicate paths in search')),
  pause,
   ind('A parse tree would solve the problem'),
  '',
  pause,
  ind('\textbf{Current solution:} Godawful hacks to guess monotonicity.'),
nil){ |slide| slide.label('search_quantifiers').slideStyle(slideStyle).signature(15) }

################################################################################
# FUZZY INFERENCE
################################################################################
slide!('Step 3: Fuzzy Inference',
  describe(['Input',  'A query common sense fact'],
           ['',       'A large (noisy) database of facts'],
           ['Output', 'True or false, \textbf{with some confidence}']),
  '',
  pause,

  h1('Often ``probably true\'\' is good enough'),
  ind('\textit{Cats have tails} $\rightarrow$ \textit{dogs have tails}'),
  '',
  pause,

  h1('Two main mechanisms'),
  ind('1. Replace Unk state with cost penalty'),
  ind(staggeredOverlay(true,
    ind(ind(fsa(false).scale(0.5))),
    '2. Introduce nearest neighbors edges',
  nil)),
nil){ |slide| slide.label('fuzzy_intro').slideStyle(slideStyle).signature(7) }

################################################################################
# FUZZY INFERENCE
################################################################################
slide!('Neat Result: Generalize Similarities',
  '\textbf{\darkred{From last year:}} $x$ \textit{has tail}; $x$ similar to $y$; $y$ \textit{has tail}',
  '',
  pause,
  
  h1('Upper bound distributional similarity'),
  ind('Angle (arc-cosine of cosine) similarity is distance metric'),
  '',
  pause,

  h1('Generalize JC similarity'),
  ctable(
    Parse.new(['animal', ['feline', '\darkred{cat}'], ['canine', '\darkred{dog}']]).constituency,
    pause,
    rtable(
      '$dist_{jc} = \log\left( \frac{p(\textrm{animal})^2}{ p(\textrm{cat}) \cdot p(\textrm{dog}) } \right)$',
      pause,
      '$~~~~~~~\:= \log\left( 
         \frac{\darkgreen{p(\textrm{fel.})}}{p(\textrm{cat})} \cdot
         \frac{p(\textrm{an.})}{\darkgreen{p(\textrm{fel.})}} \cdot
         \frac{p(\textrm{an.})}{\darkorange{p(\textrm{can.})}} \cdot
         \frac{\darkorange{p(\textrm{can.})}}{p(\textrm{dog})}\right)$',
       '$~$',
       pause,
       '$f_{\textrm{feline}\rightarrow\textrm{cat}} = \frac{p(feline)}{p(cat)}$',
       '$~$',
       pause,
       'If $w_{\textrm{feline}\rightarrow\textrm{cat}}$ is 1, get exactly $dist_{jc}$',
    nil).rjustify('c'),
  nil).cmargin(u(1.0)).rjustify('c'),

nil){ |slide| slide.label('fuzzy_jc').slideStyle(slideStyle).signature(28) }

################################################################################
# LEARNING
################################################################################
slide!('Step 4: Inference/Learning',
  header('Recall', 'Edge cost $f_e * c_{e,s}$'),
  '',
  pause,

  center,
  '$p(\textrm{True}) = \frac{1}{1 + \exp\left(w_0 - \sum\limits_i f_{e_i} \cdot (-c_{e_i,s})\right)}$',
  rtable(
    '$w_0 > 0$ (bias)',
    '$w_i = -c_{e_i,s} \le 0$',
  nil),
  left,
  pause,
  '','',

  h1('Weight in regression is negative cost in search'),
  pause,
  ind('But, not limited to these features'),
  ind('Lexicalize? Look at constituency match? etc.'),
nil){ |slide| slide.label('learn_infer').slideStyle(slideStyle).signature(11) }

################################################################################
# LEARNING
################################################################################
slide!('Learning',
  header('Currently non-existent.', 'Not that awful to hand-tune'),
  staggeredOverlay(true,
    ind(ind(image('../img/knobs.jpg').scale(0.33))),
    rtable(
      ind('Not hard to learn either (if we have data)'),
      ind('Predict held-out OpenIE facts vs. random facts'),
    nil),
  nil),
  pause,

  h1('Better ideas?'),
  pause,
  ind('Train on FraCaS'),
  pause,
  ind('MTurk a training set -- find false fact similar to true fact'),
  pause,
  ind('Bootstrap from small seed training set'),
nil){ |slide| slide.label('learn_learn').slideStyle(slideStyle).signature(8) }

################################################################################
# RESULTS
################################################################################
slide!('Results-ish',
  header('FraCaS', 'MacCartney\'s development data'),
  ind('Very small (346 problems; 183 w/single antecedent)'),
  ind('75 are relevant for NatLog inferences'),
  '','',
  pause,

  center,
  table(
    [h2('Subset'),     h2('Majority Class'), h2('MC 07'), h2('MC 08'), h2('Me')],
    ['All',        '61',             '--',    '--',    '41'],
    ['Applicable', '56',             '60',    '70',    '49'],
    ['NatLog',     '46',             '76',    '87',    '65'],
  nil).cjustify('lcrrr'),
  left,
  '',
  pause,

  header('Takeaway:', 'Guess Unk too often'),
nil){ |slide| slide.label('results_fracas').slideStyle(slideStyle).signature(3) }

################################################################################
# CONCLUSION
################################################################################
slide!('Feedback + Demo',
  describe(['Input',  'A query common sense fact'],
           ['',       'A large (noisy) database of facts'],
           ['Output', 'True or false, with some confidence']),
 '','',
 center,
 table(
   [
     fsa(false).scale(0.5),
     Parse.new(['animal', ['feline', '\darkred{cat}'], ['canine', '\darkred{dog}']]).constituency,
   nil],[
     'Search over NatLog mutations',
     'Allow fuzzy inferences',
   nil],
 nil).cmargin(u(1.0)).rmargin(u(0.5)).cjustify('c'),
 '','',
 
 '$p(\textrm{True}) = \frac{1}{1 + \exp\left(w_0 - \sum\limits_i f_{e_i} \cdot (-c_{e_i,s})\right)}$',
 left,
nil){ |slide| slide.label('feedback').slideStyle(slideStyle).signature(13) }

################################################################################
# FINISH
################################################################################
finishPresentation


