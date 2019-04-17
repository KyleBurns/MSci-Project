set border 3;
set xtics nomirror;
set ytics nomirror;
set grid;
set xrange [0:33];
set yrange [0:250];
set terminal png;
set title " ";
set key left top;


plot 144.93608 lw 3 title "true seq", \
"sol.txt" using 3:5 pt 4 ps 1 lt rgb "red", \
"rand.txt" using 3:5 pt 4 ps 1 lt rgb "blue";
