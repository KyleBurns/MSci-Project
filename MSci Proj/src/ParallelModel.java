import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.chocosolver.sat.PropNogoods;
import org.chocosolver.sat.SatSolver;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.ResolutionPolicy;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.limits.BacktrackCounter;
import org.chocosolver.solver.search.limits.TimeCounter;
import org.chocosolver.solver.search.loop.monitors.IMonitorRestart;
import org.chocosolver.solver.search.restart.MonotonicRestartStrategy;
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory;
import org.chocosolver.solver.search.strategy.decision.Decision;
import org.chocosolver.solver.search.strategy.decision.IntDecision;
import org.chocosolver.solver.variables.IntVar;

public abstract class ParallelModel {
	
	private int threadCounter;
    private CyclicBarrier cyclicBarrier;
    private int NUM_WORKERS;
    
    private final Object finishLock = new Object();
    private int bestSolutionValue;
    
    ArrayList<Thread> threads = new ArrayList<Thread>();
    private Solver[] solvers;

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
        
        cyclicBarrier = new CyclicBarrier(NUM_WORKERS);
        solvers = new Solver[NUM_WORKERS];
        
        System.out.println("Spawning " + NUM_WORKERS + " worker threads");
        for (int i = 0; i < NUM_WORKERS; i++) {
            threads.add(new Thread(new ParallelThread()));
            threads.get(i).setName("t" + i);
            threads.get(i).start();
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
    			new MonotonicRestartStrategy(500000000), 
    			Integer.MAX_VALUE);
    }
    
    private void setBacktrackRestarting(Model model) {
    	model.getSolver().setRestarts(new BacktrackCounter(model, 0), 
    			new MonotonicRestartStrategy(1000), 
    			Integer.MAX_VALUE);
    }
    
    
    class ParallelThread implements Runnable {

    	private int threadIndex = threadCounter++;
    	
    	private long startTime;
    	private long timeSpentBuildingNogoods = 0;
        private long timeSpentSearching = 0;
        private long timeSpentAtBarrier = 0;
        
        @Override
        public void run() {
        	final Model model = buildModel();
    		final PropNogoods png = model.getNogoodStore().getPropNogoods();
    		
        	ArrayDeque<Decision> decisions = new ArrayDeque<>(16);
        	model.getSolver().setSearch(new SolutionBiasedSearch(model, threadIndex*1000, !useSolutionBiased));
        	
        	if(useBacktrackRestarts) setBacktrackRestarting(model);
        	else setTimedRestarting(model);
        	
        	if(model.getResolutionPolicy() == ResolutionPolicy.MAXIMIZE) bestSolutionValue = Integer.MIN_VALUE; 
        	if(model.getResolutionPolicy() == ResolutionPolicy.MINIMIZE) bestSolutionValue = Integer.MAX_VALUE;
        	
        	Solver solver = model.getSolver(); 
        	solvers[threadIndex] = solver;
	    	
	    	
	   	    solver.plugMonitor(new IMonitorRestart() {
	   	    	@Override	
	   	    	public void beforeRestart() {	   	    		
	   	    		try {	    	   	    	
	   	    			long count = System.nanoTime();
	   	    			timeSpentSearching += count - startTime;
	   	    			cyclicBarrier.await(100, TimeUnit.SECONDS);
	   	    			
	   	    			timeSpentAtBarrier += System.nanoTime() - count;
	   	    			count = System.nanoTime();
	   	    			extractNogoodFromPathParallel(model, png, decisions);
	   	    			
	   	    			cyclicBarrier.await(100, TimeUnit.SECONDS);
	   	    			
	   	    			timeSpentBuildingNogoods += System.nanoTime() - count;  
	   	    			startTime = System.nanoTime();
		   	    	}
	                catch (BrokenBarrierException | InterruptedException | TimeoutException e) {	             
	                	solver.limitSearch(() -> true);	              
	                	return;
	                }			   	    		
	   	    	}
		    });	   	    
	   	    
	   	    
	   	    try {
				cyclicBarrier.await();
			} catch (InterruptedException | BrokenBarrierException e) {
				return;
			}
	   	    
	   	    solver.limitTime(timeout + "s");	   	 
	   	    Boolean status = null;
	   	    
	   	    startTime = System.nanoTime();
	   	    if(solver.getObjectiveManager().isOptimization()) {	
	   	    	while(solver.solve());
	   	    }   	  
	   	    else {
	   	    	status = solver.solve();
	   	    }
	   	    
	   	    System.out.println("Complete");
	   	    
	   	    synchronized(finishLock) {
	   	    	if(fastestTime == 0) {		
		   	    	fastestTime = solver.getMeasures().getTimeCount();	   	    	
		   	    	long totalNodeCount = 0;
			   	    for(Solver s : solvers) {
			   	    	if(!s.equals(solver)) solver.limitSearch(() -> true);
			   	    	totalNodeCount += s.getNodeCount();  
			   	    }			   	    			   	    			   	    
			   	    cyclicBarrier.reset();	
			   	    
			   	    if(solver.getObjectiveManager().isOptimization()) {			   
			   	    	status = false;
			   	    	for(Solver s: solvers) {
		   					int solverBest = s.getBestSolutionValue().intValue();
		   					status = status || s.isObjectiveOptimal();
		   					
		   					if((s.getModel().getResolutionPolicy() == ResolutionPolicy.MAXIMIZE && solverBest > bestSolutionValue) ||
		   					   (s.getModel().getResolutionPolicy() == ResolutionPolicy.MINIMIZE && solverBest < bestSolutionValue)) 
		   						bestSolutionValue = solverBest;		   	    	
			   	    	}
			   	    }
			   	    
			   	    			
			   	    if(status) returnString = "sat ";
			   	    else if(solver.isStopCriterionMet()) returnString = "timeout ";
			   	    else returnString = "unsat ";	
			   	    
			   	    returnString += fastestTime + " " + 
			   	    				timeSpentSearching/1000000 + " " + 
									timeSpentAtBarrier/1000000 + " " + 
									timeSpentBuildingNogoods/1000000 + " " + 
									solver.getMeasures().getNodeCount() + " " + 
									totalNodeCount;
			   	    
			   	    if(solver.getObjectiveManager().isOptimization()) returnString += " " + bestSolutionValue;			   	    
			   	    for(Thread thread: threads) if(!thread.equals(Thread.currentThread())) thread.interrupt();			
	   	    	}
	   	    }
        }
    }
    
	private void extractNogoodFromPathParallel(Model model, PropNogoods png, ArrayDeque<Decision> decisions) {
		for(int m=0;m<NUM_WORKERS;m++) {	
	        int d = (int) solvers[m].getNodeCount();
	       
	        solvers[m].getDecisionPath().transferInto(decisions, false);  

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