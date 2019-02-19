import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.chocosolver.sat.PropNogoods;
import org.chocosolver.sat.SatSolver;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.limits.BacktrackCounter;
import org.chocosolver.solver.search.limits.TimeCounter;
import org.chocosolver.solver.search.loop.monitors.IMonitorRestart;
import org.chocosolver.solver.search.restart.MonotonicRestartStrategy;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory;
import org.chocosolver.solver.search.strategy.decision.Decision;
import org.chocosolver.solver.search.strategy.decision.IntDecision;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainRandom;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.search.strategy.selectors.variables.FirstFail;
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelector;
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy;
import org.chocosolver.solver.search.strategy.strategy.IntStrategy;
import org.chocosolver.solver.variables.IntVar;

public abstract class ParallelModel {

	public long startTime;
	public long stopClock;
	
	public long timeSpentSearching = 0;
	public long timeSpentRestarting = 0;
	
	private static int threadCounter = 0;
    private CyclicBarrier cyclicBarrier;
    private int NUM_WORKERS;
    private Solver[] s;
    
    public abstract Model buildModel();
    
    protected void runSolvers(int numWorkers) {
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
    
    private void setTimedRestarting(Model model) {
    	model.getSolver().setRestarts(new TimeCounter(model, 1),
    			new MonotonicRestartStrategy(100000000), 
    			Integer.MAX_VALUE);
    }
    
    private void setBacktrackRestarting(Model model) {
    	model.getSolver().setRestarts(new BacktrackCounter(model, 0), 
    			new MonotonicRestartStrategy(660), 
    			Integer.MAX_VALUE);
    }
    
    private void setRandSearch(Model model, int seed) {
	    IntDomainRandom rng = new IntDomainRandom(seed);
	    AbstractStrategy<IntVar> former = model.getSolver().getSearch();
	    
		IntStrategy randSearch = Search.intVarSearch(
    			(VariableSelector) i -> {
    	    		if(former.getDecision() != null)
    	    			return former.getDecision().getDecisionVariable();
    	    		return null;
    	    	}
    			,
				(IntValueSelector) var -> rng.selectValue(var), former.getVariables());
		model.getSolver().setSearch(randSearch);   	
    }
    
    private void setSolutionBiasedSearch(Model model, int seed) {
	    IntDomainRandom rng = new IntDomainRandom(seed);
	    Random random = new Random(seed);
	    
	    AbstractStrategy<IntVar> former = model.getSolver().getSearch();
	    		
    	IntStrategy sbSearch = 		
    			Search.intVarSearch(
	    			(VariableSelector) i -> {
	    				if(former.getDecision() != null)
	    					return former.getDecision().getDecisionVariable();
	    				return null;
	    			}
	    			, 
					(IntValueSelector) var -> {
						if(random.nextInt(10) == 0) return rng.selectValue(var);
						else return (int) former.getDecision().getDecisionValue();
					}, former.getVariables()
				); 
    	model.getSolver().setSearch(sbSearch);
    }
 
    
    class ParallelThread implements Runnable {

    	private int threadIndex = threadCounter++;
    	
        @Override
        public void run() {
        	final Model model = buildModel();
        	//setRandSearch(model, threadIndex*1000);
        	setSolutionBiasedSearch(model, threadIndex*1000);
        	
        	//setBacktrackRestarting(model);
        	setTimedRestarting(model);
        	
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
	   	    startTime = System.currentTimeMillis();
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
	                    	lits[i++] = SatSolver.negated(png.Literal((IntVar) model.getVar(id.getDecisionVariable().getId()-1), id.getDecisionValue(), true));
	                    } else {
	                        if (i == 0) {
	                            // value can be removed permanently from var!
	                            png.addLearnt(SatSolver.negated(png.Literal((IntVar) model.getVar(id.getDecisionVariable().getId()-1), id.getDecisionValue(), true)));
	                        } else {
	                            lits[i] = SatSolver.negated(png.Literal((IntVar) model.getVar(id.getDecisionVariable().getId()-1), id.getDecisionValue(), true));
	                            png.addLearnt(Arrays.copyOf(lits, i + 1));
	                        }
	                    }
	                } else if (id.getDecOp() == DecisionOperatorFactory.makeIntNeq()) {
	                    if (id.hasNext()) {
	                        lits[i++] = png.Literal((IntVar) model.getVar(id.getDecisionVariable().getId()-1), id.getDecisionValue(), true);
	                    } else {
	                        if (i == 0) {
	                            // value can be removed permanently from var!
	                            png.addLearnt(png.Literal((IntVar) model.getVar(id.getDecisionVariable().getId()-1), id.getDecisionValue(), true));
	                        } else {
	                            lits[i] = png.Literal((IntVar) model.getVar(id.getDecisionVariable().getId()-1), id.getDecisionValue(), true);
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