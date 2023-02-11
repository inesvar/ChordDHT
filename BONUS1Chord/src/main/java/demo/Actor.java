package demo;

import java.util.Arrays;
import java.util.Hashtable;
import java.time.Duration;
import java.util.ArrayList;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;


public class Actor extends UntypedAbstractActor {

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
	private int predecessorAlive = 0;
	private int successorAlive = 0;

	// 1 : doesn't know anyone
	private boolean isAlone = true;
	// the finger table hasn't been fixed yet
	private Boolean problematicFingerTable = true;

	private boolean isFixingFingers = false;
	private boolean recentlyFixedFingers = false;
	private int numberFingersChecked = 0;
	
	// true : isn't communicating at all
	private boolean isDead = false;
	private boolean allowedToPrint = false;

	public Actor(int ID) {

		this.ID = new CircleInt(Chord.hashActorRef(getSelf()));

		this.fingerTable = new ArrayList<>();
		for (int i = 0; i < numberBits + 1; i++) {
			this.fingerTable.add(getSelf());
		}
		
		if (Chord.debugFixFingers | Chord.debugRingRepair | Chord.debugData | allowedToPrint) {
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
		return Chord.hashActorRef(fingerTable.get(index))%(1<<numberBits);

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
		if (allowedToPrint | forcePrint | Chord.debugFixFingers | Chord.debugRingRepair | Chord.debugData) {
			System.out.println("Node "+ID.toInt()+" finger table : "+Arrays.toString(fingerTableIDs)+" "+s);
		}
	}

	// sends some values to newPredecessor and returns the oldPredecessor
	public ActorRef greetNewPredecessor(CircleInt id, ActorRef newPredecessor) {
		// update the finger table
		ActorRef oldPredecessor = fingerTable.get(numberBits);
		fingerTable.set(numberBits, newPredecessor);
		if (isAlone) {
			// if this node was the first then its fingerTable is filled with the second node
			gotIntoTheRing();
			for (int i = 0; i < numberBits; i++) {
				fingerTable.set(i, newPredecessor);
			}
			setUpPeriodicalMessages();
		}

		//sending the appropriate values to the new predecessor
		for (int key : keys) {
			if (!isSuccessor(key)) {
				fingerTable.get(numberBits).tell(new MyMessage(Chord.messages.STORE, key, values.get(key)), ActorRef.noSender());
			}
		}
		return oldPredecessor;
	}

	public void setUpPeriodicalMessages() {
		// set up the periodical messages
		getContext().system().scheduler().schedule(Duration.ofMillis(5000), Duration.ofMillis(5000), getSelf(), new MyMessage(Chord.messages.FIXFINGERS), getContext().system().dispatcher(), ActorRef.noSender());
		getContext().system().scheduler().schedule(Duration.ofMillis(4000), Duration.ofMillis(2000), getSelf(), new MyMessage(Chord.messages.CHECKALIVE), getContext().system().dispatcher(), ActorRef.noSender());
		getContext().system().scheduler().schedule(Duration.ofMillis(0), Duration.ofMillis(1000), getSelf(), new MyMessage(Chord.messages.NOTIFYNEIGHBOURS), getContext().system().dispatcher(), ActorRef.noSender());
	}

	public void gotIntoTheRing() {
		isAlone = false;
		predecessorAlive = 2;
		successorAlive = 2;
	}

	public void transmitAllValues() {
		for (int key : keys) {
			fingerTable.get(0).tell(new MyMessage(Chord.messages.STORE, key, values.get(key)), ActorRef.noSender());
		}
	}

	public void kill() {
		isDead = true;
	}

/////////////////////////////////////////////////////////////////////
//																   //
//				   		MATHS ON THE CIRCLE			    		   //
//																   //
/////////////////////////////////////////////////////////////////////


	// Returns true if this is the successor of other
	public boolean isSuccessor(CircleInt other) {
		if (predecessorAlive < 0) {
			return other.isBetween(virtualPredecessor(), ID);
		}
		return other.isBetween(getFingerTableID(numberBits), ID);
	}

	// Returns true if this is the successor of other
	public boolean isSuccessor(int o) {
		CircleInt other = new CircleInt(o);
		if (predecessorAlive < 0) {
			return other.isBetween(virtualPredecessor(), ID);
		}
		return other.isBetween(getFingerTableID(numberBits), ID);
	}

	public int virtualPredecessor() {
		return (ID.toInt() - 1) % (1 << numberBits);
	}


	@Override
	public void onReceive(Object message) throws Throwable {
		if (message instanceof MyMessage && !isDead) {
			MyMessage m = (MyMessage) message;


/////////////////////////////////////////////////////////////////////
//																   //
//					BASIC RING UPDATES MESSAGES		    		   //
//																   //
/////////////////////////////////////////////////////////////////////



			//============================
			//     ADDING A NEW NODE
			//============================

			switch (m.messageType) {

				case ADD :
					synchronized(this) {
						CircleInt destID = new CircleInt(m.ID);
						if (isSuccessor(destID)) { 
							// tell the new node your fingerTable and your ID
							m.actorRef.tell(new MyMessage(Chord.messages.WELCOME, fingerTable), getSelf());
							// update your finger table + send values to the new node
							ActorRef oldPredecessor = greetNewPredecessor(destID, m.actorRef);
							// tell the old predecessor that it has a new successor
							if (predecessorAlive > 0)
								oldPredecessor.tell(new MyMessage(Chord.messages.STABILIZE, fingerTable.get(numberBits)), ActorRef.noSender());
							if (allowedToPrint | Chord.debugRingRepair | Chord.debugData | Chord.debugFixFingers) {
								System.out.println("["+ID.toInt()+"] added a new node "+destID.toInt());
							}
							printFingerTable("add", false);
						} else {
							// send the message to the right node
							fingerTable.get(appropriateFinger(destID)).tell(m, ActorRef.noSender());
						} 
					}
					break;
			




			//============================
			//     REMOVING A NODE
			//============================

				case REMOVE :
					synchronized(this) {
						CircleInt destID = new CircleInt(m.ID);
						if (isSuccessor(destID)) { // if this node is to be removed (successor(nodeID) = nodeID)
							// tell your successor your predecessor
							if (predecessorAlive > 0) {
								fingerTable.get(0).tell(new MyMessage(Chord.messages.SIGNAL, "fromDyingPredecessor", fingerTable.get(numberBits)), ActorRef.noSender());
							}
							// tell your predecessor your successor
							if (predecessorAlive > 0) {
								fingerTable.get(numberBits).tell(new MyMessage(Chord.messages.SIGNAL, "fromDyingSuccessor", fingerTable.get(0)), ActorRef.noSender());
							}
							// send your values
							transmitAllValues();
							// stop responding
							kill();
							printFingerTable("remove", false);
						} else {
							// send the message to the right node
							fingerTable.get(appropriateFinger(destID)).tell(m, ActorRef.noSender());
						}
					}
					break;
				





				//============================
				//  PREPARING A NODE REMOVAL
				//============================
					
				case SIGNAL :
					synchronized(this) {
						if (m.string2.equals("fromDyingSuccessor")) { 
							// your successor will be removed
							// replace all his occurences in the finger table
							int i = 1;
							while (fingerTable.get(i) == m.actorRef) {
								i++;
							}
							for (int j = 0; j < i; j++) {
								fingerTable.set(j, m.actorRef);
							}
							printFingerTable("signal fromDyingSuccessor", false);

						} else if (m.string2.equals("fromDyingPredecessor")) {
							// your predecessor will be removed
							fingerTable.set(numberBits, m.actorRef);
							printFingerTable("signal fromDyingPredecessor", false);
						}
					}
					break;
			





				//============================
				//  SETTING UP RING DUTIES
				//============================

				case WELCOME:
					synchronized(this) {
						gotIntoTheRing();
						for (int i = 0; i < numberBits; i++) {
							fingerTable.set(i, getSender());
						}
						fingerTable.set(numberBits, m.fingerTable.get(numberBits));
						setUpPeriodicalMessages();
						printFingerTable("welcome", false);
					}






				//============================
				//    STORING A NEW VALUE
				//============================

				case STORE:

					synchronized(this) {
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






				//============================
				//      ERASING A VALUE
				//============================

				case DUMP :
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





				//============================
				//      GETTING A VALUE
				//============================
				
				case GET :
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






				//============================
				// LOOKING FOR A FINGER NODE
				//============================

				case LOOKUP :
					synchronized(this) {
						CircleInt destID = new CircleInt(m.ID);
						if (isSuccessor(destID)) { 
							// if this node is the successor of the key
							// return the node
							getSender().tell(new MyMessage(Chord.messages.FOUND, m.fingerNumber, fingerTable.get(0)), ActorRef.noSender());
						} else {
							// send the message to the right node
							int index = appropriateFinger(destID);
							fingerTable.get(index).tell(m, getSender());
						}
					}




				//============================
				//  FIXING THE FINGER TABLE
				//============================
				
				case FIXFINGERS :

					synchronized(this) {
						if (!isFixingFingers& !recentlyFixedFingers) {
							isFixingFingers = true;
							numberFingersChecked = 0;
							for (int i = 0; i < numberBits; i++) {
								int fingerID = ID.toInt() + (1<<i);
								fingerTable.get(0).tell(new MyMessage(Chord.messages.LOOKUP, i, fingerID), getSelf());
							}
							//printFingerTable("fix_fingers", false);
							recentlyFixedFingers = true;
						} else if (recentlyFixedFingers) {
							recentlyFixedFingers = false;
						}
					}





				//============================
				// ACKNOLEDGING A FINGER-FIX
				//============================

				case FOUND :

					synchronized(this) {
						fingerTable.set(m.fingerNumber, getSender());
						numberFingersChecked++;
						if (allowedToPrint)
							System.out.println("["+ID.toInt()+"] finger "+m.fingerNumber+" is "+Chord.hashActorRef(getSender())+" ("+numberFingersChecked+")");
						if (numberFingersChecked == numberBits) {
							isFixingFingers = false;
							problematicFingerTable = false;
							printFingerTable("fix_fingers", false);
						}
					}





				//============================
				// NOTIFYIED BY A PREDECESSOR
				//============================

				case NOTIFY:

					synchronized(this) {
						CircleInt sender = new CircleInt(Chord.hashActorRef(getSender()));
						getSender().tell(new MyMessage(Chord.messages.STABILIZE, fingerTable.get(numberBits)), ActorRef.noSender());
						
						// message from predecessor
						if (fingerTable.get(numberBits) == getSender()) {
							predecessorAlive = 2;
						
						} else if (fingerTable.get(numberBits) == null) {
							fingerTable.set(numberBits, getSender());
							predecessorAlive = 2;
						
						/*} else if (sender.isBetween(getFingerTableID(numberBits), ID)) {
							// make sure the old predecessor is updated : THIS NEVER HAPPENS
							System.out.println("so it happens");
							fingerTable.get(numberBits).tell(new MyMessage("stabilize", getSender()), getSelf());
							fingerTable.set(numberBits, getSender()); 
							predecessorAlive = 2;
						*/} else {
							if (allowedToPrint)
								System.out.println("not supposed to happen ["+ID.toInt()+"] notifyied by "+sender.toInt());
						
						}
						printFingerTable("was notifyied by "+sender.toInt()+" predecessor is "+predecessorAlive, false);
					}
					
				case STABILIZE:

					synchronized(this) {
						// message from successor
						CircleInt newSuccessor = new CircleInt(Chord.hashActorRef(m.actorRef));
						successorAlive = 2;
						if (getSelf() == m.actorRef) {
							return;
						}
						if (newSuccessor.isBetween(ID, getFingerTableID(0))) {
							// a node has been added it's your successor
							// update your finger table
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
						} else {
							//if (allowedToPrint)
								System.out.println("not supposed to happen ["+ID.toInt()+"] stabilized by "+newSuccessor.toInt());
							
						}
						printFingerTable("was notifyied , successor is "+getFingerTableID(0), false);
					}

				case NOTIFYNEIGHBOURS:

					synchronized(this) {
						fingerTable.get(0).tell(new MyMessage(Chord.messages.NOTIFY), getSelf());
						/*if (fingerTable.get(numberBits) != null) {
							fingerTable.get(numberBits).tell(new MyMessage("notify", 0, ID.toInt()), getSelf());
						}*/
					}
			
				case CHECKALIVE:

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


/////////////////////////////////////////////////////////////////////
//																   //
//						DEBUGGING MESSAGES						   //
//															       //													
/////////////////////////////////////////////////////////////////////


				case ALLOWPRINT:

					synchronized(this) {
						allowPrint(m.ID == 1);
					}

				case PRINTVALUES:

					synchronized(this) {
						for (int key : keys) {
							System.out.println("["+ID.toInt()+"] : "+key+" "+values.get(key));
						}
					}

				case PRINTFINGERTABLE:

					synchronized(this) {
						if (!problematicFingerTable) {
							printFingerTable(null, true);
						}
					}
				
				case BADFINGERTABLE:

					synchronized(this) {
						if (problematicFingerTable) {
							printFingerTable(null, true);
						}
					}

				case WHODIDNTJOIN :

					synchronized(this) {
						if (isAlone) {
							printFingerTable(null, true);
						}
					}

				case KILL :

					synchronized(this) {
						kill();
					}
				}
		}
	}
}
