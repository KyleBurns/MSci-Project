import java.io.* ;
import java.util.*;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.limits.BacktrackCounter;
import org.chocosolver.solver.search.loop.monitors.IMonitorRestart;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelector;
import org.chocosolver.solver.search.strategy.strategy.IntStrategy;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.iterators.DisposableValueIterator;


public class GC_Testing {    
  
    public static void main(String[] args) throws FileNotFoundException, IOException {
    	// simple graph-colouring model
	    Model model              = new Model();
	    Scanner sc               = new Scanner(new File("instance.txt"));
	    int k                    = Integer.parseInt("8");           // number of colours
	    int n                    = sc.nextInt();                    // number of vertices
      
	    IntVar [] colorAllocation = model.intVarArray("alloc", n, 1, k);

	    while (sc.hasNext()){
	    	  // each pair of numbers can't be the same value
	    	  int [] pair = new int[2];
	    	  pair[0] = sc.nextInt();
	    	  pair[1] = sc.nextInt();
	    	  model.arithm(colorAllocation[pair[0]], "!=", colorAllocation[pair[1]]).post();
	    }
	    sc.close();

		// our own random method
		Random rand = new Random(0);
		IntStrategy randValSearch = Search.intVarSearch(
				// variable selector
				(VariableSelector<IntVar>) variables -> {
					for(IntVar v:variables)
						if(!v.isInstantiated()) return v;
					return null;
				},
				//value selector
				(IntValueSelector) var -> {
					DisposableValueIterator vit = var.getValueIterator(false);
					int[] vals = new int[var.getDomainSize()];
					int i = 0;
					while(vit.hasPrevious()){
						vals[i] = vit.previous();
						i++;
					}
					vit.dispose();
					return vals[rand.nextInt(vals.length)];
				}, colorAllocation);
		 
	     Solver solver = model.getSolver();
	     solver.setSearch(randValSearch);
	     
	     // set Luby restarts
	     solver.setLubyRestart(100, new BacktrackCounter(model, 10000000), 100000000);	     
	     // set no good recording
	     solver.setNoGoodRecordingFromRestarts(); 
	     
	     solver.plugMonitor(new IMonitorRestart() {
	    	    @Override	
	    	    public void beforeRestart() {
	    	    	//System.out.println(model.getNogoodStore());	    	    	
	    	    }
	    	});
	     
	     if (solver.solve()){
	    	 System.out.print("[" + colorAllocation[0].getValue());
	    	 for(int i=1;i<n;i++) System.out.print("," + colorAllocation[i].getValue());
	    	 System.out.print("]\n");
	     }
	     System.out.println(solver.getMeasures());	
    }
}