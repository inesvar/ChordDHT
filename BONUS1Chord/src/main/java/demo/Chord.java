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
	private static final int numberActors = 30; // has to be under 2^numberBits

	public static boolean debugFixFingers = false; // messages fixFingers, found
	public static boolean debugRingRepair = false; // messages stabilize, notify, welcome, successorDie, predecessorDie
	public static boolean debugData = true; // messages store, dump, get, add, remove
	public static boolean seeAllMessages = false;

	public static enum indirectMessages {STORE, DUMP, GET, ADD, REMOVE, LOOKUP};
	public static enum directMessages {GOT, SUCCESSORDIE, PREDECESSORDIE, WELCOME, NOTIFYSUCCESSOR, CHECKALIVE, FIXFINGERS, NOTIFY, FOUND, STABILIZE, PRINTVALUES, PRINTFINGERTABLE, BADFINGERTABLE, WHODIDNTJOIN, KILL};

	public static void main(String[] args) throws NoSuchAlgorithmException {

		final ActorSystem system = ActorSystem.create("system");

		ArrayList<Integer> IDs = new ArrayList<>();
		ArrayList<ActorRef> actors = new ArrayList<>();
		actors.add(system.actorOf(Actor.createActor(0), "0"));
		//System.out.print(0+" : ["+hashActorRef(actors.get(0))+"]\t" );
		IDs.add(hashActorRef(actors.get(0)));

		for (int i = 1 ; i < numberActors; i++) {
			actors.add(system.actorOf(Actor.createActor(i), Integer.toString(i)));
		}
		int index = numberActors;
		wait(500);

		for (int i = 1 ; i < numberActors; i++) {
			int newID = hashActorRef(actors.get(i));
			while (IDs.contains(newID)) {
				//System.out.println(i+" : ID "+newID+" already exists");
				actors.set(i, system.actorOf(Actor.createActor(index), Integer.toString(index)));
				index++;
				newID = hashActorRef(actors.get(i));
			}
			IDs.add(newID);
			actors.get(0).tell(new IndirectMessage(indirectMessages.ADD, newID, actors.get(i)), ActorRef.noSender());
			
			//System.out.print(i+" : ["+hashActorRef(actors.get(i))+"]\t" );
		} // the first actor could hash the ID himself but then the code for receiving the message from the outside would be different
		// than the code for receiving the message from the inside


		for (int i = 0; i < 7; i++) {
		//=============================================================
		// 	SOME PARAMETERS CHANGE DURING THE FORMATION OF THE RING
		//=============================================================
			/*System.out.println("\nLIST OF NODES WHO DIDNT JOIN AT TIME t = "+i+"\n");
			for (ActorRef actor : actors) {
				actor.tell(new IndirectMessage(indirectMessages.WHODIDNTJOIN), ActorRef.noSender());
			}
			wait(1000);*/
			/*System.out.println("\nLIST OF INCORRECT FINGER TABLES AT TIME t = "+i+"\n");
			for (ActorRef actor : actors) {
				actor.tell(new IndirectMessage(indirectMessages.BADFINGERTABLE), ActorRef.noSender());
			}
			wait(1000);*/
			System.out.println("\nLIST OF CORRECT FINGER TABLES AT TIME t = "+i+"\n");
			for (ActorRef actor : actors) {
				actor.tell(new DirectMessage(directMessages.PRINTFINGERTABLE), ActorRef.noSender());
			}
			wait(1000);
		}

		//====================================
		//		   DATA MANIPULATION
		//====================================
		
		System.out.println("\nDATA MANIPULATION TEST\n");
		debugData = true;
		// store somes values
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

		wait(2000);
		// store some data
		System.out.println("STORING DATA");
		for (int i = 0; i < (1<<numberBits); i++) {
			actors.get(0).tell(new IndirectMessage(indirectMessages.STORE, i, Integer.toString(i)), ActorRef.noSender());
		}

		wait(2000);
		// print all the data
		System.out.println("PRINTING ALL THE DATA");
		for (ActorRef actor : actors) {
			actor.tell(new DirectMessage(directMessages.PRINTVALUES), ActorRef.noSender());
		}

		wait(1000);
		// erase all values that aren't multiples of 2**(numberBits - 3)
		System.out.println("ERASING MOST OF THE DATA, KEEPING ONLY 8 VALUES");
		for (int i = 0; i < (1<<numberBits); i++) {
			if (i%(1<<(numberBits - 3)) != 0)
			actors.get(0).tell(new IndirectMessage(indirectMessages.DUMP, i), ActorRef.noSender());
		}

		wait(2000);
		// print all the data
		System.out.println("PRINTING ALL THE DATA");
		for (ActorRef actor : actors) {
			actor.tell(new DirectMessage(directMessages.PRINTVALUES), ActorRef.noSender());
		}

		wait(2000);
		// node 0 fetches all the data
		System.out.println("NODE ["+hashActorRef(actors.get(0))+"] FETCHING ALL THE DATA");
		for (int i = 0; i < 8; i++) {
			actors.get(0).tell(new IndirectMessage(indirectMessages.GET, (i*1<<(numberBits-3))), actors.get(0));
		}
		wait(2000);
		debugData = false;
		

		//====================================
		//			NODE FAILURE
		//====================================
		
		System.out.println("\nNODE FAILURE TEST\n");
		// print all the finger tables
		System.out.println("\nPrinting all the finger tables");
		for (ActorRef actor : actors) {
			actor.tell(new DirectMessage(directMessages.PRINTFINGERTABLE), ActorRef.noSender());
		}
		wait(500);

		// kill a node
		actors.get(0).tell(new DirectMessage(directMessages.KILL), ActorRef.noSender());
		System.out.println("\nkilled node ["+hashActorRef(actors.get(0))+"]");
		wait(5000);

		// print all the finger tables
		System.out.println("\nPrinting all the finger tables");
		actors.remove(0);
		for (ActorRef actor : actors) {
			actor.tell(new DirectMessage(directMessages.PRINTFINGERTABLE), ActorRef.noSender());
		}
		wait(3000);
		System.out.println("\nPrinting all the finger tables again");
		// print all the finger tables again
		for (ActorRef actor : actors) {
			actor.tell(new DirectMessage(directMessages.PRINTFINGERTABLE), ActorRef.noSender());
		}
		wait(1000);
		

		//======================================
		//		GRACEFUL DISCONNECTION
		//======================================
		
		System.out.println("\nDISCONNECTION TEST\n");
		// storing somes values
		System.out.println("\nStored some values");
		for (int i = 0; i < (1<<numberBits); i++) {
			actors.get(0).tell(new IndirectMessage(indirectMessages.STORE, i, Integer.toString(i)), ActorRef.noSender());
		}

		// print the values of the first node
		System.out.println("\nPrinting values of node ["+hashActorRef(actors.get(0))+"]");
		actors.get(0).tell(new DirectMessage(directMessages.PRINTVALUES), ActorRef.noSender());

		// disconnect this node
		actors.get(2).tell(new IndirectMessage(indirectMessages.REMOVE, hashActorRef(actors.get(0))), ActorRef.noSender());
		System.out.println("\ndisconnected node ["+hashActorRef(actors.get(0))+"]");
		wait(2000);
		actors.remove(0);

		// print all the values
		System.out.println("\nPrinting all the values");
		for (ActorRef actor : actors) {
			actor.tell(new DirectMessage(directMessages.PRINTVALUES), ActorRef.noSender());
		}
		wait(7000);

		// print all the values
		System.out.println("\nPrinting all the finger tables");
		for (ActorRef actor : actors) {
			actor.tell(new DirectMessage(directMessages.PRINTFINGERTABLE), ActorRef.noSender());
		}
		wait(5000);
		

		//====================================
		//	PRINTING NOTIFY & FIXFINGERS
		//====================================
		/*
		for (ActorRef actor : actors) {
			actor.tell(new DirectMessage(directMessages.ALLOWPRINT, 1), ActorRef.noSender());
		}

		wait(5000);
		for (ActorRef actor : actors) {
			actor.tell(new DirectMessage(directMessages.ALLOWPRINT, 1), ActorRef.noSender());
		}
		*/

		//=======================
		//		TERMINATION
		//=======================
		wait(8000);
		system.terminate();
	}

	public static void wait(int time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

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

}
