
# Chord algorithm

## Using consistent hashing

Nodes and keys are represented by IDs on numberBits-bit. 

Nodes' IDs are hashed ActorRefs. 
Keys' IDs are the hashed keys.

The hashing algorithm used is SHA-1 truncated to numberBits.

We consider that the value succeeding to 2**numberBits - 1 is 0, so that the IDs form a ring.
The maths for comparision and subtraction are implemented in the subclass from Actor named CircleInt.

(key, value) is placed in the node that has the smaller ID that is yet superior or equal to key.

=> This minimizes the quantity of keys to move when a node is added / removed / fails.

Remark : each node can compute the IDs from an actorRef or a key.
I pre-compute the IDs in main but it isn't necessary.


## Types of messages : functionnal messages

The first four messages are sent from main to a random node (or the first node).
The fifth message is sent from a specific node to all its previous neighbours.

### adding a new key : store(ID, value)

The nodes transmit the message until it reaches successor(key), then (key, value) is stored there.

### removing a key : dump(ID)

The nodes transmit the message until it reaches successor(key), then (key, value) is removed from the node.

## fetching data : get(ID)


### adding a new node : add(newActorRef, ID)

The nodes transmit the message until it reaches successor(key), then the (key', value') where key' < key are moved to the new node.
The neighbours' list of the successor node is modified accordingly and its new neighbours' list is sent to all the ancient neighbours and to the new node.

### removing a node : remove(oldActorRef, ID)

The nodes transmit the message until it reaches successor(key), then the (key', value') of the node being removed are stored in the successor node.
The neighbours' list of the succesor node is modified accordingly and its new neighbours' list is sent to all the ancient neighbours and to the new node.

### signaling a neighbours' change : signal(neighbours' list) (replaces Stabilize)

A node send its new neighbours' list (successor and predecessor) to its predecessor(s) and succesor(s) (both the current and previous ones).
=> make sure the predecessor and successor fields are exact, but the finger table isn't corrected
=> we solve this problem using stabilization


## Types of messages : stabilizing messages (nessecary in case of node fail)
These messages should transit in the whole ring to fix failures.

### Notify and check predecessor repair the ring if broken
n asks its successor for its predecessor p and decides whether p should be n's successor instead (this is the case if p recently joined the system).
node compares the value of itself to predecessor(successor(node)) and eventually updates its own successor.
    
### Fix_fingers : look_up(ID) (transits to ID's successor) => found(ID's successor) (goes back to n)
updates finger tables :
each node n looks for the successor(node + 2**(i-1))




