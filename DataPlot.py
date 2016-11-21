import matplotlib.pyplot as plt
import math
fileName = 'double_inside'
gry_m = []
acc_y = []

with open(fileName+'.txt') as fp:
	for line in fp:
		if 'V' in line:
			p = line.index('V')
			name = line[p+2:p+9]
			if name=='dataGry':
				# print line[p+11:]
				gry_m.append(line[p+11:])
			if name=='dataArr':
				# print line[p+19:]
				acc_y.append(line[p+19:])

fig = plt.figure('result')
ax1 = plt.subplot(121)
ax1.set_title('gry_m')
ax1.plot(gry_m,'yo',gry_m,'k')
ax2 = plt.subplot(122)
ax2.set_title('acc_y')
ax2.plot(acc_y,'go',acc_y,'k')
plt.show()
