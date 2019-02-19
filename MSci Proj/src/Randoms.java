import java.util.Random;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.iterators.DisposableValueIterator;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainImpact;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainRandom;
import org.chocosolver.solver.search.strategy.selectors.variables.FirstFail;
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelector;
import org.chocosolver.solver.search.strategy.strategy.IntStrategy;

public class Randoms {

	public static void main(String[] args) {
		int n         = 10;
		Model model   = new Model("nqueens");
		Solver solver = model.getSolver();
		IntVar[] q    = model.intVarArray("queen",n,0,n-1);

		// columns constraint  
		for (int i=0;i<n-1;i++)
		    for (int j=i+1;j<n;j++)
		    	model.arithm(q[i],"!=",q[j]).post();
		// diagonal constraint
		for (int i=0;i<n-1;i++)
		    for (int j=i+1;j<n;j++)
		    	model.distance(q[i],q[j],"!=",Math.abs(i-j)).post();

		IntDomainRandom rng = new IntDomainRandom(0);
		IntStrategy randValSearchChoco = Search.intVarSearch(
				// variable selector
				(VariableSelector<IntVar>) variables -> {
					for(IntVar v:variables)
						if(!v.isInstantiated()) return v;
					return null;
				},
				// value selector
				(IntValueSelector) var -> rng.selectValue(var), q);
		
		Random rand = new Random(0);
		IntStrategy randValSearchKyle = Search.intVarSearch(
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
				}, q);

		solver.setSearch(Search.intVarSearch(new FirstFail(model), 
				new IntSolutionBiased(), 
				q));
					
		//String separator = "";
		//for (int i=0;i<n;i++) separator += "-";
		
		solver.solve();
		  //  for (int i=0;i<n;i++){
			//	for (int j=0;j<q[i].getValue();j++) System.out.print(".");
			//	System.out.print("Q");
		//		for (int j=q[i].getValue();j<n-1;j++) System.out.print(".");
		//	System.out.println();
		  //  }
		  //  System.out.println(separator);
		//}
		System.out.println(solver.getMeasures());
	}
}

