package demo;

import java.util.Arrays;
import org.apache.commons.lang3.SerializationUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * @author Remi SHARROCK
 * @description
 */
public class Chord {

	public static int hashActorRef(ActorRef actorRef) throws NoSuchAlgorithmException {
		// serialize the ActorRef
		byte[] data = SerializationUtils.serialize(actorRef);
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		data = Arrays.copyOfRange(md.digest(data), 0, 2); // hash the byte array and take the first byte and convert it to an int
		return ((data[0] & 0xFF) << 8 ) | 
			   ((data[1] & 0xFF) << 0 );
		// credits : https://stackoverflow.com/questions/7619058/convert-a-byte-array-to-integer-in-java-and-vice-versa
	}
	public static void main(String[] args) throws NoSuchAlgorithmException {

		final ActorSystem system = ActorSystem.create("system");
		final int numberBits = 10; // has to be under 16 because of the implementation of hashActorRef

		ArrayList<ActorRef> actors = new ArrayList<>();
		actors.add(system.actorOf(Actor.createActor(0, numberBits)));

		for (int i = 1 ; i < 8; i++) {
			actors.add(system.actorOf(Actor.createActor(i, numberBits)));
		}
		for (int i = 1 ; i < 8; i++) {
			actors.get(0).tell(new MyMessage("add", hashActorRef(actors.get(i)), actors.get(i)), ActorRef.noSender());
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
		Thread.sleep(2000);
	}

}
