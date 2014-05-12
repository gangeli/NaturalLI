#!/usr/bin/env python

# Parse key mapping
keymap = {}
with open('reverb.keymap', 'r') as mapping:
  content = mapping.read()
  for line in content.split("\n"):
    if len(line) > 0:
      fields = line.split("\t")
      keymap[fields[0]] = fields[1] + "\t" + fields[2] + "\t" + fields[3]

# Training data
train = []
with open('reverb_correct_train.keys', 'r') as f:
  keys = f.read()
  for line in keys.split("\n"):
    if len(line) > 0:
      train.append("true\t" + keymap[line.replace('\\','\\\\')])
with open('reverb_wrong_train.keys', 'r') as f:
  keys = f.read()
  for line in keys.split("\n"):
    if len(line) > 0:
      train.append("false\t" + keymap[line.replace('\\','\\\\')])
with open("reverb_train.tab", "w") as f:
  f.write("\n".join(train))

# Test data
test = []
with open('reverb_correct_test.keys', 'r') as f:
  keys = f.read()
  for line in keys.split("\n"):
    if len(line) > 0:
      test.append("true\t" + keymap[line.replace('\\','\\\\')])
with open('reverb_wrong_test.keys', 'r') as f:
  keys = f.read()
  for line in keys.split("\n"):
    if len(line) > 0:
      test.append("false\t" + keymap[line.replace('\\','\\\\')])
with open("reverb_test.tab", "w") as f:
  f.write("\n".join(test))
