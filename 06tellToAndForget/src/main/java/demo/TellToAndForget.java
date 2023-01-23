package demo;

import java.util.ArrayList;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * @author Remi SHARROCK
 * @description
 */
public class TellToAndForget {

	public static void main(String[] args) {

		final ActorSystem system = ActorSystem.create("system");
		
		// Instantiate first and second actor
	    final ActorRef a = system.actorOf(ActorA.createActor(), "a");
        final ActorRef t = system.actorOf(Transmitter.createActor(), "transmitter");
        final ActorRef b = system.actorOf(ActorB.createActor(), "b");
	    
		ArrayList<ActorRef> refs = new ArrayList<>();
		refs.add(t);
		refs.add(b);
	    a.tell(new MyMessage(refs), ActorRef.noSender());


	    a.tell(new MyMessage("start"), ActorRef.noSender());


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
