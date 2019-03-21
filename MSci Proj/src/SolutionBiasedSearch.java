import java.util.Random;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory;
import org.chocosolver.solver.search.strategy.decision.Decision;
import org.chocosolver.solver.search.strategy.decision.DecisionPath;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainRandom;
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy;
import org.chocosolver.solver.variables.IntVar;

public class SolutionBiasedSearch extends AbstractStrategy<IntVar> {

    /**
     * The main strategy declared in the solver
     */
    private AbstractStrategy mainStrategy;
    private Random rng;
    private Model model;
	private IntDomainRandom randValSelect;
	private boolean fullRandom;

    public SolutionBiasedSearch(Model model, int seed, boolean fullyRandomValues) {    	
    	this.model = model;
    	mainStrategy = model.getSolver().getSearch();
    	rng = new Random(seed);
    	randValSelect = new IntDomainRandom(seed);
    	fullRandom = fullyRandomValues;
    }
    
    public SolutionBiasedSearch(Model model, int seed) {    	
    	this(model, seed, false);
    }
    
    
	@Override
	public Decision<IntVar> getDecision() {	
		Decision d = mainStrategy.getDecision();
		if(d == null) return null;	
		DecisionPath dp = model.getSolver().getDecisionPath();
		
		IntVar var = (IntVar) d.getDecisionVariable();	
		int val;
		if(rng.nextInt(dp.size()+1) == 0 || fullRandom) 
			 val = randValSelect.selectValue(var);	
		else val = (int) d.getDecisionValue();
				
		return dp.makeIntDecision(var, DecisionOperatorFactory.makeIntEq(), val);
	}
}
