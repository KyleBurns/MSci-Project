
data = [[], [], [], [], [], []]
speedups = [1, 0, 0, 0, 0, 0]
i = 0

for n in ['1','2','4','8','16','32']:
	data[i] = open(n + "threads.txt", 'r').readlines()
	i += 1

num = 0
for n in range(len(data[0])):
	seq = float(data[0][n].split()[4])
	if seq > 1000 or seq < 3:
		continue
	num += 1
	for x in range(1, 6):
		par = float(data[x][n].split()[4])
		speedups[x] += seq/par

for i in range(len(speedups)):
	speedups[i] = speedups[i]/num
speedups[0] = 1

print num
print speedups
	


			

