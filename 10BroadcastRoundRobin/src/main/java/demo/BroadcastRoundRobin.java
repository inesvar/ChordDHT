package demo;

import java.util.ArrayList;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * @author Remi SHARROCK
 * @description
 */
public class BroadcastRoundRobin {

	public static void main(String[] args) {

		final ActorSystem system = ActorSystem.create("system");
		
		// Instantiate a broadcaster
		final ActorRef broadcaster = system.actorOf(Broadcaster.createActor(), "broadcaster");

		// Instanciate the other actors
	    final ActorRef a = system.actorOf(ActorA.createActor(broadcaster), "a");
        final ActorRef b = system.actorOf(ActorB.createActor(broadcaster), "b");
        final ActorRef c = system.actorOf(ActorC.createActor(broadcaster), "c");

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
