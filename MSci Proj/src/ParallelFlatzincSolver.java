import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import org.chocosolver.parser.flatzinc.Flatzinc;
import org.chocosolver.pf4cs.SetUpException;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.search.strategy.Search;

public class ParallelFlatzincSolver extends ParallelModel{

	private String filepath;
	
	public static void main(String[] args) {
		ParallelFlatzincSolver pfs = new ParallelFlatzincSolver("C:\\Users\\Kyle\\Desktop\\ChocoFZN-Decision\\magicseq\\300.czn");
		pfs.ParallelSpeedupTest();
	}
	
	public ParallelFlatzincSolver(String fp) {
		this.filepath = fp;
	}
	
	public void ParallelSpeedupTest() {
		String str = "";
		int[] noThreads = new int[]{32, 24, 16, 12, 8, 4, 2, 1};
		str += "Solution Biased - Backtrack\n";
		for(int i=0;i<8;i++)
			str += runSolvers(noThreads[i], 60, true, true) + "\n";
		str += "Solution Biased - Time\n";
		for(int i=0;i<8;i++)
			str += runSolvers(noThreads[i], 60, true, true) + "\n";
		str += "Random - Backtrack\n";
		for(int i=0;i<8;i++)
			str += runSolvers(noThreads[i], 60, true, true) + "\n";
		str += "Random - Time\n";
		for(int i=0;i<8;i++)
			str += runSolvers(noThreads[i], 60, true, true) + "\n";
		
		try {
			String[] stuff = filepath.split("\\\\"); 
			PrintWriter writer = new PrintWriter(stuff[stuff.length-1].replaceAll(".czn", "-results.csv"), "UTF-8");
			writer.write(str);
			writer.close();
		} catch (FileNotFoundException|UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Model buildModel() {
		Flatzinc fzn = new Flatzinc();
		try {
			fzn.setUp(filepath);
		} catch (SetUpException e) {
			e.printStackTrace();
		}
        fzn.createSolver();
        fzn.buildModel();
        Model model = fzn.getModel();
        if(model.getSolver().getSearch() == null)
        	model.getSolver().setSearch(Search.inputOrderLBSearch(model.retrieveIntVars(true)));
        return model;
	}
}