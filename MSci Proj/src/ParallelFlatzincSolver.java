import org.chocosolver.parser.flatzinc.Flatzinc;
import org.chocosolver.pf4cs.SetUpException;
import org.chocosolver.solver.Model;

public class ParallelFlatzincSolver extends ParallelModel{

	static String cznFilepath = "";
	
	public static void main(String[] args) {
		cznFilepath = "C:\\Users\\Kyle\\Desktop\\mknapsack_global.czn";
		ParallelFlatzincSolver PModel = new ParallelFlatzincSolver();
		PModel.runSolvers(1);
	}

	@Override
	public Model buildModel() {
		Flatzinc fzn = new Flatzinc();
		
		try {
			fzn.setUp(cznFilepath);
		} catch (SetUpException e) {
			e.printStackTrace();
		}
		
        fzn.createSolver();
        fzn.buildModel();
        return fzn.getModel();
	}

}
