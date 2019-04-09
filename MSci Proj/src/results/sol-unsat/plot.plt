set border 3;
set xtics nomirror;
set ytics nomirror;
set grid;
set logscale x; 
set yrange [60:150];
set xrange [1:1000];
set terminal png;
set title "blah";

plot "1threads.txt" using 5:($3 >= 1000 ? 1e-10 : 1) smooth cumulative with lines, \
     "2threads.txt" using 5:($3 >= 1000 ? 1e-10 : 1) smooth cumulative with lines, \
     "4threads.txt" using 5:($3 >= 1000 ? 1e-10 : 1) smooth cumulative with lines, \
     "8threads.txt" using 5:($3 >= 1000 ? 1e-10 : 1) smooth cumulative with lines, \
     "16threads.txt" using 5:($3 >= 1000 ? 1e-10 : 1) smooth cumulative with lines, \
     "32threads.txt" using 5:($3 >= 1000 ? 1e-10 : 1) smooth cumulative with lines;


