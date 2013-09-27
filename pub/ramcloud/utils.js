G = sfig.serverSide ? global : this;
sfig.importAllMethods(G);

// Latex macros
sfig.latexMacro('R', 0, '\\mathbb{R}');
sfig.latexMacro('P', 0, '\\mathbb{P}');
sfig.latexMacro('E', 0, '\\mathbb{E}');
sfig.latexMacro('diag', 0, '\\text{diag}');

sfig.initialize();

G.prez = sfig.presentation();

// Useful functions (not standard enough to put into an sfig library).

G.frameBox = function(a) { return frame(a).padding(5).bg.strokeWidth(1).fillColor('white').end; }

G.bigLeftArrow = function() { return leftArrow(100).strokeWidth(10).color('brown'); }
G.bigRightArrow = function() { return rightArrow(100).strokeWidth(10).color('brown'); }
G.bigUpArrow = function() { return upArrow(100).strokeWidth(10).color('brown'); }
G.bigDownArrow = function() { return downArrow(100).strokeWidth(10).color('brown'); }
G.red = function(x) { return x.fontcolor('red'); }
G.green = function(x) { return x.fontcolor('green'); }
G.blue = function(x) { return x.fontcolor('blue'); }
G.darkblue = function(x) { return x.fontcolor('darkblue'); }
G.darkred = function(x) { return x.fontcolor('darkblue'); }

G.xseq = function() { return new sfig.Table([arguments]).center().margin(5); }
G.yseq = function() { return ytable.apply(null, arguments).margin(10); }

G.stmt = function(prefix, suffix) { return xtable(prefix.fontcolor('darkred') + ':',  suffix).xmargin(5).ycenter(); }
G.headerList = function(title) {
  var contents = Array.prototype.slice.call(arguments).slice(1);
  return ytable.apply(null, [title ? stmt(title) : _].concat(contents.map(function(line) {
    if (line == _) return _;
    if (typeof(line) == 'string') return bulletedText([null, line]);
    if (line instanceof sfig.PropertyChanger) return line;
    return indent(line);
  })));
}

G.node = function(x, shaded) { return overlay(circle(20).fillColor(shaded ? 'lightgray' : 'white') , x).center(); }
G.indent = function(x, n) { return frame(x).xpadding(n != null ? n : 20).xpivot(1); }
//G.stagger = function(b1, b2) {
//  b1 = std(b1);
//  b2 = std(b2);
//  return overlay(b1, pause(), b2.replace(b1));
//}

G.stagger = function() {
  var args = []
  var i = 0;
  for (arg in arguments) {
    if (arguments[arg].IGNORED) { continue; }
    args[i * 2] = std(arguments[arg]);
    if (i > 0) args[i * 2] = args[i * 2].replace(args[(i-1) * 2]);
    args [i * 2 + 1] = pause();
    i += 1;
  }
  args [args.length - 1] = _;  // clear the last pause();
  return overlay.apply(G, args);
}

G.staggeredHeadings = function() {
  var helper = function(args, i) {
    if (i == args.length) { return _; }
    if (args[i].IGNORED) {
      return helper(args, i + 1);
    } else {
      var recursive = helper(args, i + 1);
      return ytable(args[i][0],
                    stagger(ytable.apply(G, sfig.std(args[i].slice(1, args[i].length - 1)).map( function(x) { return indent(x) } )),
                            recursive));
    }
  }
  return helper(arguments, 0);
}

G.dividerSlide = function(text) {
  return slide(null, nil(), parentCenter(text)).titleHeight(0);
}

G.moveLeftOf = function(a, b, offset) { return transform(a).pivot(1, 0).shift(b.left().sub(offset == null ? 5 : offset), b.ymiddle()); }
G.moveRightOf = function(a, b, offset) { return transform(a).pivot(-1, 0).shift(b.right().add(offset == null ? 5 : offset), b.ymiddle()); }
G.moveTopOf = function(a, b, offset) { return transform(a).pivot(0, 1).shift(b.xmiddle(), b.top().sub(offset == null ? 5 : offset)); }
G.moveBottomOf = function(a, b, offset) { return transform(a).pivot(0, -1).shift(b.xmiddle(), b.bottom().add(offset == null ? 5 : offset)); }
G.moveCenterOf = function(a, b) { return transform(a).pivot(0, 0).shift(b.xmiddle(), b.ymiddle()); }



G.fact = function(arg1, rel, arg2, color) {
  return text('<b><font size="150%" style="Helvetica Neue,Helvetica,Arial,sans-serif" color="' + (color ? color : "#08c") + '">' + arg1 + '</font></b> ' +
         '<b><font style="Helvetica Neue,Helvetica,Arial,sans-serif" color="' + (color ? color : "#333") + '">' + rel + '</font></b> ' +
         '<b><font style="Helvetica Neue,Helvetica,Arial,sans-serif" color="' + (color ? color : "#08c") + '">' + arg2 + '</font></b>');
}
G.h = function(obj) {
  return (typeof(obj) == "string" ? text(obj) : obj).color('darkred');
}

G.mupImg = function() { return image("img/mup.png").scale(0.25); }
G.mdownImg = function() { return image("img/mdown.png").scale(0.25); }

G.mup = function(w) {
  return ytable(mupImg(), w).xcenter().ymargin(3)
}

G.mdown = function(w) {
  return ytable(mdownImg(), w).xcenter().ymargin(3)
}

G.natlog = function( parts ) {
  var upperRow = []
  var lowerRow = []
  parts.map( function( part ) {
    part[1].split(' ').map( function (w) {
      if (part[0] == 'up') upperRow.push(mupImg());
      else if (part[0] == 'down') upperRow.push(mdownImg());
      else upperRow.push('');
      lowerRow.push(w);
    });
  });
  return new sfig.Table([upperRow, lowerRow]).xmargin(5).ymargin(3).xcenter();
}

G.vennDiagram = function( lblA, lblB, type ) {
  var A = function(x, y) {
    return ellipse(x, y).strokeColor('blue').strokeWidth(3);
  }
  var B = function(x, y) {
    return ellipse(x, y).strokeColor('red').strokeWidth(3);
  }
  if (type == 'intersect') {
    return overlay(
      A(75, 50),
      B(75, 50).shift(60, 20),
      (typeof(lblA) == "string" ? text(lblA) : lblA).shift(-50, -90),
      (typeof(lblB) == "string" ? text(lblB) : lblB).shift(60, -60),
    _);
  } else if (type == 'subset') {
    return overlay(
      A(100, 60),
      B(40, 25).shift(25, 20),
      (typeof(lblA) == "string" ? text(lblA) : lblA).shift(-50, -95),
      (typeof(lblB) == "string" ? text(lblB) : lblB).shift(-60, -40),
    _);
  } else if (type == 'disjoint') {
    return overlay(
      A(50, 50),
      B(50, 50).shift(125, 0),
      (typeof(lblA) == "string" ? text(lblA) : lblA).shift(-50, -90),
      (typeof(lblB) == "string" ? text(lblB) : lblB).shift(80, -90),
    _);
  } else {
    throw new Exception("Unknown type: " + type);
  }
}
