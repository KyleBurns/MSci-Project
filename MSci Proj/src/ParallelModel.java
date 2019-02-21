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
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelector;
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy;
import org.chocosolver.solver.search.strategy.strategy.IntStrategy;
import org.chocosolver.solver.variables.IntVar;

public abstract class ParallelModel {
	
	private int threadCounter;
    private CyclicBarrier cyclicBarrier;
    private int NUM_WORKERS;
    private Solver[] s;
    
    private int timeout;
    private float fastestTime;
    private String returnString;
    
    private boolean useBacktrackRestarts;
    private boolean useSolutionBiased;
    
    public abstract Model buildModel();
    
    protected String runSolvers(int numWorkers, int timeLimit, boolean useBacktrackRestarts, boolean useSolutionBiased) {
        NUM_WORKERS = numWorkers;
        threadCounter = 0;
        timeout = timeLimit;
        
        fastestTime = 0;
        returnString = "";
        
        this.useBacktrackRestarts = useBacktrackRestarts;
        this.useSolutionBiased = useSolutionBiased;
        
        cyclicBarrier = new CyclicBarrier(NUM_WORKERS, new AggregatorThread());
        s = new Solver[NUM_WORKERS];
        Thread[] threads  = new Thread[NUM_WORKERS];
        
        System.out.println("Spawning " + NUM_WORKERS + " worker threads");
        for (int i = 0; i < NUM_WORKERS; i++) {
            threads[i] = new Thread(new ParallelThread());
            threads[i].setName("t" + i);
            threads[i].start();
        }
        for (Thread thread : threads) {
            try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }	
        return returnString;
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
        	
        	if(useSolutionBiased) setSolutionBiasedSearch(model, threadIndex*1000);
        	else setRandSearch(model, threadIndex*1000);
        	
        	if(useBacktrackRestarts) setBacktrackRestarting(model);
        	else setTimedRestarting(model);
        	
        	Solver solver = model.getSolver(); 
        	s[threadIndex] = solver;
        	
	   	    solver.plugMonitor(new IMonitorRestart() {
	   	    	@Override	
	   	    	public void beforeRestart() {
	   	    		try {
	   	    			cyclicBarrier.await();
		   	    		extractNogoodFromPathParallel(model);
		   	    		cyclicBarrier.await();
		   	    	}
	                catch (InterruptedException | BrokenBarrierException e) {
	                	solver.addStopCriterion(() -> true);
	                }	   	    		
	   	    	}
		    });
	   	    
	   	    solver.limitTime(timeout + "s");
	   	    Boolean status = solver.solve();
	   	    if(fastestTime == 0) {
	    		fastestTime = solver.getMeasures().getTimeCount();
	    		for (Thread t : Thread.getAllStackTraces().keySet())
	    			if (t.getName().startsWith("t") && !t.equals(Thread.currentThread())) t.interrupt();

		   	    if(status) returnString = NUM_WORKERS + ",sat,";
		   	    else if(solver.isStopCriterionMet()) returnString = NUM_WORKERS + ",timeout,";
		   	    else returnString = NUM_WORKERS + ",unsat,";	
		   	    returnString += fastestTime + "s," + solver.getMeasures().getNodeCount();
	    		Thread.currentThread().interrupt();
	   	    }  	   	      
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