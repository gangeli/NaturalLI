#!/usr/bin/env python
#

import sys
import matplotlib.pyplot as plt



#if len(sys.argv) < 4:
#    print "Usage: File xlimit ylimit"


def pr(f):
  precisions = []
  recalls = []
  with open(f, 'r') as f:
      for line in f:
          p, r = line.split("\t")
          precisions.append(float(p))
          recalls.append(float(r))
  return [precisions, recalls]

plt.clf()
ax = plt.gca()
ax.spines['right'].set_color('none')
ax.spines['top'].set_color('none')
ax.xaxis.set_ticks_position('bottom')
ax.yaxis.set_ticks_position('left')

[precisions, recalls] = pr('final_results/ollie.plot');
plt.plot(recalls, precisions, label="Ollie", linewidth=3.0, linestyle=':', color='red')
[precisions, recalls] = pr('final_results/our_system_nonominals.plot');
plt.plot(recalls, precisions, label="Our System - Nominals", linewidth=3.0, linestyle='-', color='green')
#[precisions, recalls] = pr('final_results/our_system_names_websites.plot');
#plt.plot(recalls, precisions, label="Our System + Alt. Name + Website")


plt.ylabel("Precision")
plt.xlabel("Recall")
plt.ylim([0.0, 1.0])
plt.xlim([0.0, 0.15])
plt.legend() #loc="lower left")
plt.savefig('pr_curve.pdf')

