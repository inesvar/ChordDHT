package demo;

import java.util.ArrayList;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * @author Remi SHARROCK
 * @description
 */
public class CommunicationTopologyCreation {

	public static void main(String[] args) {

		final ActorSystem system = ActorSystem.create("system");

		int matrix[][] = {{0,1,1,0}, {0,0,0,1}, {1,0,0,1}, {1,0,0,1}};

		// Instanciate the actors
		ArrayList<ActorRef> actors = new ArrayList<>();
		for (int i = 0 ; i < matrix.length; i++) {
			actors.add(system.actorOf(Actor.createActor(), Integer.toString(i+1)));
		}

		for (int i = 0 ; i < matrix.length ; i++) {
			ArrayList<ActorRef> refs = new ArrayList<>();
			for (int j = 0 ; j < matrix.length ; j++) {	
				if (matrix[i][j] != 0) {
					refs.add(actors.get(j));
				}
			}
			actors.get(i).tell(new MyMessage(refs), ActorRef.noSender());
		}

	    // We wait 5 seconds before ending system (by default)
	    // But this is not the best solution.
	    try {
			waitBeforeTerminate();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			system.terminate();
		}
	}

	public static void waitBeforeTerminate() throws InterruptedException {
		Thread.sleep(5000);
	}

}
