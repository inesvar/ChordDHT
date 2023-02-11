package demo;

import java.util.Arrays;
import java.util.Hashtable;
import org.apache.commons.lang3.SerializationUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;


public class Actor extends UntypedAbstractActor {

	// Logger attached to actor
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	// Identifier (an int with a method isBetween)
	private CircleInt ID;
	// the finger table and then at index numberBits the predecessor
	private ArrayList<ActorRef> fingerTable;
	// List of (key, value) stored in the node
	private Hashtable<Integer, String> values = new Hashtable<>();
	// List of keys stored in the node
	private ArrayList<Integer> keys = new ArrayList<>();;
	// number of bits used for the IDs
	private int numberBits = Chord.numberBits;
	// when the predecessor doesn't ping two times in a row, the predecessor is dead
	private int predecessorAlive = 2;
	private int successorAlive = 2;

	// 1 : doesn't know anyone
	private boolean isAlone = true;

	private Boolean problematicFingerTable = true;

	private boolean isFixingFingers = false;
	private boolean recentlyFixedFingers = false;
	private int numberFingersChecked = 0;

	private boolean allowedToPrint = false;


	// has stopped communicating
	private boolean isDead = false;

	public Actor(int ID) {

		try {
			this.ID = new CircleInt(hashActorRef(getSelf()));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			this.ID = null;
		}

		this.fingerTable = new ArrayList<>();
		for (int i = 0; i < numberBits + 1; i++) {
			this.fingerTable.add(getSelf());
		}
		
		if (allowedToPrint) {
			System.out.println("Node "+this.ID.toInt()+" created");
		}
	}

	// Static function creating actor
	public static Props createActor(int ID) {
		return Props.create(Actor.class, () -> {
			return new Actor(ID);
		});
	}

	public void allowPrint(boolean b) {
		allowedToPrint = b;
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
		if (actorRef == null) {
			return -1;
		}
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
	public int appropriateFinger(CircleInt id) {
		// example :
		// id = 54, ID = 228, numberBits = 8 (256-circle)
		// distance = 54 - 228 [256] = 82 [256]
		int distance = id.subtract(this.ID);
		// i = 7
		int i = numberBits - 1;
		while (i > 0) {
			// if distance >= 2^i then we can send the message to the node at index i
			if (distance >= (1<<i)) {
				break;
			}
			i--;
		}
		// i = 6
		// passed to successor of ID + 64
		//System.out.println("Node "+ID.toInt()+" efficiently sending to "+getFingerTableID(i)+" (distance "+distance+" and i "+i+")");
		return i;
	}

	public void printFingerTable(String s, boolean forcePrint) {
		int[] fingerTableIDs = new int[numberBits + 1];
		for (int i = 0; i < numberBits + 1; i++) {
			fingerTableIDs[i] = getFingerTableID(i);
		}
		if (allowedToPrint | forcePrint) {
			System.out.println("Node "+ID.toInt()+" finger table : "+Arrays.toString(fingerTableIDs)+" "+s);
		}
	}

	// sends some values to newPredecessor and returns the oldPredecessor
	public ActorRef greetNewPredecessor(CircleInt id, ActorRef newPredecessor) {
		// update the finger table
		ActorRef oldPredecessor = fingerTable.get(numberBits);
		fingerTable.set(numberBits, newPredecessor);
		if (isAlone) {
			// if the node is alone then its fingerTable is filled with the second node
			isAlone = false;
			for (int i = 0; i < numberBits; i++) {
				fingerTable.set(i, newPredecessor);
			}
			setUpPeriodicalMessages();
		}

		//sending the appropriate values to the new predecessor
		for (int key : keys) {
			if (!isSuccessor(key)) {
				fingerTable.get(numberBits).tell(new MyMessage("store", key, values.get(key)), ActorRef.noSender());
			}
		}
		return oldPredecessor;
	}

	public void setUpPeriodicalMessages() {
		// set up the periodical messages
		getContext().system().scheduler().schedule(Duration.ofMillis(5000), Duration.ofMillis(5000), getSelf(), new MyMessage("fix_fingers"), getContext().system().dispatcher(), ActorRef.noSender());
		getContext().system().scheduler().schedule(Duration.ofMillis(4000), Duration.ofMillis(2000), getSelf(), new MyMessage("checkAlive"), getContext().system().dispatcher(), ActorRef.noSender());
		getContext().system().scheduler().schedule(Duration.ofMillis(0), Duration.ofMillis(1000), getSelf(), new MyMessage("notifyNeighbours"), getContext().system().dispatcher(), ActorRef.noSender());
	}

	public void transmitAllValues() {
		for (int key : keys) {
			fingerTable.get(0).tell(new MyMessage("store", key, values.get(key)), ActorRef.noSender());
		}
	}

	public void kill() {
		isDead = true;
	}


	@Override
	public void onReceive(Object message) throws Throwable {
		if (message instanceof MyMessage && !isDead) {
			MyMessage m = (MyMessage) message;


			if (m.string.equals("add")) { // a new node is being added to the network
				synchronized(this) {
					////log.info("["+ID.toInt()+"] received a message "+m.string);
					CircleInt destID = new CircleInt(m.ID);
					if (isSuccessor(destID)) { 
						// if this node is the successor of the new node
						// it's the case when the new node is the second node to be added to the network 
						// tell the new node your fingerTable and your ID
						m.actorRef.tell(new MyMessage("welcome", fingerTable), getSelf());
						// update your finger table + send values to the new node
						ActorRef oldPredecessor = greetNewPredecessor(destID, m.actorRef);
						// tell the old predecessor that it has a new successor
						if (predecessorAlive > 0)
							oldPredecessor.tell(new MyMessage("signal", "fromPreviousSuccessor", fingerTable.get(numberBits)), ActorRef.noSender());
						if (allowedToPrint) {
							System.out.println("["+ID.toInt()+"] added a new node "+destID.toInt());
						}
						printFingerTable("add", false);
					} else {
						// send the message to the right node
						fingerTable.get(appropriateFinger(destID)).tell(m, ActorRef.noSender());
						//System.out.println("["+ID.toInt()+"] transmitting to add "+destID.toInt()+" to "+getFingerTableID(appropriateFinger(destID)));
					} 
				}

			} else if (m.string.equals("remove")) { // a node is being removed from the network
				synchronized(this) {
					//log.info("["+ID.toInt()+"] received a message "+m.string);
					CircleInt destID = new CircleInt(m.ID);
					if (isSuccessor(destID)) { 
						// if this node is to be removed
						// tell your successor your predecessor
						if (predecessorAlive > 0) {
							fingerTable.get(0).tell(new MyMessage("signal", "fromDyingPredecessor", fingerTable.get(numberBits)), ActorRef.noSender());
						}
						// tell your predecessor your successor
						if (predecessorAlive > 0) {
							fingerTable.get(numberBits).tell(new MyMessage("signal", "fromDyingSuccessor", fingerTable.get(0)), ActorRef.noSender());
						}
						// send your values
						transmitAllValues();
						printFingerTable("remove", false);
					} else {
						// send the message to the right node
						fingerTable.get(appropriateFinger(destID)).tell(m, ActorRef.noSender());
					}
				}
				
			} else if (m.string.equals("signal")) { // some change in neighbours occured
				synchronized(this) {
					//log.info("["+ID.toInt()+"] received a message "+m.string + " " + m.string2);
					if (m.string2.equals("fromPreviousSuccessor")) { 
						// a node has been added it's your successor
						// update your finger table
						CircleInt newSuccessor = new CircleInt(hashActorRef(m.actorRef));
						int index = appropriateFinger(newSuccessor);
						//fingerTable.set(1, fingerTable.get(0));
						for (int i = 0; i <= index; i++) {
							fingerTable.set(i, m.actorRef);
						}
						for (int i = index + 1; i < numberBits; i++) {
							if (newSuccessor.isBetween(ID, getFingerTableID(i))) {
								fingerTable.set(i, m.actorRef);
							} else {
								break;
							}
						}
						printFingerTable("signal fromPreviousSuccessor", false);
						

					} else if (m.string2.equals("fromDyingSuccessor")) { 
						// your successor will be removed
						fingerTable.set(0, m.actorRef); // m.actorRef is also fingerTable.get(1)
						printFingerTable("signal fromDyingSuccessor", false);

					} else if (m.string2.equals("fromDyingPredecessor")) {
						// your predecessor will be removed
						fingerTable.set(numberBits, m.actorRef);
						printFingerTable("signal fromDyingPredecessor", false);
					}
					
				}
			
			} else if (m.string.equals("welcome")) { // you've been added to the network
				synchronized(this) {
					isAlone = false;
					//log.info("["+ID.toInt()+"] received a message "+m.string);
					for (int i = 0; i < numberBits; i++) {
						fingerTable.set(i, getSender());
					}
					fingerTable.set(numberBits, m.fingerTable.get(numberBits));
					//fingerTable.set(0, getSender());
					setUpPeriodicalMessages();
					printFingerTable("welcome", false);
				}


			} else if (m.string.equals("store")) {

				synchronized(this) {
					//log.info("["+ID.toInt()+"] received a message "+m.string);
					CircleInt destID = new CircleInt(m.ID);
					if (isSuccessor(destID)) { 
						// if this node is the successor of the key
						// store the key and value
						values.put(m.ID, m.value);
						keys.add(m.ID);
					} else {
						// send the message to the right node
						int index = appropriateFinger(destID);
						fingerTable.get(index).tell(m, ActorRef.noSender());
					} 
				}

			} else if (m.string.equals("dump")) {
				synchronized(this) {
					//log.info("["+ID.toInt()+"] received a message "+m.string);
					CircleInt destID = new CircleInt(m.ID);
					if (isSuccessor(destID)) { 
						// if this node is the successor of the key
						// dump the key and value
						values.remove(m.ID);
						keys.remove(m.ID);
					} else {
						// send the message to the right node
						int index = appropriateFinger(destID);
						fingerTable.get(index).tell(m, ActorRef.noSender());
					} 
				}

			} else if (m.string.equals("get")) {
				synchronized(this) {
					//log.info("["+ID.toInt()+"] received a message "+m.string);
					CircleInt destID = new CircleInt(m.ID);
					if (isSuccessor(destID)) { 
						// if this node is the successor of the key
						// return the value
						getSender().tell(values.get(m.ID), ActorRef.noSender());
					
					} else {
						// send the message to the right node
						int index = appropriateFinger(destID);
						fingerTable.get(index).tell(m, getSender());
					}
					printFingerTable("get", false);
				}

			} else if (m.string.equals("fix_fingers")) {

				synchronized(this) {
					//getContext().system().scheduler().scheduleOnce(Duration.ofMillis(5000), getSelf(), new MyMessage("fix_fingers"), getContext().system().dispatcher(), ActorRef.noSender());
					
					if (!isFixingFingers& !recentlyFixedFingers) {
						isFixingFingers = true;
						numberFingersChecked = 0;
						//log.info("["+ID.toInt()+"] received a message "+m.string);
						// check that each finger is correct
						//System.out.println("["+ID.toInt()+"] fix fingers");

						for (int i = 0; i < numberBits; i++) {
							int fingerID = ID.toInt() + (1<<i);
							fingerTable.get(0).tell(new MyMessage("lookup", i, fingerID), getSelf());
						}
						//printFingerTable("fix_fingers", false);
						recentlyFixedFingers = true;
					} else if (recentlyFixedFingers) {
						recentlyFixedFingers = false;
					}
				}

			} else if (m.string.equals("found")) {

				synchronized(this) {
					//log.info("["+ID.toInt()+"] received a message "+m.string);
					// check that the finger is correct
					//CircleInt candidatID = new CircleInt(m.ID);
					//CircleInt fingerID = new CircleInt(ID.add((1<<m.number)));
					//if (candidatID.isSuccessor(fingerID)) {
						fingerTable.set(m.number, getSender());
						numberFingersChecked++;
						if (allowedToPrint)
							System.out.println("["+ID.toInt()+"] finger "+m.number+" is "+hashActorRef(getSender())+" ("+numberFingersChecked+")");
					//}
					if (numberFingersChecked == numberBits) {
						isFixingFingers = false;
						problematicFingerTable = false;
						printFingerTable("fix_fingers", false);
					}
				}

			} else if (m.string.equals("notify")) {

				synchronized(this) {
					//log.info("["+ID.toInt()+"] received a message "+m.string);

					CircleInt sender = new CircleInt(hashActorRef(getSender()));
					getSender().tell(new MyMessage("stabilize", fingerTable.get(numberBits)), ActorRef.noSender());
					
					// message from predecessor
					if (fingerTable.get(numberBits) == getSender()) {
						predecessorAlive = 2;
					
					} else if (fingerTable.get(numberBits) == null) {
						fingerTable.set(numberBits, getSender());
						predecessorAlive = 2;
					
					} else if (sender.isBetween(getFingerTableID(numberBits), ID)) {
						// make sure the old predecessor is updated
						System.out.println("so it happens");
						fingerTable.get(numberBits).tell(new MyMessage("signal", "fromPreviousSuccessor", getSender()), getSelf());
						fingerTable.set(numberBits, getSender()); 
						predecessorAlive = 2;
					} else {
						if (allowedToPrint)
							System.out.println("not supposed to happen ["+ID.toInt()+"] notifyied by "+sender.toInt());
					
					}
					printFingerTable("was notifyied by "+sender.toInt()+" predecessor is "+predecessorAlive, false);
				}
					
			} else if (m.string == "stabilize") {

				synchronized(this) {
					// message from successor
					successorAlive = 2;
					if (getSelf() == m.actorRef) {
						return;
					}
					CircleInt successor = new CircleInt(hashActorRef(m.actorRef));
					if (successor.isBetween(ID, getFingerTableID(0))) {
						fingerTable.set(0, m.actorRef); 
					} else {
						//if (allowedToPrint)
							System.out.println("not supposed to happen ["+ID.toInt()+"] stabilized by "+successor.toInt());
						
					}
					printFingerTable("was notifyied , successor is "+getFingerTableID(0), false);
				}
				
			} else if (m.string.equals("notifyNeighbours")) {

				synchronized(this) {
					fingerTable.get(0).tell(new MyMessage("notify"), getSelf());
					/*if (fingerTable.get(numberBits) != null) {
						fingerTable.get(numberBits).tell(new MyMessage("notify", 0, ID.toInt()), getSelf());
					}*/
				}
			
			} else if (m.string.equals("checkAlive")) {

				synchronized(this) {
					predecessorAlive -= 1;
					successorAlive -= 1;
					if (predecessorAlive == 0) {
						// predecessor is dead
						//if (allowedToPrint)
							System.out.println("["+ID.toInt()+"] predecessor is dead");
						
						fingerTable.set(numberBits, null);
					}
					if (successorAlive == 0) {
						// successor is dead
						//if (allowedToPrint)
							System.out.println("["+ID.toInt()+"] successor is dead");
						
						int i = 1;
						while (fingerTable.get(i) == fingerTable.get(0)) {
							i++;
						}
						for (int j = 0; j < i; j++) {
							fingerTable.set(j, fingerTable.get(i));
						}
					}
					printFingerTable("checkAlive successor"+successorAlive+"predecessor"+predecessorAlive, false);
				}

			} else if (m.string.equals("lookup")) {

				synchronized(this) {
					//log.info("["+ID.toInt()+"] received a message "+m.string);
					CircleInt destID = new CircleInt(m.ID);
					if (isSuccessor(destID)) { 
						getSender().tell(new MyMessage("found", m.number, this.ID.toInt()), getSelf());
						if (allowedToPrint)
							System.out.println("["+ID.toInt()+"] is the finger number "+m.number+" of "+hashActorRef(getSender()));
						//printFingerTable("lookup", false);
					} else {
						// send the message to the right node
						fingerTable.get(appropriateFinger(destID)).tell(m, getSender());
					} 
				}
			} else if (m.string.equals("printFingerTable")) {

				synchronized(this) {
					if (!problematicFingerTable) {
						printFingerTable(null, true);
					}
				}
			
			} else if (m.string.equals("printValues")) {

				synchronized(this) {
					for (int key : keys) {
						System.out.print("["+ID.toInt()+"] : "+key+" "+values.get(key)+"\t");
					}
				}
			
			} else if (m.string.equals("whoDidntJoin")) {

				synchronized(this) {
					if (isAlone) {
						printFingerTable(null, true);
					}
				}
			} else if (m.string.equals("badFingerTable")) {

				synchronized(this) {
					if (problematicFingerTable) {
						printFingerTable(null, true);
					}
				}
			} else if (m.string.equals("allowPrint")) {

				synchronized(this) {
					allowPrint(m.ID == 1);
				}
			} else if (m.string.equals("kill")) {

				synchronized(this) {
					kill();
				}
			}

		}
	}

	// Returns true if this is the successor of other
	public boolean isSuccessor(CircleInt other) {
		if (predecessorAlive < 0) {
			return other.isBetween(ID.virtualPredecessor(), ID);
		}
		return other.isBetween(getFingerTableID(numberBits), ID);
	}

	// Returns true if this is the successor of other
	public boolean isSuccessor(int o) {
		CircleInt other = new CircleInt(o);
		if (predecessorAlive < 0) {
			return other.isBetween(ID.virtualPredecessor(), ID);
		}
		return other.isBetween(getFingerTableID(numberBits), ID);
	}

	class CircleInt {
		private int value;
	
		public CircleInt(int value) {
			//System.out.println("received value "+value+ " " + (1 << numberBits) + " " + (value % (1024)));
			this.value = value % (1 << numberBits);
			//System.out.println("converted value "+this.value);
		}
	
		public int toInt() {
			return value;
		}
	
		// Returns true if a <= value <= b in the circle
		// if a == b, returns true
		public boolean isBetween(int a, int b) {
			if (a < b) {
				return a <= value && value <= b;
			} else {
				return a <= value || value <= b;
			}
		}

		// Returns true if a < value <= b in the circle
		public boolean isBetween(CircleInt ca, int b) {
			int a = ca.toInt();
			if (a < b) {
				return a < value && value <= b;
			} else {
				return a < value || value <= b;
			}
		}

		// Returns true if a < value <= b in the circle
		// if a == b, returns true
		public boolean isBetween(int a, CircleInt cb) {
			int b = cb.toInt();
			if (a < b) {
				return a < value && value <= b;
			} else {
				return a < value || value <= b;
			}
		}

		// Returns true if a <= value < b in the circle
		// if a == b, returns true
		/*public boolean isBetween(CircleInt ca, CircleInt cb) {
			int a = ca.toInt();
			int b = cb.toInt();
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

		public int virtualPredecessor() {
			return (ID.toInt() - 1) % (1 << numberBits);
		}
	}
}
