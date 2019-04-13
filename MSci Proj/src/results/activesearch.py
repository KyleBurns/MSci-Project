
data = [[], [], [], [], [], []]
speedups = [0, 0, 0, 0, 0, 0]
i = 0

for n in ['1','2','4','8','16','32']:
	data[i] = open(n + "threads.txt", 'r').readlines()
	i += 1

for n in range(len(data[0])):
	if float(data[0][n].split()[4]) < 3:
		continue
	for x in range(0, 6):
		if len(data[x][n].split()) < 6:
			continue
		time = float(data[x][n].split()[4])
		search = float(data[x][n].split()[5])/1000
		speedups[x] += search/time

for i in range(len(speedups)):
	speedups[i] = speedups[i]/len(data[0])*100

print speedups
	


			

