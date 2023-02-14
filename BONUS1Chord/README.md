
# Chord algorithm


## Using **Consistent Hashing**

The hashing algorithm used is **SHA-1** truncated to numberBits.

Nodes' IDs are hashed ActorRefs. 
Keys' IDs are the hashed keys.

We consider that the value succeeding to 2\**numberBits - 1 is 0, so that **the IDs form a ring**.
The maths for comparision, addition and subtraction are implemented in the `Actor` subclass `CircleInt`.

**Remark : although each node can compute the IDs from an actorRef or a key, IDs are pre-computed in main** so the messages transiting in the network don't have to be changed. 


# Types of messages

There are two types of messages :
- *direct messages* sent to a known destination (debugging messages but also answers to requests)
- *indirect messages* with an unknown destination (a message storing a value for instance)

Since nodes can fail and consequently messages can be lost, **it would be necessary to send essential messages** (new data to be stored in the hash table, or some important control messages like `welcome`) **in a way that it is stored to be eventually send again**. It isn't implemented in the code, but it causes data loss.

## Data messages

These messages are used to **move values in the network**.
The 3 first messages are transmitted until they reach successor(ID).

### adding a new value : `store(ID, value)`

### erasing a value : `dump(ID)`

### requesting a value : `get(ID)`

Answered by successor(ID) using *got*

### transmitting a value : `got(ID, value)`

Sent back directly to the node making the request using getSender()

## Forming-the-Ring messages

These messages ensure that a **ring is formed in case no nodes fail**.

### adding a new node : `add(newID, newActorRef)`

Transmitted until it reaches successor(newID), then :
- the new node gets an approximative fingerTable (message *welcome*) composed with only two nodes (its predecessor and successor) : this way, the messages sent before the finger tables are fixed will not miss their destinator
- some values are transferred (messages *store*)
- successor(ID) updates its fingerTable
- the previous predecessor of successor(ID) is informed (message *stabilize*)

### removing a node : `remove(oldID)`

Transmitted until it reaches oldID (= successor(oldID), then :
- the current succesor and predecessor are informed (messages *successorDie*, *predecessorDie*)
- all oldID's values are transferred (messages *store*)

### signaling a disconnection : `successorDie(actorRef), predecessorDie(actorRef)`

Sent to the predecessor/successor when :
- one dies and informs its predecessor and successor

### welcoming a new node into the network : `welcome(actorRef)`

Sent directly to a new node by its successor.

## Fixing-the-Ring messages

These messages ensure that the **ring stays even if case of fails**.

They're sent **periodically** thanks to a scheduler sending **internal messages**.

### notifying your successor : `notify` (sent thanks to internal message `notifySuccessor`)

Sent to the successor. (It's not sent directly from the scheduler because the scheduler determines the destinator after sending the previous message and not immediately before sending)

The successor check periodically that it has received a `notify` (thanks to `checkAlive` internal message), otherwise marks the predecessor as dead.
    
### searching a finger node : `lookUp(ID, fingerNumber)` (sent thanks to internal message `fixFingers`)

Transmitted until it reaches successor(ID), then successor(ID) sends back a `found` message with its ID to the sender.
In the case where the message is sent to a dead node, it will be lost. Thus, it's necessary to retry when there was no answer to fix the finger table as quickly as possible.

### acknowledging a finger node : `found(fingerID, fingerNumber)`

Updating your finger table.

### telling someone your predecessor : `stabilize(actorRef)`

Generally directly sent to a node that sent you `notify` :
- if this node is your predecessor, this node will know that you (their successor) is alive
- if this node is not your predecessor, this node will be informed of your predecessor which is (one of) their successor(s)

It can also be sent :
- to your previous predecessor to inform them of your new predecessor

## Debugging messages

### toggle printing : `allowPrint(number)`

### printing the values : `printValues`

### printing the correct finger tables : `printFingerTable`

### printing only the new incorrect finger tables : `badFingerTable`

### printing the isolated nodes : `whoDidntJoin`

### killing a node : `kill`












