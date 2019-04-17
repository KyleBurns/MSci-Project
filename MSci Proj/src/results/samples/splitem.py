import os

f = open("sample-seq.txt", 'r').readlines()

for line in f:
	g = open(line.split()[1]+"/seq.txt", "a+")
	g.write(line)
	g.close()

