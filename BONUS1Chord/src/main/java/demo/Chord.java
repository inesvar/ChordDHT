package demo;

import java.util.Arrays;
import java.util.Hashtable;
import java.lang.Integer;
import org.apache.commons.lang3.SerializationUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class Chord {

	public static final int numberBits = 10; // has to be under 16 because of the implementation of hashActorRef
	private static final int numberActors = 12;

	public static int hashActorRef(ActorRef actorRef) throws NoSuchAlgorithmException {
		// serialize the ActorRef
		byte[] data = SerializationUtils.serialize(actorRef);
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		data = Arrays.copyOfRange(md.digest(data), 0, 2); // hash the byte array and take the first two bytes and convert it to an int
		int a = ((data[0] & 0xFF) << 8 ) | ((data[1] & 0xFF) << 0 );
		return a%(1<<numberBits);
		// credits : https://stackoverflow.com/questions/7619058/convert-a-byte-array-to-integer-in-java-and-vice-versa
	}

	public static int hash(int i) throws NoSuchAlgorithmException {
		byte[] data = new byte[1];
		data[0] = (byte)i;
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		data = Arrays.copyOfRange(md.digest(data), 0, 2); // hash the byte array and take the first two bytes and convert it to an int
		int a = ((data[0] & 0xFF) << 8 ) | ((data[1] & 0xFF) << 0 );
		return a%(1<<numberBits);
		// credits : https://stackoverflow.com/questions/7619058/convert-a-byte-array-to-integer-in-java-and-vice-versa
	}

	public static void main(String[] args) throws NoSuchAlgorithmException {

		final ActorSystem system = ActorSystem.create("system");

		ArrayList<Integer> IDs = new ArrayList<>();
		ArrayList<ActorRef> actors = new ArrayList<>();
		actors.add(system.actorOf(Actor.createActor(0), "0"));
		System.out.print(0+" : ["+hashActorRef(actors.get(0))+"]\t" );

		for (int i = 1 ; i < numberActors; i++) {
			actors.add(system.actorOf(Actor.createActor(i), Integer.toString(i)));
		}
		int index = numberActors;

		for (int i = 1 ; i < numberActors; i++) {
			int newID = hashActorRef(actors.get(i));
			while (IDs.contains(newID)) {
				System.out.println(i+" : ID "+newID+" already exists");
				actors.set(i, system.actorOf(Actor.createActor(index), Integer.toString(index)));
				index++;
				newID = hashActorRef(actors.get(i));
			}
			IDs.add(newID);
			actors.get(0).tell(new MyMessage("add", newID, actors.get(i)), ActorRef.noSender());
			
			System.out.print(i+" : ["+hashActorRef(actors.get(i))+"]\t" );
		} // the first actor could hash the ID himself but then the code for receiving the message from the outside would be different
		// than the code for receiving the message from the inside


		for (int i = 0; i < 10; i++) {
			/*System.out.println("\nWHO DIDNT JOIN "+i+"\n");
			for (ActorRef actor : actors) {
				actor.tell(new MyMessage("whoDidntJoin"), ActorRef.noSender());
			}
			wait(1000);*/
			System.out.println("\nBAD FINGER TABLES "+i+"\n");
			for (ActorRef actor : actors) {
				actor.tell(new MyMessage("badFingerTable"), ActorRef.noSender());
			}
			wait(1000);
			/*System.out.println("\nFINGER TABLES "+i+"\n");
			for (ActorRef actor : actors) {
				actor.tell(new MyMessage("printFingerTable"), ActorRef.noSender());
			}
			wait(1000);*/
		}

		/*for (ActorRef actor : actors) {
			actor.tell(new MyMessage("allowPrint", 1), ActorRef.noSender());
		}

		wait(5000);
		for (ActorRef actor : actors) {
			actor.tell(new MyMessage("allowPrint", 1), ActorRef.noSender());
		}

		/*wait(9000);
		// print all the finger tables
		for (ActorRef actor : actors) {
			actor.tell(new MyMessage("printFingerTable"), ActorRef.noSender());
		}*/

		/*// store somes values
		Hashtable<Integer, String> values = new Hashtable<>();
		values.put(0, "hello");
		values.put(1, "world");
		values.put(2, "!");
		values.put(3, "I");
		values.put(4, "am");
		values.put(5, "speaking");
		values.put(6, "from");
		values.put(7, "outside");
		values.put(8, "the");
		values.put(9, "network");

		for (int i = 0; i < (1<<numberBits); i++) {
			actors.get(0).tell(new MyMessage("store", i, Integer.toString(i)), ActorRef.noSender());
		}

		wait(2000);
		// print all the data
		/*for (ActorRef actor : actors) {
			actor.tell(new MyMessage("printValues"), ActorRef.noSender());
		}

		// print all the finger tables
		*/for (ActorRef actor : actors) {
			actor.tell(new MyMessage("printFingerTable"), ActorRef.noSender());
		}

		//wait(500);

		// print the data of one node and then kill it
		//actors.get(0).tell(new MyMessage("printValues"), ActorRef.noSender());

		wait(500);

		actors.get(0).tell(new MyMessage("kill"), ActorRef.noSender());
		System.out.println("killed a node");

		wait(5000);

		actors.remove(0);
		// print all the finger tables
		for (ActorRef actor : actors) {
			actor.tell(new MyMessage("printFingerTable"), ActorRef.noSender());
		}

		wait(5000);

		//actors.remove(0);
		// print all the finger tables
		for (ActorRef actor : actors) {
			actor.tell(new MyMessage("printFingerTable"), ActorRef.noSender());
		}
		
		// actors.get(0).kill();


	    // We wait 5 seconds before ending system (by default)
	    // But this is not the best solution.
	    wait(50000);
		system.terminate();
	}

	public static void wait(int time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
