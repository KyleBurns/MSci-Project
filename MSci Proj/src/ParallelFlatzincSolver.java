import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.chocosolver.parser.flatzinc.Flatzinc;
import org.chocosolver.pf4cs.SetUpException;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.search.strategy.Search;

public class ParallelFlatzincSolver extends ParallelModel{

	private String filepath;
	
	public static void main(String[] args) throws IOException {
		ParallelFlatzincSolver pfs = new ParallelFlatzincSolver("C:\\Users\\Kyle\\Desktop\\ChocoFZN-Decision\\blackhole\\3.czn");
		pfs.runModel(2, 1);
	}
	
	public ParallelFlatzincSolver(String fp) {
		this.filepath = fp;
	}
	
	public void runModel(int noThreads, int config) throws IOException {
		String outString = "";
		String outFile = "";
		switch(config) {
		case 0: outString = runSolvers(noThreads, 100, true, true) + "\n"; outFile = "sol-bt"; break;
		case 1: outString = runSolvers(noThreads, 100, true, false) + "\n"; outFile = "rand-bt"; break;
		case 2: outString = runSolvers(noThreads, 100, false, true) + "\n"; outFile = "sol-time"; break; 
		case 3: outString = runSolvers(noThreads, 100, false, false) + "\n"; outFile = "rand-time"; break;
		default: outString = "";
		}

		try {
			File file = new File(filepath);
			String instance = file.getName().replaceFirst(".czn", "");
			String problem = new File(file.getParent()).getName();
			
			File resultFile = new File("results-" + outFile + ".txt");
			if(!resultFile.exists())
				resultFile.createNewFile();
			
			FileWriter writer = new FileWriter(resultFile, true);
			writer.write(problem + " " + instance + " " + noThreads + " " + outString);
			writer.close();
			System.out.print(problem + " " + instance + " " + noThreads + " " + outString);
			
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