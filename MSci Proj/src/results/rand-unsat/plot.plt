set border 3;
set xtics nomirror;
set ytics nomirror;
set grid;
set logscale x; 
set xrange [1:1000];
set yrange [60:130];
set terminal png;
set title " ";
set key left top;


plot "1threads.txt" using 5:(1) with dots title "1 thread", \
     "2threads.txt" using 5:($3 >= 1000 ? 1e-10 : 1) smooth cumulative with lines title "2 threads", \
     "4threads.txt" using 5:($3 >= 1000 ? 1e-10 : 1) smooth cumulative with lines title "4 threads", \
     "8threads.txt" using 5:($3 >= 1000 ? 1e-10 : 1) smooth cumulative with lines title "8 threads", \
     "16threads.txt" using 5:($3 >= 1000 ? 1e-10 : 1) smooth cumulative with lines title "16 threads", \
     "32threads.txt" using 5:($3 >= 1000 ? 1e-10 : 1) smooth cumulative with lines title "32 threads";


