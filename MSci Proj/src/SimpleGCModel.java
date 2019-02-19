import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMax;
import org.chocosolver.solver.search.strategy.selectors.variables.FirstFail;
import org.chocosolver.solver.search.strategy.strategy.IntStrategy;
import org.chocosolver.solver.variables.IntVar;

public class SimpleGCModel extends ParallelModel {

    protected ArrayList<Integer> intBuffer = new ArrayList<Integer>();
    
    public static void main(String[] args) throws FileNotFoundException {    
        SimpleGCModel play = new SimpleGCModel();
        play.readData("instance.txt");
        play.runSolvers(4);
    }
   
    public void readData(String filename) throws FileNotFoundException {
    	Scanner sc = new Scanner(new File(filename));
    	while (sc.hasNext()) intBuffer.add(sc.nextInt());
    	sc.close();
    }
    
	@Override
	public Model buildModel() {
        Model model = new Model();
        int k = 8;					             // number of colours
        int n = intBuffer.get(0);                // number of vertices
        IntVar[] colorAllocation = model.intVarArray("alloc", n, 1, k);
        
	    for(int i=1;i<intBuffer.size();i+=2)
	    	  // each pair of numbers can't be the same value
	    	  model.arithm(colorAllocation[intBuffer.get(i)], "!=", colorAllocation[intBuffer.get(i+1)]).post();
	    model.getSolver().setSearch(new IntStrategy(colorAllocation, new FirstFail(model), new IntDomainMax()));
	    return model;
	}
}
