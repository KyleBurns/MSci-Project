import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class ParallelStruct {

	public static int seedCounter = 0;
    private CyclicBarrier cyclicBarrier;
    private List<Integer> numCollection = Collections.synchronizedList(new ArrayList<>());
    private int NUM_WORKERS;
    
    private void runSimulation(int numWorkers) {
        NUM_WORKERS = numWorkers;

        cyclicBarrier = new CyclicBarrier(NUM_WORKERS, new AggregatorThread());
        System.out.println("Spawning " + NUM_WORKERS + " worker threads");
        for (int i = 0; i < NUM_WORKERS; i++) {
            Thread worker = new Thread(new ParallelThread());
            worker.setName("Thread " + i);
            worker.start();
        }
    }
    
    class ParallelThread implements Runnable {

    	private int randomSeed = seedCounter++;
    	
        @Override
        public void run() {
            Random random = new Random(randomSeed);
        	while(true) {
        		int rngNumber = random.nextInt(1000000);
        		System.out.println("Generated: " + rngNumber);
        		numCollection.add(rngNumber);
                try {
                    cyclicBarrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
        	}
        }
    }

	class AggregatorThread implements Runnable {
	
	    @Override
	    public void run() {
	        System.out.println("Checking for poison pill...");
	        for (int i : numCollection)
	        	if(i == 1337) {
	        		System.out.println("Poison pill found, bye bye");
	        		System.exit(0);
	        	}
	        System.out.println("No poison pill found, keep executing");
	    }
	}
   
    public static void main(String[] args) {
        ParallelStruct play = new ParallelStruct();
        play.runSimulation(5);
    }
}
