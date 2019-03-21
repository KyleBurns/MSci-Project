import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

import org.chocosolver.parser.flatzinc.Flatzinc;
import org.chocosolver.pf4cs.SetUpException;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.ResolutionPolicy;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.limits.BacktrackCounter;
import org.chocosolver.solver.search.restart.MonotonicRestartStrategy;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainRandom;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelector;
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy;
import org.chocosolver.solver.search.strategy.strategy.IntStrategy;
import org.chocosolver.solver.variables.IntVar;

public class SequentialFlatzincSolver {

	private Model model;
	private String filepath;
	
	public static void main(String[] args) throws IOException {
		SequentialFlatzincSolver sfs = new SequentialFlatzincSolver(args[0]);
		sfs.runModel(Integer.parseInt(args[1]));
	}
	
	public SequentialFlatzincSolver(String filepath) {
		this.filepath = filepath;
		Flatzinc fzn = new Flatzinc();
		try {
			fzn.setUp(filepath);
		} catch (SetUpException e) {
			e.printStackTrace();
		}
        fzn.createSolver();
        fzn.buildModel();
        model = fzn.getModel();
        
        if(model.getSolver().getSearch() == null);
        	model.getSolver().setSearch(Search.inputOrderLBSearch(model.retrieveIntVars(true)));        
	}

	public void runModel(int useRestarts) {
		String outFile = "";
        if(useRestarts > 0) {
        	model.getSolver().setNoGoodRecordingFromRestarts();
        	model.getSolver().setSearch(new SolutionBiasedSearch(model, 0));
        	model.getSolver().setRestarts(new BacktrackCounter(model, 0), 
        			new MonotonicRestartStrategy(1000), 
        			Integer.MAX_VALUE);
        	outFile = "seq-restarts";
        }
        else outFile = "seq-true";        
		
        Solver solver = model.getSolver();
    	
        solver.limitTime("100s");
   	    Boolean status = null;
   	    
   	    if(solver.getObjectiveManager().isOptimization()) {
   	    	while(solver.solve());
   	    	status = solver.isObjectiveOptimal();
   	    }
   	    else {
   	    	status = solver.solve();    	  	    		
   	    }
   	    
		float time = solver.getTimeCount();
				
		File file = new File(filepath);
		String instance = file.getName().replaceFirst(".czn", "");
		String problem = new File(file.getParent()).getName();
		File resultFile = new File("results-" + outFile + ".txt");
		
		String outString = problem + " " + instance + " ";
		if(status) outString += "sat ";
   	    else if(model.getSolver().isStopCriterionMet()) outString += "timeout ";
   	    else outString += "unsat ";
		outString += time + " " + solver.getNodeCount();
		if(solver.getObjectiveManager().isOptimization()) outString += " " + solver.getBestSolutionValue();
		outString += "\n";
		
		try {
			if(!resultFile.exists())
				resultFile.createNewFile();
			FileWriter writer = new FileWriter(resultFile, true);
			writer.write(outString);
			writer.close();
			System.out.print(outString);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}
