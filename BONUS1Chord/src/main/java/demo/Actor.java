package demo;

import java.util.Arrays;
import java.util.Hashtable;
import org.apache.commons.lang3.SerializationUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.concurrent.locks.ReentrantLock;


public class Actor extends UntypedAbstractActor {

	// Logger attached to actor
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	// Identifier (an int with a method isBetween)
	private CircleInt ID;
	// the finger table and then at index numberBits the predecessor
	private ArrayList<ActorRef> fingerTable;
	// List of (key, value) stored in the node
	private Hashtable<Integer, String> values;
	// List of keys stored in the node
	private ArrayList<Integer> keys;
	// number of bits used for the IDs
	private int numberBits;
	
	private boolean sentPing;

	private boolean isNew = true;

	private boolean isFixingFingers = false;

	private ReentrantLock lock = new ReentrantLock();


	public Actor(int ID, int numberBits) {
		this.numberBits = numberBits;
		try {
			this.ID = new CircleInt(hashActorRef(getSelf()));
			//System.out.println("this ID is "+this.ID.convertToInt());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			this.ID = null;
		}
		this.fingerTable = new ArrayList<>();
		// the first node has to know its own predecessor is itself
		for (int i = 0; i < numberBits + 1; i++) {
			this.fingerTable.add(getSelf());
		}
		this.values = new Hashtable<>();
		this.keys = new ArrayList<>();
		this.sentPing = false;
		System.out.println("Node "+this.ID.convertToInt()+" created");
	}

	// Static function creating actor
	public static Props createActor(int ID, int numberBits) {
		return Props.create(Actor.class, () -> {
			return new Actor(ID, numberBits);
		});
	}

	public int getFingerTableID(int index){
		try {
			//System.out.println("not getting a new ID");
			return hashActorRef(fingerTable.get(index))%(1<<numberBits);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return -1;
		}
	}

	public int hashActorRef(ActorRef actorRef) throws NoSuchAlgorithmException {
		// serialize the ActorRef
		byte[] data = SerializationUtils.serialize(actorRef);
		//System.out.println("hashing "+data);
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		data = Arrays.copyOfRange(md.digest(data), 0, 2); // hash the byte array and take the first two bytes and convert it to an int
		//System.out.println("obtaining "+data);
		int a = ((data[0] & 0xFF) << 8 ) | ((data[1] & 0xFF) << 0 );
		return a%(1<<numberBits);
		// credits : https://stackoverflow.com/questions/7619058/convert-a-byte-array-to-integer-in-java-and-vice-versa
	}

	// returns the index in the finger table that the message should be sent to
	public int efficientlySendTo(CircleInt id) {
		int distance = id.subtract(this.ID);
		int i = numberBits - 1; 
		while (i > 0) {
			if (distance >= (1<<i)) {
				break;
			}
			i--;
		}
		//System.out.println("Node "+ID.convertToInt()+" efficiently sending to "+getFingerTableID(i)+" (distance "+distance+" and i "+i+")");
		return i;
	}

	public void printMyFingerTable(String s) {
		int[] fingerTableIDs = new int[numberBits + 1];
		for (int i = 0; i < numberBits + 1; i++) {
			fingerTableIDs[i] = getFingerTableID(i);
		}
		System.out.println("Node "+ID.convertToInt()+" finger table : "+Arrays.toString(fingerTableIDs)+" "+s);
	}

	// sends some values to newPredecessor and returns the oldPredecessor
	public ActorRef greetNewPredecessor(CircleInt id, ActorRef newPredecessor) {
		// update the finger table
		ActorRef oldPredecessor = fingerTable.get(numberBits);
		fingerTable.set(numberBits, newPredecessor);
		if (isNew) {
			isNew = false;
			for (int i = 0; i < numberBits; i++) {
				fingerTable.set(i, newPredecessor);
			}
		}

		for (int key : keys) {
			if (id.isBetween(key, this.ID)) {
				fingerTable.get(0).tell(new MyMessage("store", key, values.get(key)), ActorRef.noSender());
			}
		}
		return oldPredecessor;
	}


	@Override
	public void onReceive(Object message) throws Throwable {
		if (message instanceof MyMessage) {
			MyMessage m = (MyMessage) message;


			if (m.string.equals("add")) { // a new node is being added to the network
				////log.info("["+ID.convertToInt()+"] received a message "+m.string);
				CircleInt destID = new CircleInt(m.ID);
				if (ID.isSuccessor(destID)) { 
					// if this node is the successor of the new node
					// it's the case when the new node is the second node to be added to the network
					// tell the new node your fingerTable and your ID
					m.actorRef.tell(new MyMessage("welcome",fingerTable), getSelf());
					ActorRef oldPredecessor = greetNewPredecessor(destID, m.actorRef);
					// tell the old predecessor that it has a new successor
					oldPredecessor.tell(new MyMessage("signal", "fromPreviousSuccessor", fingerTable.get(numberBits)), ActorRef.noSender());
					System.out.println("["+ID.convertToInt()+"] added a new node "+destID.convertToInt());
					printMyFingerTable("add");
					//take the Reentrant lock
					lock.lock();
					if (isFixingFingers == false)
						getSelf().tell(new MyMessage("fix_fingers"), ActorRef.noSender());
					//release the Reentrant lock
					lock.unlock();

				} else {
					// send the message to the right node
					int index = efficientlySendTo(destID);
					fingerTable.get(index).tell(m, ActorRef.noSender());
				} 

			} else if (m.string.equals("remove")) { // a node is being removed from the network
				//log.info("["+ID.convertToInt()+"] received a message "+m.string);
				CircleInt destID = new CircleInt(m.ID);
				if (ID.isSuccessor(destID)) { 
					// if this node is to be removed
					// tell your successor your predecessor
					fingerTable.get(1).tell(new MyMessage("signal", "fromDyingPredecessor", fingerTable.get(numberBits)), ActorRef.noSender());
					// tell your predecessor your successor
					fingerTable.get(numberBits).tell(new MyMessage("signal", "fromDyingSuccessor", fingerTable.get(0)), ActorRef.noSender());
					printMyFingerTable("remove");
				} else {
					// send the message to the right node
					int index = efficientlySendTo(destID);
					fingerTable.get(index).tell(m, ActorRef.noSender());
				}
				
			} else if (m.string.equals("signal")) { // some change in neighbours occured
				//log.info("["+ID.convertToInt()+"] received a message "+m.string + " " + m.string2);
				if (m.string2.equals("fromPreviousSuccessor")) { 
					// a node has been added it's your successor
					fingerTable.set(1, fingerTable.get(0));
					fingerTable.set(0, m.actorRef);
					

				} else if (m.string2.equals("fromDyingSuccessor")) { 
					// your successor will be removed
					fingerTable.set(0, m.actorRef); // m.actorRef is also fingerTable.get(1)

				} else if (m.string2.equals("fromDyingPredecessor")) {
					// your predecessor will be removed
					fingerTable.set(numberBits, m.actorRef);
				}
				printMyFingerTable("signal");
			
			} else if (m.string.equals("welcome")) { // you've been added to the network
				//log.info("["+ID.convertToInt()+"] received a message "+m.string);
				fingerTable = m.fingerTable;
				fingerTable.set(1, fingerTable.get(0));
				fingerTable.set(0, getSender());
				isNew = false;
				printMyFingerTable("welcome");


			} else if (m.string.equals("store")) {
				//log.info("["+ID.convertToInt()+"] received a message "+m.string);
				CircleInt destID = new CircleInt(m.ID);
				if (ID.isSuccessor(destID)) { 
					// if this node is the successor of the key
					// store the key and value
					values.put(m.ID, m.value);
				} else {
					// send the message to the right node
					int index = efficientlySendTo(destID);
					fingerTable.get(index).tell(m, ActorRef.noSender());
				} 



			} else if (m.string.equals("dump")) {
				//log.info("["+ID.convertToInt()+"] received a message "+m.string);
				CircleInt destID = new CircleInt(m.ID);
				if (ID.isSuccessor(destID)) { 
					// if this node is the successor of the key
					// dump the key and value
					values.remove(m.ID);
				} else {
					// send the message to the right node
					int index = efficientlySendTo(destID);
					fingerTable.get(index).tell(m, ActorRef.noSender());
				} 

			} else if (m.string.equals("get")) {
				//log.info("["+ID.convertToInt()+"] received a message "+m.string);
				ActorRef sender = getSender();
				CircleInt destID = new CircleInt(m.ID);
				if (ID.isSuccessor(destID)) { 
					// if this node is the successor of the key
					// return the value
					sender.tell(values.get(m.ID), ActorRef.noSender());
				
				} else {
					// send the message to the right node
					int index = efficientlySendTo(destID);
					fingerTable.get(index).tell(m, sender);
				}
				printMyFingerTable("get");

			} else if (m.string.equals("fix_fingers")) {
				lock.lock();
				if (isFixingFingers == false) {
					isFixingFingers = true;
					//log.info("["+ID.convertToInt()+"] received a message "+m.string);
					// check that each finger is correct
					System.out.println("["+ID.convertToInt()+"] fix fingers");

					for (int i = 0; i < numberBits; i++) {
						int fingerID = ID.convertToInt() + (1<<i);
						fingerTable.get(0).tell(new MyMessage("lookup", i, fingerID), getSelf());
					}
					isFixingFingers = false;
					// send the message to the next node
					fingerTable.get(0).tell(m, ActorRef.noSender());
				}
				lock.unlock();

			} else if (m.string.equals("found")) {
				//log.info("["+ID.convertToInt()+"] received a message "+m.string);
				// check that the finger is correct
				fingerTable.set(m.number, getSender());
				System.out.println("["+ID.convertToInt()+"] finger "+m.number+" is "+hashActorRef(getSender()));

				printMyFingerTable("fix_fingers");
			} else if (m.string.equals("notify")) {
				//log.info("["+ID.convertToInt()+"] received a message "+m.string);
				CircleInt sender = new CircleInt(hashActorRef(getSender()));
				// check that the sender is the predecessor
				if (getSender().equals(fingerTable.get(numberBits))) {
					// send the message to the next node
					fingerTable.get(0).tell(m, getSelf());
				} else if (fingerTable.get(numberBits) == null){
					fingerTable.set(numberBits, getSender()); 
				}
				printMyFingerTable("notify");
			
			} else if (m.string.equals("ping")) {
				if (sentPing == false) {
					//log.info("["+ID.convertToInt()+"] received a message "+m.string);
					getSender().tell(m, getSelf()); // ping back
					sentPing = true;
					fingerTable.get(numberBits).tell(m, getSelf()); // ping own predecessor
				} else {
					sentPing = false;
				}
			} else if (m.string.equals("lookup")) {
				//log.info("["+ID.convertToInt()+"] received a message "+m.string);
				CircleInt destID = new CircleInt(m.ID);
				if (ID.isSuccessor(destID)) { 
					getSender().tell(new MyMessage("found", m.number, this.ID.convertToInt()), getSelf());
					System.out.println("["+ID.convertToInt()+"] is the finger number "+m.number+" of "+hashActorRef(getSender()));
					//printMyFingerTable("lookup");
				} else {
					// send the message to the right node
					int index = efficientlySendTo(destID);
					fingerTable.get(index).tell(m, getSender());
				} 
			}
		}
	}



	class CircleInt {
		private int value;
	
		public CircleInt(int value) {
			//System.out.println("received value "+value+ " " + (1 << numberBits) + " " + (value % (1024)));
			this.value = value % (1 << numberBits);
			//System.out.println("converted value "+this.value);
		}
	
		public int convertToInt() {
			return value;
		}
	
		// Returns true if a <= value <= b in the circle
		// if a == b, returns true
		/*public boolean isBetween(int a, int b) {
			if (a < b) {
				return a <= value && value <= b;
			} else {
				return a <= value || value <= b;
			}
		}

		// Returns true if a <= value <= b in the circle
		public boolean isBetween(CircleInt ca, int b) {
			int a = ca.convertToInt();
			if (a < b) {
				return a <= value && value <= b;
			} else {
				return a <= value || value <= b;
			}
		}*/

		// Returns true if a <= value < b in the circle
		// if a == b, returns true
		public boolean isBetween(int a, CircleInt cb) {
			int b = cb.convertToInt();
			if (a < b) {
				return a <= value && value < b;
			} else {
				return a <= value || value < b;
			}
		}

		// Returns true if a <= value < b in the circle
		// if a == b, returns true
		/*public boolean isBetween(CircleInt ca, CircleInt cb) {
			int a = ca.convertToInt();
			int b = cb.convertToInt();
			if (a < b) {
				return a <= value && value < b;
			} else {
				return a <= value || value < b;
			}
		}*/
		
		// Returns the clockwise distance from other to this in the circle
		public int subtract(CircleInt other) {
			return (value - other.value) % (1 << numberBits);
		}
		
		// Returns the addition of this and other in the circle
		public int add(int other) {
			return (value + other) % (1 << numberBits);
		}
		
		// Returns true if other is the predecessor of this
		// or if this is the only node in the network
		public boolean isSuccessor(CircleInt other) {
			return other.isBetween(getFingerTableID(numberBits), ID);
		}
	}
}
