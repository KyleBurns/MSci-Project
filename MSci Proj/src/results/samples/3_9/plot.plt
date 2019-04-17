set border 3;
set xtics nomirror;
set ytics nomirror;
set grid;
set xrange [0.9:33];
set yrange [0:5];
set terminal png;
set title "rel2onto 3-9";
set key off;
set logscale x 2;

set title font ",20";
set xlabel "Threads";
set ylabel "Speedup (s)";

plot 1 lw 1 title "true seq", \
"sol.txt" using 3:(144.93608/$5) pt 4 ps 3 lt rgb "red", \
"rand.txt" using 3:(144.93608/$5) pt 2 ps 3 lt rgb "blue";
