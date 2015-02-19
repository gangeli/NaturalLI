#!/usr/bin/env python3
#

from socket import *
from threading import Lock
from concurrent.futures import ThreadPoolExecutor
from copy import copy
import sys
import json
import os.path

TCP_IP = '127.0.0.1'
if len(sys.argv) > 1:
  TCP_IP = sys.argv[1]
TCP_PORT = 1337
if len(sys.argv) > 2:
  TCP_PORT = int(sys.argv[2])
PARALLELISM=4
if len(sys.argv) > 3:
  PARALLELISM = int(sys.argv[3])
IN_MODEL="/dev/null"
if len(sys.argv) > 4:
  IN_MODEL = sys.argv[4]
OUT_MODEL="/dev/null"
if len(sys.argv) > 5:
  OUT_MODEL = sys.argv[5]

# A bit of help text
print( "Connecting to %s:%u on %u threads" % (TCP_IP, TCP_PORT, PARALLELISM) )
print( "Enter a number of optional premises, separated by newlines," )
print( "followed by a query and two newlines. Or, pipe one of the test" )
print( "case files in test/data/ ." )
print( "For example:" )
print( "" )
print( "All cats have tails." )
print( "Some animals have tails." )
print( "" )
print( "=> 'Some animals have tails.' is true (0.999075)" )
print( "" )
print( "vv Your input here (^D to exit) vv" )

"""
  Creates an initial weight array for encoding soft natural logic costs.
  @return An array of costs, which can be passed into, e.g.,
          costsPreamble()
"""
def softNatlogCosts(constant=0.001, good=0.01, soft=0.1, bad=1.0):
  # Initialize to defaults
  costs = [0.001] * 353
  # Mutation costs
  costs[0] = soft  # Angle NN
  costs[1] = soft  # Verb Entail
  for i in range(2,14):
    costs[i] = constant
  # Transition costs
  costs[14] = good  # true: equivalent
  costs[15] = good  # true: forward
  costs[16] = bad   # true: reverse
  costs[17] = good  # true: negation
  costs[18] = good  # true: alternation
  costs[19] = bad   # true: cover
  costs[20] = bad   # true: independence
  costs[21] = good  # false: equivalent
  costs[22] = bad   # false: forward
  costs[23] = good  # false: reverse
  costs[24] = good  # false: negation
  costs[25] = bad   # false: alternation
  costs[26] = good  # false: cover
  costs[27] = bad   # false: independence
  # Insertion costs
  for i in range(28,353):
    costs[i] = constant
  costs[119] = 1.0  # special case for 'for'
  # Return
  return costs


"""
  Do a single update.
  @param costs The current costs vector
  @param features The feature vector of the example, or None if
         no vector was found.
  @param discount If true, we should discount all costs.
"""
def update(costs, features, discount):
  global COSTS
  global COSTS_LOCK
  COSTS_LOCK.acquire();
  if discount:
    for i in range(0,353):
      COSTS[i] -= 0.1
      if COSTS[i] < 0.001:
        COSTS[i] = 0.001
  else:
    for i in range(0,353):
      COSTS[i] += 0.1 * float(features[i])
  COSTS_LOCK.release();


"""
  Convert the response from the search process into a feature vector,
  matching the dimensions of the cost vector.
"""
def mkFeatureVector(mutate, transFromTrue, transFromFalse, insert):
  features = [0] * 353
  for i in range(0, 14):
    features[i] = mutate[i];
  for i in range(14, 21):
    features[i] = transFromTrue[i - 14];
  for i in range(21, 28):
    features[i] = transFromFalse[i - 21];
  for i in range(28, 28 + len(insert)):
    features[i] = insert[i - 28];
  return features


"""
  Defines the preamble string for soft NatLog costs.
  @param  A raw array of costs. This can be initialized with, e.g.,
          softNatlogCosts()
  @return A list of preamble Strings.
"""
def costsPreamble(costs):
  return [
      '%skipNegationSearch = true',
      '%mutationLexicalCost @ angle_nn = '          + str(costs[0]),
      '%mutationLexicalCost @ verb_entail = '       + str(costs[1]),
      '%mutationLexicalCost @ antonym = '           + str(costs[2]),
      '%mutationLexicalCost @ holonym = '           + str(costs[3]),
      '%mutationLexicalCost @ hyponym = '           + str(costs[4]),
      '%mutationLexicalCost @ meronym = '           + str(costs[5]),
      '%mutationLexicalCost @ quantifier_down = '   + str(costs[6]),
      '%mutationLexicalCost @ quantifier_negate = ' + str(costs[7]),
      '%mutationLexicalCost @ quantifier_reword = ' + str(costs[8]),
      '%mutationLexicalCost @ quantifier_up = '     + str(costs[9]),
      '%mutationLexicalCost @ sense_add = '         + str(costs[10]),
      '%mutationLexicalCost @ sense_remove = '      + str(costs[11]),
      '%mutationLexicalCost @ similar = '           + str(costs[12]),
      '%mutationLexicalCost @ synonym = '           + str(costs[13]),
      '%transitionCostFromTrue @ equivalent = '          + str(costs[14]),
      '%transitionCostFromTrue @ forward_entailment = '  + str(costs[15]),
      '%transitionCostFromTrue @ reverse_entailment = '  + str(costs[16]),
      '%transitionCostFromTrue @ negation = '            + str(costs[17]),
      '%transitionCostFromTrue @ alternation = '         + str(costs[18]),
      '%transitionCostFromTrue @ cover = '               + str(costs[19]),
      '%transitionCostFromTrue @ independence = '        + str(costs[20]),
      '%transitionCostFromFalse @ equivalent = '         + str(costs[21]),
      '%transitionCostFromFalse @ forward_entailment = ' + str(costs[22]),
      '%transitionCostFromFalse @ reverse_entailment = ' + str(costs[23]),
      '%transitionCostFromFalse @ negation = '           + str(costs[24]),
      '%transitionCostFromFalse @ alternation = '        + str(costs[25]),
      '%transitionCostFromFalse @ cover = '              + str(costs[26]),
      '%transitionCostFromFalse @ independence = '       + str(costs[27]),
      '%insertionLexicalCost @ acomp = ' + str(costs[28]),
      '%insertionLexicalCost @ advcl = ' + str(costs[29]),
      '%insertionLexicalCost @ advmod = ' + str(costs[30]),
      '%insertionLexicalCost @ agent = ' + str(costs[31]),
      '%insertionLexicalCost @ purpcl = ' + str(costs[32]),
      '%insertionLexicalCost @ amod = ' + str(costs[33]),
      '%insertionLexicalCost @ appos = ' + str(costs[34]),
      '%insertionLexicalCost @ aux = ' + str(costs[35]),
      '%insertionLexicalCost @ auxpass = ' + str(costs[36]),
      '%insertionLexicalCost @ cc = ' + str(costs[37]),
      '%insertionLexicalCost @ ccomp = ' + str(costs[38]),
      '%insertionLexicalCost @ conj = ' + str(costs[39]),
      '%insertionLexicalCost @ cop = ' + str(costs[40]),
      '%insertionLexicalCost @ csubj = ' + str(costs[41]),
      '%insertionLexicalCost @ csubjpass = ' + str(costs[42]),
      '%insertionLexicalCost @ dep = ' + str(costs[43]),
      '%insertionLexicalCost @ det = ' + str(costs[44]),
      '%insertionLexicalCost @ discourse = ' + str(costs[45]),
      '%insertionLexicalCost @ dobj = ' + str(costs[46]),
      '%insertionLexicalCost @ expl = ' + str(costs[47]),
      '%insertionLexicalCost @ goeswith = ' + str(costs[48]),
      '%insertionLexicalCost @ iobj = ' + str(costs[49]),
      '%insertionLexicalCost @ mark = ' + str(costs[50]),
      '%insertionLexicalCost @ mwe = ' + str(costs[51]),
      '%insertionLexicalCost @ neg = ' + str(costs[52]),
      '%insertionLexicalCost @ nn = ' + str(costs[53]),
      '%insertionLexicalCost @ npadvmod = ' + str(costs[54]),
      '%insertionLexicalCost @ nsubj = ' + str(costs[55]),
      '%insertionLexicalCost @ nsubjpass = ' + str(costs[56]),
      '%insertionLexicalCost @ num = ' + str(costs[57]),
      '%insertionLexicalCost @ number = ' + str(costs[58]),
      '%insertionLexicalCost @ parataxis = ' + str(costs[59]),
      '%insertionLexicalCost @ pcomp = ' + str(costs[60]),
      '%insertionLexicalCost @ pobj = ' + str(costs[61]),
      '%insertionLexicalCost @ poss = ' + str(costs[62]),
      '%insertionLexicalCost @ possessive = ' + str(costs[63]),
      '%insertionLexicalCost @ preconj = ' + str(costs[64]),
      '%insertionLexicalCost @ predet = ' + str(costs[65]),
      '%insertionLexicalCost @ prep = ' + str(costs[66]),
      '%insertionLexicalCost @ prt = ' + str(costs[67]),
      '%insertionLexicalCost @ punct = ' + str(costs[68]),
      '%insertionLexicalCost @ quantmod = ' + str(costs[69]),
      '%insertionLexicalCost @ rcmod = ' + str(costs[70]),
      '%insertionLexicalCost @ root = ' + str(costs[71]),
      '%insertionLexicalCost @ tmod = ' + str(costs[72]),
      '%insertionLexicalCost @ vmod = ' + str(costs[73]),
      '%insertionLexicalCost @ partmod = ' + str(costs[74]),
      '%insertionLexicalCost @ infmod = ' + str(costs[75]),
      '%insertionLexicalCost @ xcomp = ' + str(costs[76]),
      '%insertionLexicalCost @ conj_and = ' + str(costs[77]),
      '%insertionLexicalCost @ conj_and\\/or = ' + str(costs[78]),
      '%insertionLexicalCost @ conj_both = ' + str(costs[79]),
      '%insertionLexicalCost @ conj_but = ' + str(costs[80]),
      '%insertionLexicalCost @ conj_or = ' + str(costs[81]),
      '%insertionLexicalCost @ conj_nor = ' + str(costs[82]),
      '%insertionLexicalCost @ conj_plus = ' + str(costs[83]),
      '%insertionLexicalCost @ conj_x = ' + str(costs[84]),
      '%insertionLexicalCost @ prep_aboard = ' + str(costs[85]),
      '%insertionLexicalCost @ prep_about = ' + str(costs[86]),
      '%insertionLexicalCost @ prep_above = ' + str(costs[87]),
      '%insertionLexicalCost @ prep_across = ' + str(costs[88]),
      '%insertionLexicalCost @ prep_after = ' + str(costs[89]),
      '%insertionLexicalCost @ prep_against = ' + str(costs[90]),
      '%insertionLexicalCost @ prep_along = ' + str(costs[91]),
      '%insertionLexicalCost @ prep_alongside = ' + str(costs[92]),
      '%insertionLexicalCost @ prep_amid = ' + str(costs[93]),
      '%insertionLexicalCost @ prep_among = ' + str(costs[94]),
      '%insertionLexicalCost @ prep_anti = ' + str(costs[95]),
      '%insertionLexicalCost @ prep_around = ' + str(costs[96]),
      '%insertionLexicalCost @ prep_as = ' + str(costs[97]),
      '%insertionLexicalCost @ prep_at = ' + str(costs[98]),
      '%insertionLexicalCost @ prep_before = ' + str(costs[99]),
      '%insertionLexicalCost @ prep_behind = ' + str(costs[100]),
      '%insertionLexicalCost @ prep_below = ' + str(costs[101]),
      '%insertionLexicalCost @ prep_beneath = ' + str(costs[102]),
      '%insertionLexicalCost @ prep_beside = ' + str(costs[103]),
      '%insertionLexicalCost @ prep_besides = ' + str(costs[104]),
      '%insertionLexicalCost @ prep_between = ' + str(costs[105]),
      '%insertionLexicalCost @ prep_beyond = ' + str(costs[106]),
      '%insertionLexicalCost @ prep_but = ' + str(costs[107]),
      '%insertionLexicalCost @ prep_by = ' + str(costs[108]),
      '%insertionLexicalCost @ prep_concerning = ' + str(costs[109]),
      '%insertionLexicalCost @ prep_considering = ' + str(costs[110]),
      '%insertionLexicalCost @ prep_despite = ' + str(costs[111]),
      '%insertionLexicalCost @ prep_down = ' + str(costs[112]),
      '%insertionLexicalCost @ prep_during = ' + str(costs[113]),
      '%insertionLexicalCost @ prep_en = ' + str(costs[114]),
      '%insertionLexicalCost @ prep_except = ' + str(costs[115]),
      '%insertionLexicalCost @ prep_excepting = ' + str(costs[116]),
      '%insertionLexicalCost @ prep_excluding = ' + str(costs[117]),
      '%insertionLexicalCost @ prep_following = ' + str(costs[118]),
      '%insertionLexicalCost @ prep_for = ' + str(costs[119]),
      '%insertionLexicalCost @ prep_from = ' + str(costs[120]),
      '%insertionLexicalCost @ prep_if = ' + str(costs[121]),
      '%insertionLexicalCost @ prep_in = ' + str(costs[122]),
      '%insertionLexicalCost @ prep_including = ' + str(costs[123]),
      '%insertionLexicalCost @ prep_inside = ' + str(costs[124]),
      '%insertionLexicalCost @ prep_into = ' + str(costs[125]),
      '%insertionLexicalCost @ prep_like = ' + str(costs[126]),
      '%insertionLexicalCost @ prep_minus = ' + str(costs[127]),
      '%insertionLexicalCost @ prep_near = ' + str(costs[128]),
      '%insertionLexicalCost @ prep_of = ' + str(costs[129]),
      '%insertionLexicalCost @ prep_off = ' + str(costs[130]),
      '%insertionLexicalCost @ prep_on = ' + str(costs[131]),
      '%insertionLexicalCost @ prep_onto = ' + str(costs[132]),
      '%insertionLexicalCost @ prep_opposite = ' + str(costs[133]),
      '%insertionLexicalCost @ prep_out = ' + str(costs[134]),
      '%insertionLexicalCost @ prep_outside = ' + str(costs[135]),
      '%insertionLexicalCost @ prep_over = ' + str(costs[136]),
      '%insertionLexicalCost @ prep_past = ' + str(costs[137]),
      '%insertionLexicalCost @ prep_per = ' + str(costs[138]),
      '%insertionLexicalCost @ prep_plus = ' + str(costs[139]),
      '%insertionLexicalCost @ prep_regarding = ' + str(costs[140]),
      '%insertionLexicalCost @ prep_round = ' + str(costs[141]),
      '%insertionLexicalCost @ prep_save = ' + str(costs[142]),
      '%insertionLexicalCost @ prep_since = ' + str(costs[143]),
      '%insertionLexicalCost @ prep_than = ' + str(costs[144]),
      '%insertionLexicalCost @ prep_through = ' + str(costs[145]),
      '%insertionLexicalCost @ prep_throughout = ' + str(costs[146]),
      '%insertionLexicalCost @ prep_to = ' + str(costs[147]),
      '%insertionLexicalCost @ prep_toward = ' + str(costs[148]),
      '%insertionLexicalCost @ prep_towards = ' + str(costs[149]),
      '%insertionLexicalCost @ prep_under = ' + str(costs[150]),
      '%insertionLexicalCost @ prep_underneath = ' + str(costs[151]),
      '%insertionLexicalCost @ prep_unlike = ' + str(costs[152]),
      '%insertionLexicalCost @ prep_until = ' + str(costs[153]),
      '%insertionLexicalCost @ prep_up = ' + str(costs[154]),
      '%insertionLexicalCost @ prep_upon = ' + str(costs[155]),
      '%insertionLexicalCost @ prep_versus = ' + str(costs[156]),
      '%insertionLexicalCost @ prep_vs. = ' + str(costs[157]),
      '%insertionLexicalCost @ prep_via = ' + str(costs[158]),
      '%insertionLexicalCost @ prep_with = ' + str(costs[159]),
      '%insertionLexicalCost @ prep_within = ' + str(costs[160]),
      '%insertionLexicalCost @ prep_without = ' + str(costs[161]),
      '%insertionLexicalCost @ prep_whether = ' + str(costs[162]),
      '%insertionLexicalCost @ prep_according_to = ' + str(costs[163]),
      '%insertionLexicalCost @ prep_as_per = ' + str(costs[164]),
      '%insertionLexicalCost @ prep_compared_to = ' + str(costs[165]),
      '%insertionLexicalCost @ prep_instead_of = ' + str(costs[166]),
      '%insertionLexicalCost @ prep_preparatory_to = ' + str(costs[167]),
      '%insertionLexicalCost @ prep_across_from = ' + str(costs[168]),
      '%insertionLexicalCost @ prep_as_to = ' + str(costs[169]),
      '%insertionLexicalCost @ prep_compared_with = ' + str(costs[170]),
      '%insertionLexicalCost @ prep_irrespective_of = ' + str(costs[171]),
      '%insertionLexicalCost @ prep_previous_to = ' + str(costs[172]),
      '%insertionLexicalCost @ prep_ahead_of = ' + str(costs[173]),
      '%insertionLexicalCost @ prep_aside_from = ' + str(costs[174]),
      '%insertionLexicalCost @ prep_due_to = ' + str(costs[175]),
      '%insertionLexicalCost @ prep_next_to = ' + str(costs[176]),
      '%insertionLexicalCost @ prep_prior_to = ' + str(costs[177]),
      '%insertionLexicalCost @ prep_along_with = ' + str(costs[178]),
      '%insertionLexicalCost @ prep_away_from = ' + str(costs[179]),
      '%insertionLexicalCost @ prep_depending_on = ' + str(costs[180]),
      '%insertionLexicalCost @ prep_near_to = ' + str(costs[181]),
      '%insertionLexicalCost @ prep_pursuant_to = ' + str(costs[182]),
      '%insertionLexicalCost @ prep_alongside_of = ' + str(costs[183]),
      '%insertionLexicalCost @ prep_based_on = ' + str(costs[184]),
      '%insertionLexicalCost @ prep_except_for = ' + str(costs[185]),
      '%insertionLexicalCost @ prep_off_of = ' + str(costs[186]),
      '%insertionLexicalCost @ prep_regardless_of = ' + str(costs[187]),
      '%insertionLexicalCost @ prep_apart_from = ' + str(costs[188]),
      '%insertionLexicalCost @ prep_because_of = ' + str(costs[189]),
      '%insertionLexicalCost @ prep_exclusive_of = ' + str(costs[190]),
      '%insertionLexicalCost @ prep_out_of = ' + str(costs[191]),
      '%insertionLexicalCost @ prep_subsequent_to = ' + str(costs[192]),
      '%insertionLexicalCost @ prep_as_for = ' + str(costs[193]),
      '%insertionLexicalCost @ prep_close_by = ' + str(costs[194]),
      '%insertionLexicalCost @ prep_contrary_to = ' + str(costs[195]),
      '%insertionLexicalCost @ prep_outside_of = ' + str(costs[196]),
      '%insertionLexicalCost @ prep_such_as = ' + str(costs[197]),
      '%insertionLexicalCost @ prep_as_from = ' + str(costs[198]),
      '%insertionLexicalCost @ prep_close_to = ' + str(costs[199]),
      '%insertionLexicalCost @ prep_followed_by = ' + str(costs[200]),
      '%insertionLexicalCost @ prep_owing_to = ' + str(costs[201]),
      '%insertionLexicalCost @ prep_thanks_to = ' + str(costs[202]),
      '%insertionLexicalCost @ prep_as_of = ' + str(costs[203]),
      '%insertionLexicalCost @ prep_contrary_to = ' + str(costs[204]),
      '%insertionLexicalCost @ prep_inside_of = ' + str(costs[205]),
      '%insertionLexicalCost @ prep_preliminary_to = ' + str(costs[206]),
      '%insertionLexicalCost @ prep_together_with = ' + str(costs[207]),
      '%insertionLexicalCost @ prep_by_means_of = ' + str(costs[208]),
      '%insertionLexicalCost @ prep_in_case_of = ' + str(costs[209]),
      '%insertionLexicalCost @ prep_in_place_of = ' + str(costs[210]),
      '%insertionLexicalCost @ prep_on_behalf_of = ' + str(costs[211]),
      '%insertionLexicalCost @ prep_with_respect_to = ' + str(costs[212]),
      '%insertionLexicalCost @ prep_in_accordance_with = ' + str(costs[213]),
      '%insertionLexicalCost @ prep_in_front_of = ' + str(costs[214]),
      '%insertionLexicalCost @ prep_in_spite_of = ' + str(costs[215]),
      '%insertionLexicalCost @ prep_on_top_of = ' + str(costs[216]),
      '%insertionLexicalCost @ prep_in_addition_to = ' + str(costs[217]),
      '%insertionLexicalCost @ prep_in_lieu_of = ' + str(costs[218]),
      '%insertionLexicalCost @ prep_on_account_of = ' + str(costs[219]),
      '%insertionLexicalCost @ prep_with_regard_to = ' + str(costs[220]),
      '%insertionLexicalCost @ prepc_aboard = ' + str(costs[221]),
      '%insertionLexicalCost @ prepc_about = ' + str(costs[222]),
      '%insertionLexicalCost @ prepc_above = ' + str(costs[223]),
      '%insertionLexicalCost @ prepc_across = ' + str(costs[224]),
      '%insertionLexicalCost @ prepc_after = ' + str(costs[225]),
      '%insertionLexicalCost @ prepc_against = ' + str(costs[226]),
      '%insertionLexicalCost @ prepc_along = ' + str(costs[227]),
      '%insertionLexicalCost @ prepc_amid = ' + str(costs[228]),
      '%insertionLexicalCost @ prepc_among = ' + str(costs[229]),
      '%insertionLexicalCost @ prepc_anti = ' + str(costs[230]),
      '%insertionLexicalCost @ prepc_around = ' + str(costs[231]),
      '%insertionLexicalCost @ prepc_as = ' + str(costs[232]),
      '%insertionLexicalCost @ prepc_at = ' + str(costs[233]),
      '%insertionLexicalCost @ prepc_before = ' + str(costs[234]),
      '%insertionLexicalCost @ prepc_behind = ' + str(costs[235]),
      '%insertionLexicalCost @ prepc_below = ' + str(costs[236]),
      '%insertionLexicalCost @ prepc_beneath = ' + str(costs[237]),
      '%insertionLexicalCost @ prepc_beside = ' + str(costs[238]),
      '%insertionLexicalCost @ prepc_besides = ' + str(costs[239]),
      '%insertionLexicalCost @ prepc_between = ' + str(costs[240]),
      '%insertionLexicalCost @ prepc_beyond = ' + str(costs[241]),
      '%insertionLexicalCost @ prepc_but = ' + str(costs[242]),
      '%insertionLexicalCost @ prepc_by = ' + str(costs[243]),
      '%insertionLexicalCost @ prepc_concerning = ' + str(costs[244]),
      '%insertionLexicalCost @ prepc_considering = ' + str(costs[245]),
      '%insertionLexicalCost @ prepc_despite = ' + str(costs[246]),
      '%insertionLexicalCost @ prepc_down = ' + str(costs[247]),
      '%insertionLexicalCost @ prepc_during = ' + str(costs[248]),
      '%insertionLexicalCost @ prepc_except = ' + str(costs[249]),
      '%insertionLexicalCost @ prepc_excepting = ' + str(costs[250]),
      '%insertionLexicalCost @ prepc_excluding = ' + str(costs[251]),
      '%insertionLexicalCost @ prepc_far_from = ' + str(costs[252]),
      '%insertionLexicalCost @ prepc_following = ' + str(costs[253]),
      '%insertionLexicalCost @ prepc_for = ' + str(costs[254]),
      '%insertionLexicalCost @ prepc_from = ' + str(costs[255]),
      '%insertionLexicalCost @ prepc_in = ' + str(costs[256]),
      '%insertionLexicalCost @ prepc_inside = ' + str(costs[257]),
      '%insertionLexicalCost @ prepc_into = ' + str(costs[258]),
      '%insertionLexicalCost @ prepc_like = ' + str(costs[259]),
      '%insertionLexicalCost @ prepc_minus = ' + str(costs[260]),
      '%insertionLexicalCost @ prepc_near = ' + str(costs[261]),
      '%insertionLexicalCost @ prepc_of = ' + str(costs[262]),
      '%insertionLexicalCost @ prepc_off = ' + str(costs[263]),
      '%insertionLexicalCost @ prepc_on = ' + str(costs[264]),
      '%insertionLexicalCost @ prepc_onto = ' + str(costs[265]),
      '%insertionLexicalCost @ prepc_opposite = ' + str(costs[266]),
      '%insertionLexicalCost @ prepc_outside = ' + str(costs[267]),
      '%insertionLexicalCost @ prepc_over = ' + str(costs[268]),
      '%insertionLexicalCost @ prepc_past = ' + str(costs[269]),
      '%insertionLexicalCost @ prepc_per = ' + str(costs[270]),
      '%insertionLexicalCost @ prepc_plus = ' + str(costs[271]),
      '%insertionLexicalCost @ prepc_regarding = ' + str(costs[272]),
      '%insertionLexicalCost @ prepc_round = ' + str(costs[273]),
      '%insertionLexicalCost @ prepc_save = ' + str(costs[274]),
      '%insertionLexicalCost @ prepc_since = ' + str(costs[275]),
      '%insertionLexicalCost @ prepc_than = ' + str(costs[276]),
      '%insertionLexicalCost @ prepc_through = ' + str(costs[277]),
      '%insertionLexicalCost @ prepc_to = ' + str(costs[278]),
      '%insertionLexicalCost @ prepc_toward = ' + str(costs[279]),
      '%insertionLexicalCost @ prepc_towards = ' + str(costs[280]),
      '%insertionLexicalCost @ prepc_under = ' + str(costs[281]),
      '%insertionLexicalCost @ prepc_underneath = ' + str(costs[282]),
      '%insertionLexicalCost @ prepc_unlike = ' + str(costs[283]),
      '%insertionLexicalCost @ prepc_until = ' + str(costs[284]),
      '%insertionLexicalCost @ prepc_up = ' + str(costs[285]),
      '%insertionLexicalCost @ prepc_upon = ' + str(costs[286]),
      '%insertionLexicalCost @ prepc_versus = ' + str(costs[287]),
      '%insertionLexicalCost @ prepc_via = ' + str(costs[288]),
      '%insertionLexicalCost @ prepc_with = ' + str(costs[289]),
      '%insertionLexicalCost @ prepc_within = ' + str(costs[290]),
      '%insertionLexicalCost @ prepc_without = ' + str(costs[291]),
      '%insertionLexicalCost @ prepc_according_to = ' + str(costs[292]),
      '%insertionLexicalCost @ prepc_as_per = ' + str(costs[293]),
      '%insertionLexicalCost @ prepc_compared_to = ' + str(costs[294]),
      '%insertionLexicalCost @ prepc_instead_of = ' + str(costs[295]),
      '%insertionLexicalCost @ prepc_preparatory_to = ' + str(costs[296]),
      '%insertionLexicalCost @ prepc_across_from = ' + str(costs[297]),
      '%insertionLexicalCost @ prepc_as_to = ' + str(costs[298]),
      '%insertionLexicalCost @ prepc_compared_with = ' + str(costs[299]),
      '%insertionLexicalCost @ prepc_irrespective_of = ' + str(costs[300]),
      '%insertionLexicalCost @ prepc_previous_to = ' + str(costs[301]),
      '%insertionLexicalCost @ prepc_ahead_of = ' + str(costs[302]),
      '%insertionLexicalCost @ prepc_aside_from = ' + str(costs[303]),
      '%insertionLexicalCost @ prepc_due_to = ' + str(costs[304]),
      '%insertionLexicalCost @ prepc_next_to = ' + str(costs[305]),
      '%insertionLexicalCost @ prepc_prior_to = ' + str(costs[306]),
      '%insertionLexicalCost @ prepc_along_with = ' + str(costs[307]),
      '%insertionLexicalCost @ prepc_away_from = ' + str(costs[308]),
      '%insertionLexicalCost @ prepc_depending_on = ' + str(costs[309]),
      '%insertionLexicalCost @ prepc_near_to = ' + str(costs[310]),
      '%insertionLexicalCost @ prepc_pursuant_to = ' + str(costs[311]),
      '%insertionLexicalCost @ prepc_alongside_of = ' + str(costs[312]),
      '%insertionLexicalCost @ prepc_based_on = ' + str(costs[313]),
      '%insertionLexicalCost @ prepc_except_for = ' + str(costs[314]),
      '%insertionLexicalCost @ prepc_off_of = ' + str(costs[315]),
      '%insertionLexicalCost @ prepc_regardless_of = ' + str(costs[316]),
      '%insertionLexicalCost @ prepc_apart_from = ' + str(costs[317]),
      '%insertionLexicalCost @ prepc_because_of = ' + str(costs[318]),
      '%insertionLexicalCost @ prepc_exclusive_of = ' + str(costs[319]),
      '%insertionLexicalCost @ prepc_out_of = ' + str(costs[320]),
      '%insertionLexicalCost @ prepc_subsequent_to = ' + str(costs[321]),
      '%insertionLexicalCost @ prepc_as_for = ' + str(costs[322]),
      '%insertionLexicalCost @ prepc_close_by = ' + str(costs[323]),
      '%insertionLexicalCost @ prepc_contrary_to = ' + str(costs[324]),
      '%insertionLexicalCost @ prepc_outside_of = ' + str(costs[325]),
      '%insertionLexicalCost @ prepc_such_as = ' + str(costs[326]),
      '%insertionLexicalCost @ prepc_as_from = ' + str(costs[327]),
      '%insertionLexicalCost @ prepc_close_to = ' + str(costs[328]),
      '%insertionLexicalCost @ prepc_followed_by = ' + str(costs[329]),
      '%insertionLexicalCost @ prepc_owing_to = ' + str(costs[330]),
      '%insertionLexicalCost @ prepc_thanks_to = ' + str(costs[331]),
      '%insertionLexicalCost @ prepc_as_of = ' + str(costs[332]),
      '%insertionLexicalCost @ prepc_contrary_to = ' + str(costs[333]),
      '%insertionLexicalCost @ prepc_inside_of = ' + str(costs[334]),
      '%insertionLexicalCost @ prepc_preliminary_to = ' + str(costs[335]),
      '%insertionLexicalCost @ prepc_together_with = ' + str(costs[336]),
      '%insertionLexicalCost @ prepc_by_means_of = ' + str(costs[337]),
      '%insertionLexicalCost @ prepc_in_case_of = ' + str(costs[338]),
      '%insertionLexicalCost @ prepc_in_place_of = ' + str(costs[339]),
      '%insertionLexicalCost @ prepc_on_behalf_of = ' + str(costs[340]),
      '%insertionLexicalCost @ prepc_with_respect_to = ' + str(costs[341]),
      '%insertionLexicalCost @ prepc_in_accordance_with = ' + str(costs[342]),
      '%insertionLexicalCost @ prepc_in_front_of = ' + str(costs[343]),
      '%insertionLexicalCost @ prepc_in_spite_of = ' + str(costs[344]),
      '%insertionLexicalCost @ prepc_on_top_of = ' + str(costs[345]),
      '%insertionLexicalCost @ prepc_in_addition_to = ' + str(costs[346]),
      '%insertionLexicalCost @ prepc_in_lieu_of = ' + str(costs[347]),
      '%insertionLexicalCost @ prepc_on_account_of = ' + str(costs[348]),
      '%insertionLexicalCost @ prepc_with_regard_to = ' + str(costs[349]),
      '%insertionLexicalCost @ prepc_with_regard_to = ' + str(costs[350]),
      '%insertionLexicalCost @ op = ' + str(costs[351]),
      '%insertionLexicalCost @ prep_dep = ' + str(costs[352]),
  ]

# Evaluation variables
RESULT_LOCK = Lock()
numGuessAndCorrect = 0
numGuessTrue       = 0
numGoldTrue        = 0
numCorrect         = 0
numStrictCorrect   = 0
numTotal           = 0

# Global Weights
COSTS_LOCK = Lock()
COSTS = softNatlogCosts();
if os.path.isfile(IN_MODEL):
  with open(IN_MODEL) as costs:
    content = costs.readlines()
    for i in range(0, len(COSTS)):
      COSTS[i] = float(content[i]);

"""
  Run a query on the server, with an optional premise set.
  @param premises A list of premises, each of which is a String.
  @param query A query, as a String, and optionally prepended with either
               'TRUE: ' or 'FALSE: ' to communicate a value judgment to
               the server.
  @return True if the query succeeded, else False.
"""
def query(premises, query, costs=None):
  # We want these variables
  global RESULT_LOCK
  global numGuessAndCorrect
  global numGuessTrue
  global numGoldTrue
  global numCorrect
  global numStrictCorrect
  global numTotal
  
  # Open a socket
  s = socket(AF_INET, SOCK_STREAM)
  s.connect((TCP_IP, TCP_PORT))

  # Write the preamble, if it exists.
  if costs:
    for line in costsPreamble(costs):
      s.send((line.strip() + "\n").encode("ascii"))
  # Write the premises + query
  for premise in premises:
    s.send((premise.strip() + "\n").encode("ascii"))
  s.send((query.strip() + '\n\n').encode("ascii"))
  gold = None
  booleanGold = True
  if query.startswith('TRUE: '):
    query = query[len("TRUE: "):]
    gold = 'true'
    booleanGold = True
  elif query.startswith('FALSE: '):
    query = query[len("FALSE: "):]
    gold = 'false'
    booleanGold = False
  elif query.startswith('UNK: '):
    query = query[len("UNK: "):]
    gold = 'unknown'
    booleanGold = False

  # Read the response
  rawResponse = s.recv(32768).decode("ascii");

  # Parse pass/fail judgments
  didPass=True
  if rawResponse.startswith("PASS: "):
    rawResponse = rawResponse[len("PASS: "):]
    didPass = True
  elif rawResponse.startswith("FAIL: "):
    rawResponse = rawResponse[len("FAIL: "):]
    didPass = False

  # Parse JSON
  response = json.loads(rawResponse)
  if response['success']:
    # Materialize JSON
    numResults  = response['numResults']  # Int
    totalTicks  = response['totalTicks']  # Int
    probability = response['truth']       # Double
    bestPremise = response['bestPremise'] # String
    path        = response['path']        # List
    # Scrape features
    features = None
    if 'features' in response:
      features = mkFeatureVector(
          response['features']['mutationCounts'],
          response['features']['transitionFromTrueCounts'],
          response['features']['transitionFromFalseCounts'],
          response['features']['insertionCosts']);  # TODO(gabor) typo in server...
    # Assess result
    guess = 'unknown'
    booleanGuess = False
    if probability > 0.5:
      guess = 'true'
      booleanGuess = True
    elif probability < 0.5:
      guess = 'false'
    # Do weight update
    if booleanGold and not booleanGuess:
      pass
#      update(costs, features, True)
    elif not booleanGold and booleanGuess:
      update(costs, features, False)
    # Output
    if gold != None:
      pfx = "FAIL:"
      if gold == guess:
        pfx = "PASS:"
      if gold == 'false' and guess == 'unknown':
        pfx = "????:"  # 3-class failure; 2-class success
      print ("=> %s '%s' is %s (%f) because '%s'" % (pfx, query, guess, probability, bestPremise))
    else:
      print ("=> '%s' is %s (%f) because '%s'" % (query, guess, probability, bestPremise))
    # Compute results
    RESULT_LOCK.acquire()
    if gold != None:
      numTotal += 1;
      if booleanGuess == booleanGold:
        numCorrect += 1
      if guess == gold:
        numStrictCorrect += 1
      if booleanGuess and booleanGold:
        numGuessAndCorrect += 1
      if booleanGuess:
        numGuessTrue += 1
      if booleanGold:
        numGoldTrue += 1
    RESULT_LOCK.release()
  else:
    print ("Query failed: %s" % response['message'])
    return False
  
  # Socket should be closed already, but just in case...
  s.close()
  return True

#
# Interactive Shell Entry Point
#
if __name__ == "__main__":
  lines = []
  with ThreadPoolExecutor(max_workers=PARALLELISM) as threads:
    while True:
      line = sys.stdin.readline()
      if not line:
        break;
      if line.strip().startswith('#'):
        continue
      if (line == '\n'):
        # Parse the lines
        lines.reverse()
        if len(lines) == 0:
          continue
        elif len(lines) == 1:
          queryStr = lines[0]
          premises = []
        else:
          queryStr, *premises = lines
        lines = []
        # Start the query (async)
        COSTS_LOCK.acquire()
        costs = copy(COSTS)
        COSTS_LOCK.release()
#        threads.submit(query, premises, queryStr, costs)
        query(premises, queryStr, costs)
      else:
        lines.append(line.strip());
  
  
  # Compute scores
  p      = 1.0 if numGuessTrue == 0 else (float(numGuessAndCorrect) / float(numGuessTrue))
  r      = 0.0 if numGoldTrue == 0 else (float(numGuessAndCorrect) / float(numGoldTrue))
  f1     = 0.0 if (p + r == 0) else 2.0 * p * r / (p + r)
  accr   = 0.0 if numTotal == 0 else (float(numCorrect) / float(numTotal))
  strict = 0.0 if numTotal == 0 else (float(numStrictCorrect) / float(numTotal))
  
  # Print scores
  if numTotal > 0:
    print("--------------------")
    print("P:        %.3g" % p)
    print("R:        %.3g" % r)
    print("F1:       %.3g" % f1)
    print("Accuracy: %.3g" % accr)
    print("3-class:  %.3g" % strict)
    print("")
    print("(Total):  %d"   % numTotal)
    print("--------------------")

  with open(OUT_MODEL, 'w') as model:
    for i in range(0, len(COSTS)):
      model.write('' + str(COSTS[i]) + '\n')
