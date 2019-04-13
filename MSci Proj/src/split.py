f = open("results/opt-sol.txt", 'r').readlines()

for n in ['1','2','4','8','16','32']:
	out = open(n + "threads.txt", 'w+')
	for l in f:
		if l.split()[2] == n:
			out.write(l)
			

