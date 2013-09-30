// For node.js
require('./sfig/internal/sfig.js');
require('./sfig/internal/Graph.js');
require('./sfig/internal/RootedTree.js');
require('./sfig/internal/metapost.js');
require('./sfig/internal/seedrandom.js');
require('./utils.js');

//------------------------------------------------------------------------------
// Title
//------------------------------------------------------------------------------
prez.addSlide(slide(nil(),
  ytable('Philosophers Are Mortal:',
         'Inferring The Truth Of Unseen Facts').center().scale(1.3).strokeColor('darkred'),
  ytable(
    text('Gabor Angeli').scale(1.2),
  _).center().ymargin(20),
  ytable(
    nowrapText('Or, "I can haz systems help?"'),
  _).center().scale(0.8),
_).body.center().end.showIndex(false).showHelp(true));

//------------------------------------------------------------------------------
// Motivation
//------------------------------------------------------------------------------
function check(correct, text) {
  return xtable(correct ? image('img/check.png').scale(0.05) : image('img/xmark.png').scale(0.05),
    text).xmargin(20);
}
function ex(correct, text) { return indent(check(correct, text)); }
prez.addSlide(slide('Introduction',
  ytable(
    stmt('Elevator Pitch', 'determine if an arbitrary fact is "true"'),
    indent('<i>("true" is kind of complicated -- but that\'s another issue!)</i>'),
  _),
  pause(),
  'Examples'.fontcolor('darkred'),
  staggeredHeadings(
    [ex(true, 'Philosophers are mortal'),
      indent(image('img/socrates.jpg').scale(0.40)),
     _],
    [ex(true, 'Cats have tails'),
      indent(image('img/cat.png').scale(0.5)),
     _],
    [ytable(
//      'More practical examples'.fontcolor('darkred'),
      ex(true, 'Politicians are employees of countries'),
      ex(true, 'Plane crash may cause death'))],
    [ytable(
      ex(false, 'Politicians are born in Baby Boomer'),
      ex(false, 'Fright may cause death'))],
    [stmt('Goal', 'handle <b>any</b> query fact')],
    [stmt('Goal', 'reduce to logical inference when possible')],
    [stmt('Goal', 'infer falsehood')],
  _),
  pause(),
_));

//------------------------------------------------------------------------------
// OpenIE
//------------------------------------------------------------------------------
prez.addSlide(slide('Have Large Database Of Facts',
  stmt('Open Information Extraction', ''),
  indent('<i>I can haz cheezburger?</i> $\\rightarrow$ (I, can haz, cheezburger)'),
  pause(),
  stmt('Hypothesis', 'if it\'s on the internet, it\'s true'),
  pause(),
  '',
  parentCenter('<i>Is a philosopher mortal?</i>'),
  pause(),
  '',
  parentCenter(image('img/wiki-philosophermortal.png').scale(0.60)),
  parentCenter(text('(ReVerb run on Wikipedia)').scale(0.75)),
  parentCenter(text('What about unseen facts?').color('darkred')),
_));

//------------------------------------------------------------------------------
// More Data
//------------------------------------------------------------------------------
prez.addSlide(slide('Option 1: More Data',
  stagger(
    parentCenter(xtable(image('img/clouds.png').scale(0.25), rightArrow(100), image('img/database.png').scale(0.10))).ycenter().xmargin(50),
    parentCenter(image('img/reverb-clueweb12.png').scale(0.64)),
  _).center(),
  parentCenter(text('(ReVerb run on ClueWeb12)').scale(0.75)),
  pause(),
  stmt('Always more facts', '<i>Is Aristotle mortal?</i>'),
_));

//------------------------------------------------------------------------------
// Inference
//------------------------------------------------------------------------------
prez.addSlide(slide('Option 2: Inference',
  parentCenter(ytable(
//    parentCenter(image('img/reverb-header.png').scale(0.60)),
    table(["(1)", fact('Human', 'are', 'mortals').scale(0.66)],//image('img/reverb-humanmortal.png').scale(0.75)],
          ["(2)", fact('all philosophers', 'is', 'human').scale(0.66)],//image('img/reverb-philosopherhuman.png').scale(0.75)],
          ['$\\therefore$', fact('Philosophers', 'are', 'mortal').scale(0.66)],
          pause(),
          ["(3)", fact('Aristotle', 'was', 'a Greek philosopher').scale(0.66)],//image('img/reverb-aristotlephilosopher.png').scale(0.75)],
          ['$\\therefore$', fact('Aristotle', 'is', 'mortal').scale(0.66)],
        _).ycenter().ymargin(3).xmargin(10),
    _)),

  pause(),

  staggeredHeadings(
    [h('Many little hidden inferences'),
      xtable('X is <b>a mortal human</b>', rightArrow(25), "X is <b>mortal</b>").ycenter().xmargin(10),
      xtable('X was <b>a Greek philosopher</b>', rightArrow(25), "X is <b>a philosopher</b>").ycenter().xmargin(10),
     _],
    [h('Always more facts'),
        xtable(
          fact('Philosophers', 'give', 'lectures').scale(0.5),
          fact('Philosophers', 'think about', 'causality').scale(0.5),
          _).margin(25),
        xtable(
          fact('Philosophers', 'sit in', 'armchairs').scale(0.5),
          fact('Philosophers', 'respect', 'Plato').scale(0.5),
          _).margin(25),
      _],
    [h('Hard to handle uninferrable facts'), ''],
  _),
_));

//------------------------------------------------------------------------------
// Similarity
//------------------------------------------------------------------------------
prez.addSlide(slide('Option Meh: Textual Similarity',
  parentCenter(table(["(1)", fact('Socrates', 'is', 'mortal').scale(0.66)],//image('img/reverb-humanmortal.png').scale(0.75)],
        ["(2)", fact('"Aristotle"', 'is similar to', '"Socrates"').scale(0.66)],//image('img/reverb-philosopherhuman.png').scale(0.75)],
        ['$\\therefore$', fact('Aristotle', 'is', 'mortal').scale(0.66)],
      _).ycenter().ymargin(3).xmargin(10)),
  pause(),
  stmt('Pros', 'Respectable accuracy; handles any fact'),
  stmt('Cons', 'It\'s a bunch of hippie nonsense'),
  pause(),
  stagger(
    xtable(image("img/cat_wings.jpg").scale(0.33),
      table(["(1)", fact('no cat', 'has', 'wings').scale(0.66)],//image('img/reverb-humanmortal.png').scale(0.75)],
            ["(2)", fact('"no cat"', 'is similar to', '"cat"').scale(0.66)],//image('img/reverb-philosopherhuman.png').scale(0.75)],
            ['$\\therefore$', fact('cat', 'has', 'wings').scale(0.66)],
          _).ycenter().ymargin(3).xmargin(10),
    _),
    xtable(image("img/human_tail.gif").scale(0.33),
      table(["(1)", fact('cat', 'has', 'a tail').scale(0.66)],//image('img/reverb-humanmortal.png').scale(0.75)],
            ["(2)", fact('"cat"', 'is similar to', '"human"').scale(0.66)],//image('img/reverb-philosopherhuman.png').scale(0.75)],
            ['$\\therefore$', fact('human', 'has', 'a tail').scale(0.66)],
          _).ycenter().ymargin(3).xmargin(10),
    _),
  _),
_).leftFooter("(Presented at CoNLL 2013)"));

//------------------------------------------------------------------------------
// NatLog Intro
//------------------------------------------------------------------------------
prez.addSlide(slide('Option Awesome: Natural Logic',
  stmt('Big Idea', 'Sequence of <i>textual edits</i> preserve truth'),
  pause(),

  staggeredHeadings(
    [stmt('Predicate', 'A subset of objects in the world'),
      '&nbsp;',
      '&nbsp;',
      '&nbsp;',
      stagger(
        indent(indent(indent(indent(
          vennDiagram('All the things!', 'Cat$(x)$', 'subset'))))),
        indent(indent(indent(indent(
          vennDiagram('All the things!', text('Play-with-yarn$(x)$').scale(0.60), 'subset'))))),
        indent(indent(indent(indent(
          vennDiagram('Cat$(x)$', 'Fluffy$(x)$', 'intersect'))))),
        indent(indent(indent(indent(
          vennDiagram('Cat$(x)$', text('Fluffy-cat$(x)$').scale(0.9), 'subset'))))),
        indent(indent(indent(indent(
          vennDiagram('Cat$(x)$', 'Dog$(x)$', 'disjoint'))))),
      _),
    _],
    [stmt('Downward monotone', xtable( '(', mdownImg(), ')', 'Cat $\\rightarrow$ Fluffy cat').xmargin(10)),
      '&nbsp;',
      '&nbsp;',
      indent(indent(indent(indent(
        vennDiagram('Cat', 'Fluffy cat', 'subset'))))),
    _],
    [stmt('Upward monotone', xtable( '(', mupImg(), ')', 'Fluffy cat $\\rightarrow$ Cat').xmargin(10)),
      '&nbsp;',
      stagger (
        indent(indent(indent(indent(
          vennDiagram('Cat', 'Fluffy cat', 'subset'))))),
        indent(indent(indent(indent(
          vennDiagram('Animal', 'Cat', 'subset'))))),
        indent(indent(indent(indent(
          vennDiagram('Cat', 'Fluffy cat', 'subset'))))),
      _),
    _],
  _),

  '&nbsp;',

  stagger(
    xtable('', natlog( [['', 'All'], ['down', 'cats'], ['up', 'play with yarn']] )).xmargin(50),
    check(true,
      xtable(
        natlog( [['', 'All'], ['down', 'cats'], ['up', 'play with yarn']] ),
        sfig.rightArrow(15).scale(3),
        'All <b>fluffy cats</b> play with yarn',
      _).ycenter().xmargin(10)),
    check(false,
      xtable(
        natlog( [['', 'All'], ['down', 'cats'], ['up', 'play with yarn']] ),
        sfig.rightArrow(15).scale(3),
        'All <b>animals</b> play with yarn',
      _).ycenter().xmargin(10)),
    check(true,
      xtable(
        natlog( [['', 'All'], ['down', 'cats'], ['up', 'play with yarn']] ),
        sfig.rightArrow(15).scale(3),
        'All cats <b>interact with</b> yarn',
      _).ycenter().xmargin(10)),
    check(true,
      xtable(
        natlog( [['', 'All'], ['down', 'cats'], ['up', 'play with yarn']] ),
        sfig.rightArrow(15).scale(3),
        'All cats play with <b>toys</b>',
      _).ycenter().xmargin(10)),
    check(false,
      xtable(
        natlog( [['', 'All'], ['down', 'cats'], ['up', 'play with yarn']] ),
        sfig.rightArrow(15).scale(3),
        'All cats play with <b>pink</b> yarn',
      _).ycenter().xmargin(10)),
  _),


_));

//------------------------------------------------------------------------------
// Entailment As Search
//------------------------------------------------------------------------------
function stateDemo(cost, arg1, rel, arg2) {
  return table(['<b>Cost</b>', indent('<b>State</b>')],
    [cost, fact(arg1, rel, arg2)]).xmargin(25).ymargin(10).ycenter();
}
prez.addSlide(slide('Entailment As Search',
  ytable(
    stmt('State', xtable('A fact (e.g.,', fact('cat', 'has', 'tail').scale(0.75), ')').xmargin(3).ycenter()),
    pause(),
    stmt('Start State', xtable('The query fact (e.g.,', fact('blue cats', 'have', 'tails').scale(0.75), ')').xmargin(3).ycenter()),
    stmt('End State', xtable('A known fact (e.g.,', fact('All animals', 'have', 'tails').scale(0.75), ')').xmargin(3).ycenter()),
    pause(),
    stmt('Edge', xtable('Likely valid edit (e.g.,', fact('cat', 'has', 'tail').scale(0.75), sfig.rightArrow(25).scale(1.5), fact('animal', 'has', 'tail').scale(0.75), ')').xmargin(3).ycenter()),
    indent('Search from the consequent to a valid antecedent'),
    pause(),
    stmt('Edge Cost', xtable(image('img/magic.jpg').scale(0.25), 'Magic! (likelihood of validity)').ycenter()),
    pause(),
    '&nbsp;',
    parentCenter(stagger(
      stateDemo(0.0, 'blue cats', 'have', 'tails'),
      stateDemo(0.1, 'cats', 'have', 'tails'),
      stateDemo(0.8, 'cat', 'have', 'tails'),
      stateDemo(1.3, 'animal', 'have', 'tails'),
      stateDemo(2.3, 'animals', 'have', 'tails'),
      stateDemo(3.8, 'All animals', 'have', 'tails'),
    _)),
  _),
_));

//------------------------------------------------------------------------------
// NLP Details
//------------------------------------------------------------------------------
var T = sfig.rootedTree
prez.addSlide(slide('Exciting NLP details',
  ytable(
    h('Edge cost correspond to log(P(truth))'),
    indent(indent(indent(indent(ytable(
      '$P(true) \\propto e^{\\theta^T \\cdot \\phi}$',
      indent('$\\theta \\geq 0$ (learned)'),
      indent('$\\phi \\geq 0$ (features of search path)'),
    _))))),
    pause(),
    indent('<i>Partial path has partial $\\phi$; $\\theta$ given $\\rightarrow$ $cost=\\theta^T \\cdot \\phi$</i>'),
  _),
  pause(),

  stmt('Online Learning', 'Learning $\\theta$ improves search'),
  pause(),

  ytable(
    h('Generalize textual similarity'),
    indent(xtable(
      T('Animal', T('Feline', text('Cat').color('red')), T('Canine', text('Dog').color('red'))).scale(0.9),
      '$dist_{jc} = \\log\\left(\\frac{P(Animal)^2}{P(Cat) \\cdot P(Dog)}\\right)$',
    _).center().xmargin(50)),
  _),
_));

//------------------------------------------------------------------------------
// Systems Details
//------------------------------------------------------------------------------
prez.addSlide(slide('Problem Size',
  ytable(
    h('Vocabulary'),
    indent('20 million words'),
    indent('Average 11 edits per word'),
    indent('$>100$ edits for 100k most common words'),
    indent('$\\sim 10$ words per fact '),
  _),
  
  ytable(
    h('Fact database'),
    indent('$>1$ billion known facts'),
    indent('$>150$ GB in DB; 750 GB in text'),
  _),
  
  ytable(
    h('Deeper, faster search $\\rightarrow$ better performance'),
    indent('Depth 3 = $\\Omega( (100 * 10)^3 )$ edits'),
    indent('Deletions are easy'),
    indent('Insertions? (likely, quantifiers + neighboring facts)'),
  _),
_));



// For node.js: output PDF (you must have Metapost installed).
prez.writePdf({outPrefix: 'index'});
