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

	public static final int numberBits = 5; // has to be under 16 because of the implementation of hashActorRef
	private static final int numberActors = 12; // has to be under 2^numberBits

	public static final boolean debugFixFingers = false;
	public static final boolean debugRingRepair = false;
	public static final boolean debugData = false;

	public static enum messages {ADD,  REMOVE, SUCCESSORDIE, PREDECESSORDIE, NOTIFY, 
		STABILIZE, FIXFINGERS, LOOKUP, FOUND, WELCOME, CHECKALIVE, NOTIFYNEIGHBOURS, STORE, DUMP, GET, ALLOWPRINT, PRINTVALUES, 
		PRINTFINGERTABLE, BADFINGERTABLE, WHODIDNTJOIN, KILL};


	public static int hashActorRef(ActorRef actorRef) {
		if (actorRef == null) {
			return -1;
		}
		// serialize the ActorRef
		byte[] data = SerializationUtils.serialize(actorRef);
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		data = Arrays.copyOfRange(md.digest(data), 0, 2); // hash the byte array and take the first two bytes and convert it to an int
		int a = ((data[0] & 0xFF) << 8 ) | ((data[1] & 0xFF) << 0 );
		return a%(1<<numberBits);
		// credits : https://stackoverflow.com/questions/7619058/convert-a-byte-array-to-integer-in-java-and-vice-versa
	}

	public static int hash(int i) {
		byte[] data = new byte[1];
		data[0] = (byte)i;
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
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
		IDs.add(hashActorRef(actors.get(0)));

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
			actors.get(0).tell(new MyMessage(messages.ADD, newID, actors.get(i)), ActorRef.noSender());
			
			System.out.print(i+" : ["+hashActorRef(actors.get(i))+"]\t" );
		} // the first actor could hash the ID himself but then the code for receiving the message from the outside would be different
		// than the code for receiving the message from the inside


		for (int i = 0; i < 10; i++) {
			/*System.out.println("\nWHO DIDNT JOIN "+i+"\n");
			for (ActorRef actor : actors) {
				actor.tell(new MyMessage(messages.WHODIDNTJOIN), ActorRef.noSender());
			}
			wait(1000);*/
			System.out.println("\nBAD FINGER TABLES "+i+"\n");
			for (ActorRef actor : actors) {
				actor.tell(new MyMessage(messages.BADFINGERTABLE), ActorRef.noSender());
			}
			wait(1000);
			/*System.out.println("\nFINGER TABLES "+i+"\n");
			for (ActorRef actor : actors) {
				actor.tell(new MyMessage(messages.PRINTFINGERTABLE), ActorRef.noSender());
			}
			wait(1000);*/
		}

		/*for (ActorRef actor : actors) {
			actor.tell(new MyMessage(messages.ALLOWPRINT, 1), ActorRef.noSender());
		}

		wait(5000);
		for (ActorRef actor : actors) {
			actor.tell(new MyMessage(messages.ALLOWPRINT, 1), ActorRef.noSender());
		}

		/*wait(9000);
		// print all the finger tables
		for (ActorRef actor : actors) {
			actor.tell(new MyMessage(messages.PRINTFINGERTABLE), ActorRef.noSender());
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
			actors.get(0).tell(new MyMessage(messages.STORE, i, Integer.toString(i)), ActorRef.noSender());
		}

		wait(2000);
		// print all the data
		/*for (ActorRef actor : actors) {
			actor.tell(new MyMessage(messages.PRINTVALUES), ActorRef.noSender());
		}*/
		
		//====================================
		//	testing reparaibility
		//====================================
		/* 
		// print all the finger tables
		for (ActorRef actor : actors) {
			actor.tell(new MyMessage(messages.PRINTFINGERTABLE), ActorRef.noSender());
		}
		wait(500);

		// kill a node
		actors.get(0).tell(new MyMessage(messages.KILL), ActorRef.noSender());
		System.out.println("killed a node");
		wait(5000);

		// print all the finger tables
		actors.remove(0);
		for (ActorRef actor : actors) {
			actor.tell(new MyMessage(messages.PRINTFINGERTABLE), ActorRef.noSender());
		}

		// print all the finger tables again
		for (ActorRef actor : actors) {
			actor.tell(new MyMessage(messages.PRINTFINGERTABLE), ActorRef.noSender());
		}
		*/

		//=========================================================
		//	testing data transmission in case of disconnection
		//=========================================================
		
		// storing somes values
		for (int i = 0; i < (1<<numberBits); i++) {
			actors.get(0).tell(new MyMessage(messages.STORE, i, Integer.toString(i)), ActorRef.noSender());
		}

		// print the values of the first node
		actors.get(0).tell(new MyMessage(messages.PRINTVALUES), ActorRef.noSender());

		// disconnect this node
		actors.get(0).tell(new MyMessage(messages.REMOVE, hashActorRef(actors.get(0)), actors.get(0)), ActorRef.noSender());
		System.out.println("disconnected a node");
		wait(5000);

		// print all the finger tables
		actors.remove(0);
		for (ActorRef actor : actors) {
			actor.tell(new MyMessage(messages.PRINTFINGERTABLE), ActorRef.noSender());
		}
		wait(5000);

		// print all the finger tables
		actors.remove(0);
		for (ActorRef actor : actors) {
			actor.tell(new MyMessage(messages.PRINTFINGERTABLE), ActorRef.noSender());
		}
		wait(5000);

		// print all the values
		actors.remove(0);
		for (ActorRef actor : actors) {
			actor.tell(new MyMessage(messages.PRINTVALUES), ActorRef.noSender());
		}
		wait(500);
		




		wait(5000);


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
