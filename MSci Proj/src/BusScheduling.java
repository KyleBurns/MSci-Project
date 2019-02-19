import org.chocosolver.solver.Model;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.Solver;

public class BusScheduling {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Model model = new Model();
		Solver solver = model.getSolver();
		
		IntVar[][] allocationMatrix = model.intVarMatrix(14, 6, 0, 10);
		IntVar[][] driverRoutine = model.intVarMatrix(11, 14, 0, 6);

		for(int i=0;i<11;i++)
			for(int j=0;j<14;j++)
				for(int k=1;k<7;k++)
					model.ifOnlyIf(
							model.arithm(driverRoutine[i][j], "=", k-1), 
							model.arithm(allocationMatrix[j][k-1], "=", i)
						);
					
			
		if(solver.solve()) {
			for(int i=1;i<15;i++) System.out.print(i%10 + " ");
			System.out.println();
			System.out.println("===========================");
			for (int i=0;i<6;i++) {
				for (int j=0;j<14;j++) System.out.print((char) (65+allocationMatrix[j][i].getValue()) + " ");
				System.out.println();
			}
			System.out.println();
			for(int i=0;i<11;i++) {
				System.out.print((char) (65+i) + ": ");
				for (int j=0;j<14;j++) System.out.print(driverRoutine[i][j].getValue() + " ");
				System.out.println();
			}
		}
	}

}
