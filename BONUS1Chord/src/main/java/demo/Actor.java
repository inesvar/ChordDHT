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
	private ActorRef[] fingerTable;
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
	private Boolean[] fingerChecked = new Boolean[numberBits+1];
	
	// true : isn't communicating at all
	private boolean isDead = false;
	// number of lost messages
	private int numberMessagesLost = 0;

	public Actor(int ID) {

		this.ID = new CircleInt(Chord.hashActorRef(getSelf()));

		fingerTable = new ActorRef[numberBits + 1];
		for (int i = 0; i < numberBits + 1; i++) {
			fingerTable[i] = getSelf();
		}
		
		if (Chord.debugFixFingers | Chord.debugRingRepair | Chord.debugData | Chord.seeAllMessages) {
			System.out.println("Node "+this.ID.toInt()+" created");
		}
	}

	// Static function creating actor
	public static Props createActor(int ID) {
		return Props.create(Actor.class, () -> {
			return new Actor(ID);
		});
	}

	public int getFingerTableID(int index){
		return Chord.hashActorRef(fingerTable[index])%(1<<numberBits);

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
		if (Chord.seeAllMessages)
			System.out.println("Node "+ID.toInt()+" efficiently sending to "+getFingerTableID(i)+" (distance "+distance+" and i "+i+")");
		return i;
	}

	public void printFingerTable(String s, boolean forcePrint) {
		if (forcePrint | Chord.seeAllMessages) {
			int[] fingerTableIDs = new int[numberBits + 1];
			for (int i = 0; i < numberBits + 1; i++) {
				fingerTableIDs[i] = getFingerTableID(i);
			}
			System.out.println("Node "+ID.toInt()+" finger table : "+Arrays.toString(fingerTableIDs)+" "+s);
		}
	}

	// sends some values to newPredecessor and returns the oldPredecessor
	public ActorRef greetNewPredecessor(CircleInt id, ActorRef newPredecessor) {
		// update the finger table
		ActorRef oldPredecessor = fingerTable[numberBits];
		fingerTable[numberBits] = newPredecessor;
		if (isAlone) {
			// if this node was the first then its fingerTable is filled with the second node
			gotIntoTheRing();
			for (int i = 0; i < numberBits; i++) {
				fingerTable[i] = newPredecessor;
			}
		}

		//sending the appropriate values to the new predecessor
		for (int key : keys) {
			if (!isSuccessor(key)) {
				fingerTable[numberBits].tell(new IndirectMessage(Chord.indirectMessages.STORE, key, values.get(key)), ActorRef.noSender());
			}
		}
		return oldPredecessor;
	}

	public void setUpPeriodicalMessages() {
		// set up the periodical messages
		getContext().system().scheduler().schedule(Duration.ofMillis(5000), Duration.ofMillis(1000), getSelf(), new SchedulerMessage(Chord.directMessages.FIXFINGERS), getContext().system().dispatcher(), ActorRef.noSender());
		getContext().system().scheduler().schedule(Duration.ofMillis(4000), Duration.ofMillis(1000), getSelf(), new SchedulerMessage(Chord.directMessages.CHECKALIVE), getContext().system().dispatcher(), ActorRef.noSender());
		getContext().system().scheduler().schedule(Duration.ofMillis(0), Duration.ofMillis(500), getSelf(), new SchedulerMessage(Chord.directMessages.NOTIFYSUCCESSOR), getContext().system().dispatcher(), ActorRef.noSender());
	}

	public void gotIntoTheRing() {
		isAlone = false;
		predecessorAlive = 2;
		successorAlive = 2;
		setUpPeriodicalMessages();
	}

	public void transmitAllValues() {
		for (int key : keys) {
			fingerTable[0].tell(new IndirectMessage(Chord.indirectMessages.STORE, key, values.get(key)), ActorRef.noSender());
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
		if (message instanceof IndirectMessage) {
		//============================
		//    TRANSITTING MESSAGES
		//============================
			IndirectMessage m = (IndirectMessage) message;
			if (isDead) {
				numberMessagesLost++;
				if (Chord.debugRingRepair | Chord.debugData | Chord.debugFixFingers | Chord.seeAllMessages)
					System.out.println("["+ID.toInt()+"] is dead, a message "+m.messageType+" was lost !! "+numberMessagesLost+" lost already");
				return;
			}
			CircleInt destID = new CircleInt(m.ID);
			if (isSuccessor(destID)) { 
				// if you're the successor
				switch (m.messageType) {
				//============================
				//    STORING A NEW VALUE
				//============================
					case STORE :
						// store the key and value
						values.put(m.ID, m.value);
						keys.add(m.ID);
						if (Chord.debugData | Chord.seeAllMessages)
							System.out.println("["+ID.toInt()+"] stored a new value "+m.value+" for key "+m.ID);
						break;
				//============================
				//      ERASING A VALUE
				//============================
					case DUMP :
						// dump the key and value
						values.remove(m.ID);
						keys.removeIf(n -> (n == m.ID));
						if (Chord.debugData| Chord.seeAllMessages)
							System.out.println("["+ID.toInt()+"] erased value for key "+m.ID);
						break;
				//============================
				//      GETTING A VALUE
				//============================
					case GET :
						// return the value
						getSender().tell(new DirectMessage(Chord.directMessages.GOT, m.ID, values.get(m.ID)), ActorRef.noSender());
						break;
				//============================
				//     ADDING A NEW NODE
				//============================
					case ADD :	
						// tell the new node your fingerTable and your ID
						m.actorRef.tell(new DirectMessage(Chord.directMessages.WELCOME, fingerTable[numberBits]), getSelf());
						// update your finger table + send values to the new node
						ActorRef oldPredecessor = greetNewPredecessor(destID, m.actorRef);
						// tell the old predecessor that it has a new successor
						if (predecessorAlive > 0)
							oldPredecessor.tell(new DirectMessage(Chord.directMessages.STABILIZE, fingerTable[numberBits]), ActorRef.noSender());
						printFingerTable("["+ID.toInt()+"] added a new node "+destID.toInt(), Chord.debugRingRepair | Chord.debugData | Chord.debugFixFingers);
						break;
			//============================
			//     REMOVING A NODE
			//============================
					case REMOVE :
						// tell your successor your predecessor
						if (predecessorAlive > 0) {
							fingerTable[0].tell(new DirectMessage(Chord.directMessages.PREDECESSORDIE, fingerTable[numberBits]), ActorRef.noSender());
						}
						// tell your predecessor your successor
						if (predecessorAlive > 0) {
							fingerTable[numberBits].tell(new DirectMessage(Chord.directMessages.SUCCESSORDIE, fingerTable[0]), ActorRef.noSender());
						}
						// send your values
						transmitAllValues();
						// stop responding
						kill();
						printFingerTable("["+ID.toInt()+"] was removed", Chord.debugRingRepair | Chord.debugData | Chord.debugFixFingers);
						break;
				//============================
				// LOOKING FOR A FINGER NODE
				//============================
					case LOOKUP :
						// return the node
						getSender().tell(new DirectMessage(Chord.directMessages.FOUND, ID.toInt(), m.fingerNumber), getSelf());
				}
			} else {
				// send the message to the right node
				fingerTable[appropriateFinger(destID)].tell(m, getSender());
			} 
		} else if (message instanceof DirectMessage) {
		//============================
		//    	DIRECT MESSAGES
		//============================
			DirectMessage m = (DirectMessage) message;
			if (isDead) {
				if (!(message instanceof SchedulerMessage)) {
					numberMessagesLost++;
					if (Chord.debugRingRepair | Chord.debugData | Chord.debugFixFingers| Chord.seeAllMessages)
						System.out.println("["+ID.toInt()+"] is dead, a message "+m.messageType+" was lost !! "+numberMessagesLost+" lost already");			
				}
				return;
			}
			switch (m.messageType) {
				//============================
				//      GETTING A VALUE
				//============================
				case GOT :
					if (Chord.debugData| Chord.seeAllMessages)
						System.out.println("["+ID.toInt()+"] got value "+m.value+" for key "+m.ID);
					break;
				//============================
				//  PREPARING A NODE REMOVAL
				//============================
				case PREDECESSORDIE :
					int firstIndex = numberBits-1;
					while (fingerTable[firstIndex] == fingerTable[numberBits]) {
						firstIndex--;
					}
					for (int j = numberBits; j > firstIndex; j--) {
						fingerTable[j] = m.actorRef;
					}
					printFingerTable("["+ID.toInt()+"]'s predecessor disconnected", Chord.debugRingRepair | Chord.debugData | Chord.debugFixFingers);
					break;

				case SUCCESSORDIE :
					int lastIndex = 1;
					while (fingerTable[lastIndex] == fingerTable[0]) {
						lastIndex++;
					}
					for (int j = 0; j < lastIndex; j++) {
						fingerTable[j] = m.actorRef;
					}
					printFingerTable("["+ID.toInt()+"]'s successor disconnected", Chord.debugRingRepair | Chord.debugData | Chord.debugFixFingers);
					break;

				//============================
				//  SETTING UP RING DUTIES
				//============================
				case WELCOME:
					gotIntoTheRing(); // periodical messages, and isAlive bits
					for (int i = 0; i < numberBits; i++) {
						fingerTable[i] = getSender();
					}
					fingerTable[numberBits] = m.actorRef;
					printFingerTable("welcome", Chord.debugRingRepair | Chord.debugFixFingers);
					break;

				//============================
				//  FIXING THE FINGER TABLE
				//============================
				case FIXFINGERS :
					if (!recentlyFixedFingers) {
						if (isFixingFingers) {
							for (int i = numberBits-1; i > -1; i--) {
								if (!fingerChecked[i]) {
									fingerTable[i] = fingerTable[0];
									if (Chord.debugFixFingers| Chord.seeAllMessages)
										System.out.println("["+ID.toInt()+"] lost finger "+i+" probably due to some node fail");
							
								}
							}
							isFixingFingers = false;
							recentlyFixedFingers = true;
							problematicFingerTable = false;
							printFingerTable(" fixed finger table", Chord.debugFixFingers);
							return;
						}
						isFixingFingers = true;
						numberFingersChecked = 0;
						for (int i = 0; i < numberBits; i++) {
							fingerChecked[i] = false;
						}
						for (int i = 0; i < numberBits; i++) {
							int fingerID = ID.toInt() + (1<<i);
							fingerTable[0].tell(new IndirectMessage(Chord.indirectMessages.LOOKUP, fingerID, i), getSelf());
						}
						if (Chord.debugFixFingers| Chord.seeAllMessages)
							System.out.println("["+ID.toInt()+"] has started fixing fingers");
					} else if (recentlyFixedFingers) {
						recentlyFixedFingers = false;
					}
					break;

				//============================
				// ACKNOLEDGING A FINGER-FIX
				//============================
				case FOUND :
					synchronized(this) {
						fingerTable[m.fingerNumber] = getSender();
						numberFingersChecked++;
						fingerChecked[m.fingerNumber] = true;
						if (Chord.debugFixFingers| Chord.seeAllMessages)
							System.out.println("["+ID.toInt()+"] finger "+m.fingerNumber+" is "+m.ID+" ("+numberFingersChecked+")");
						if (numberFingersChecked == numberBits) {
							isFixingFingers = false;
							recentlyFixedFingers = true;
							problematicFingerTable = false;
							printFingerTable(" fixed finger table", Chord.debugFixFingers);
						}
					}
					break;

				//============================
				// NOTIFYIED BY A PREDECESSOR
				//============================

				case NOTIFY:
					CircleInt sender = new CircleInt(Chord.hashActorRef(getSender()));
					getSender().tell(new DirectMessage(Chord.directMessages.STABILIZE, fingerTable[numberBits]), ActorRef.noSender());
					
					// message from predecessor
					if (fingerTable[numberBits] == getSender()) {
						predecessorAlive = 2;
					
					} else if (fingerTable[numberBits] == null) {
						fingerTable[numberBits] = getSender();
						predecessorAlive = 2;
					
					/*} else if (sender.isBetween(getFingerTableID(numberBits), ID)) {
						// make sure the old predecessor is updated : THIS NEVER HAPPENS
						System.out.println("so it happens");
						fingerTable[numberBits).tell(new MyMessage("stabilize", getSender()), getSelf());
						fingerTable[numberBits, getSender()); 
						predecessorAlive = 2;
					*/} else {
						if (Chord.debugRingRepair| Chord.seeAllMessages)
							System.out.println("not supposed to happen ["+ID.toInt()+"] notifyied by "+sender.toInt());
					
					}
					printFingerTable("was notifyied by "+sender.toInt()+", predecessor is alive : "+predecessorAlive+"/2", Chord.debugRingRepair);
					break;
					
				case STABILIZE:
					if (m.actorRef == null)
						return;
					CircleInt newSuccessor = new CircleInt(Chord.hashActorRef(m.actorRef));
					successorAlive = 2;
					if (getSelf() == m.actorRef) {
						printFingerTable("successor is alive : "+successorAlive+"/2", Chord.debugRingRepair);
						return;
					}
					if (newSuccessor.isBetween(ID, getFingerTableID(0))) {
						// a node has been added it's your successor
						// update your finger table
						int index = appropriateFinger(newSuccessor);
						//fingerTable[1, fingerTable[0));
						for (int i = 0; i <= index; i++) {
							fingerTable[i] = m.actorRef;
						}
						for (int i = index + 1; i < numberBits; i++) {
							if (newSuccessor.isBetween(ID, getFingerTableID(i))) {
								fingerTable[i] = m.actorRef;
							} else {
								break;
							}
						}
					} else {
						if (Chord.debugRingRepair | Chord.seeAllMessages)
							System.out.println("anomaly occurred, ["+ID.toInt()+"] stabilized by "+newSuccessor.toInt());
						
					}
					printFingerTable("was notifyied, successor is "+getFingerTableID(0)+" was sent : "+Chord.hashActorRef(m.actorRef), Chord.debugRingRepair);
					break;

				case NOTIFYSUCCESSOR:
					fingerTable[0].tell(new DirectMessage(Chord.directMessages.NOTIFY), getSelf());
					break;
			
				case CHECKALIVE:
					predecessorAlive -= 1;
					successorAlive -= 1;
					if (predecessorAlive == 0) {
						// predecessor is dead
						System.out.println("["+ID.toInt()+"] predecessor is dead");
						firstIndex = numberBits-1;
						while (fingerTable[firstIndex] == fingerTable[numberBits]) {
							firstIndex--;
						}
						if (firstIndex == -1) {
							if (Chord.debugRingRepair | Chord.seeAllMessages)
								System.out.println("["+ID.toInt()+"] is now alone");
							isAlone = true;
							for (int i = 0; i < numberBits; i++) {
								fingerTable[i] = getSelf();
							}
						} else {
							for (int j = firstIndex + 1; j < numberBits; j++) {
								fingerTable[j] = fingerTable[firstIndex];
							}
							fingerTable[numberBits] = null;
						}
					}
					if (successorAlive == 0) {
						// successor is dead
						System.out.println("["+ID.toInt()+"] successor is dead");
						int i = 1;
						while (fingerTable[i] == fingerTable[0]) {
							i++;
						}
						for (int j = 0; j < i; j++) {
							fingerTable[j] = fingerTable[i];
						}
					}
					printFingerTable("checkAlive successor"+successorAlive+"/2 predecessor"+predecessorAlive+"/2", Chord.debugRingRepair);
					break;


/////////////////////////////////////////////////////////////////////
//																   //
//						DEBUGGING MESSAGES						   //
//															       //													
/////////////////////////////////////////////////////////////////////


				/*case ALLOWPRINT:
					allowPrint(m.ID == 1);
					break;*/

				case PRINTVALUES:
					for (int key : keys) {
						System.out.println("["+ID.toInt()+"] : "+key+" "+values.get(key));
					}
					break;

				case PRINTFINGERTABLE:
					if (!problematicFingerTable) {
						printFingerTable("was fixed recently : "+(recentlyFixedFingers&(!isFixingFingers)), true);
					}
					break;
				
				case BADFINGERTABLE:
					if (problematicFingerTable) {
						printFingerTable("wasn't fixed yet", true);
					}
					break;

				case WHODIDNTJOIN :
					if (isAlone) {
						printFingerTable("hasn't joined the ring", true);
					}
					break;

				case KILL :
					kill();
					break;
			}
		}
	}
}
