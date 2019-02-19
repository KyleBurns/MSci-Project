import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.chocosolver.sat.PropNogoods;
import org.chocosolver.sat.SatSolver;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.nary.nogood.NogoodConstraint;
import org.chocosolver.solver.search.limits.BacktrackCounter;
import org.chocosolver.solver.search.limits.TimeCounter;
import org.chocosolver.solver.search.loop.monitors.IMonitorClose;
import org.chocosolver.solver.search.loop.monitors.IMonitorRestart;
import org.chocosolver.solver.search.restart.MonotonicRestartStrategy;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory;
import org.chocosolver.solver.search.strategy.decision.Decision;
import org.chocosolver.solver.search.strategy.decision.DecisionPath;
import org.chocosolver.solver.search.strategy.decision.IntDecision;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainRandom;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.search.strategy.selectors.variables.FirstFail;
import org.chocosolver.solver.search.strategy.strategy.IntStrategy;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.criteria.LongCriterion;
import org.chocosolver.util.tools.TimeUtils;

public class ParallelModelTimeBased {

	public long startTime = System.currentTimeMillis();
	public long stopClock;
	
	public long timeSpentSearching = 0;	
	public long timeSpentRestarting = 0;
	
	public static int threadCounter = 0;
    private CyclicBarrier cyclicBarrier;
    private int NUM_WORKERS;
    
    private ArrayList<Integer> intBuffer = new ArrayList<Integer>();
    private Solver[] s;
    
    public static void main(String[] args) throws FileNotFoundException {    
        ParallelModelTimeBased play = new ParallelModelTimeBased();
        play.readData("instance.txt");
        play.runSolvers(4);
    }
 
    private void readData(String filename) throws FileNotFoundException {
    	Scanner sc = new Scanner(new File(filename));
    	while (sc.hasNext()) intBuffer.add(sc.nextInt());
    	sc.close();
    }
    
    private void runSolvers(int numWorkers) {
        NUM_WORKERS = numWorkers;
        cyclicBarrier = new CyclicBarrier(NUM_WORKERS, new AggregatorThread());
        s = new Solver[NUM_WORKERS];

        System.out.println("Spawning " + NUM_WORKERS + " worker threads");
        for (int i = 0; i < NUM_WORKERS; i++) {
            Thread worker = new Thread(new ParallelThread());
            worker.setName("Thread " + i);
            worker.start();
        }
    }
 
    public Model buildModelWithRandSearch(String textFile, int seed) {
    	// simple graph-colouring model
        Model model = new Model();
        int k = 9;					             // number of colours
        int n = intBuffer.get(0);                // number of vertices
        IntVar[] colorAllocation = model.intVarArray("alloc", n, 1, k);
        
	    for(int i=1;i<intBuffer.size();i+=2)
	    	  // each pair of numbers can't be the same value
	    	  model.arithm(colorAllocation[intBuffer.get(i)], "!=", colorAllocation[intBuffer.get(i+1)]).post();
	    
	    IntDomainRandom rng = new IntDomainRandom(seed);
		IntStrategy randSearch = Search.intVarSearch(
				new FirstFail(model),
				(IntValueSelector) var -> rng.selectValue(var), colorAllocation);
		model.getSolver().setSearch(randSearch);
	    return model;
    }
    
    class ParallelThread implements Runnable {

    	private int threadIndex = threadCounter++;
    	
        @Override
        public void run() {
        	final Model model = buildModelWithRandSearch("instance.txt", threadIndex*1000);       	
        	
        	Solver solver = model.getSolver();        	       	
	   	  
        	solver.plugMonitor(new IMonitorRestart() {
	   	    	@Override	
	   	    	public void beforeRestart() {
	   	    		timeSpentSearching += System.currentTimeMillis() - stopClock;
	   	    		stopClock = System.currentTimeMillis();
	   	    		
	   	    		s[threadIndex] = solver;
	   	    		
	   	    		try {cyclicBarrier.await();}
	                catch (InterruptedException | BrokenBarrierException e) {e.printStackTrace();}
	   	    		
	   	    		extractNogoodFromPathParallel(model);
	   	    			
	   	    		try {cyclicBarrier.await();}
	                catch (InterruptedException | BrokenBarrierException e) {e.printStackTrace();}
	   	    		
	   	    		timeSpentRestarting += System.currentTimeMillis() - stopClock;
	   	    		stopClock = System.currentTimeMillis();
	   	    	}
		    });
        
        	solver.setRestarts(new TimeCounter(model, 1),
        			new MonotonicRestartStrategy(100000000), 
        			Integer.MAX_VALUE);
        	
	   	    stopClock = System.currentTimeMillis();
    		if (solver.solve()) System.out.println("Satisfied!");
        	else System.out.println("Unsatisfied!");
    		timeSpentSearching += System.currentTimeMillis() - stopClock;
    		System.out.println(solver.getMeasures());
    		long totalTime = System.currentTimeMillis() - startTime;
        	System.out.println(timeSpentSearching + "ms searching (" + (100*timeSpentSearching)/totalTime + "%)");
        	System.out.println(timeSpentRestarting + "ms restarting (" + (100*timeSpentRestarting)/totalTime + "%)");
        	System.out.println(totalTime + "ms total");
        	System.exit(0);
        }
    }
    
	class AggregatorThread implements Runnable {
	    @Override
	    public void run() {	
	    }
	}
	
	private void extractNogoodFromPathParallel(Model model) {	
		PropNogoods png = model.getNogoodStore().getPropNogoods();
		
		for(int m=0;m<NUM_WORKERS;m++) {
			ArrayDeque<Decision> decisions = new ArrayDeque<Decision>(16);		
	        int d = (int) s[m].getNodeCount();
	        s[m].getDecisionPath().transferInto(decisions, false);
	
	        Decision<IntVar> decision;
	        int[] lits = new int[d];
	        int i = 0;
	        while (!decisions.isEmpty()) {
	            decision = decisions.pollFirst();
	            if (decision instanceof IntDecision) {
	                IntDecision id = (IntDecision) decision;
	                if (id.getDecOp() == DecisionOperatorFactory.makeIntEq()) {
	                    if (id.hasNext() || id.getArity() == 1) {
	                    	lits[i++] = SatSolver.negated(png.Literal(model.getVar(id.getDecisionVariable().getId()-1).asIntVar(), id.getDecisionValue(), true));
	                    } else {
	                        if (i == 0) {
	                            // value can be removed permanently from var!
	                            png.addLearnt(SatSolver.negated(png.Literal(model.getVar(id.getDecisionVariable().getId()-1).asIntVar(), id.getDecisionValue(), true)));
	                        } else {
	                            lits[i] = SatSolver.negated(png.Literal(model.getVar(id.getDecisionVariable().getId()-1).asIntVar(), id.getDecisionValue(), true));
	                            png.addLearnt(Arrays.copyOf(lits, i + 1));
	                        }
	                    }
	                } else if (id.getDecOp() == DecisionOperatorFactory.makeIntNeq()) {
	                    if (id.hasNext()) {
	                        lits[i++] = png.Literal(model.getVar(id.getDecisionVariable().getId()-1).asIntVar(), id.getDecisionValue(), true);
	                    } else {
	                        if (i == 0) {
	                            // value can be removed permanently from var!
	                            png.addLearnt(png.Literal(model.getVar(id.getDecisionVariable().getId()-1).asIntVar(), id.getDecisionValue(), true));
	                        } else {
	                            lits[i] = png.Literal(model.getVar(id.getDecisionVariable().getId()-1).asIntVar(), id.getDecisionValue(), true);
	                            png.addLearnt(Arrays.copyOf(lits, i + 1));
	                        }
	                    }
	                } else {
	                    throw new UnsupportedOperationException("NogoodStoreFromRestarts cannot deal with such operator: " + ((IntDecision) decision).getDecOp());
	                }
	            } else {
	                throw new UnsupportedOperationException("NogoodStoreFromRestarts can only deal with IntDecision.");
	            }
	        }
		}
	}
}