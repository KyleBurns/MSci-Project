import numpy as np

v = open("sol.txt", "r").readlines()

def cv(x):
	return np.std(x)/np.mean(x)

def speedup(seq, x):
	r = []
	for n in range(len(x)):
		r.append(seq/float(x[n].split()[4]))
	return r
					

for n in range(6):
	print cv(speedup(144.93608, v[n*10:(n*10)+10]))
