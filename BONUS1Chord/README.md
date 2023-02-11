
# Chord algorithm




## Using **Consistent Hashing**

The hashing algorithm used is **SHA-1** truncated to numberBits.

Nodes' IDs are hashed ActorRefs. 
Keys' IDs are the hashed keys.

We consider that the value succeeding to 2\**numberBits - 1 is 0, so that **the IDs form a ring**.
The maths for comparision, addition and subtraction are implemented in the `Actor` subclass `CircleInt`.

**Remark : although each node can compute the IDs from an actorRef or a key, IDs are pre-computed in main** so the messages transiting in the network don't have to be changed.


## Updating-the-Ring messages

These messages ensure that a **ring is formed in case no nodes fail**.

### adding a new node : `add(newActorRef, newID)`

Transmitted until it reaches successor(newID), then :
- the new node gets an approximative fingerTable (message *welcome*)
- some values are transferred (messages *store*)
- successor(ID) updates its fingerTable
- the previous predecessor of successor(ID) is informed (message *signal*)

### adding a new node : `remove(oldActorRef, oldID)`

Transmitted until it reaches oldID, then :
- the current succesor and predecessor are informed (message *signal*)
- all oldID's values are transferred (messages *store*)

### signaling a neighbours' change : `signal(code, ActorRef)`

Sent to the predecessor/successor when :
- one signals its new predecessor to its old predecessor (code `fromPreviousSuccessor`)
- one dies and informs its predecessor (code `fromDyingSuccessor`) and successor (code `fromDyingPredecessor`)

## Fixing-the-Ring messages

These messages ensure that the **ring stays even if case of fails** and that **the finger tables are updated**.

They're sent periodically thanks to a scheduler.

### notifying its successor : `notify(code)`

Sent to the predecessor (code 0) and successor (code 1).

The successor check periodically that it has received a `notify`, otherwise marks the predecessor as dead.
    
### fixing the finger tables 1 : `look_up(ID, fingerNumber)`

Transmitted until it reaches successor(ID), then successor(ID) sends back a `found` message with its ID to the sender.

### fixing the finger tables 2 : `found(fingerID, fingerNumber)`

### stabilize_req()

Sent to the successor (no need to notify its successor) to check that it's the closest successor. The successor has to answer by `stabilize_done` (no need to notify its predecessor).

## Data messages

Theses messages are transmitted until they reach successor(ID).

### adding a new value : `store(ID, value)`

### erasing a value : `dump(ID)`

### fetching data : `get(ID)`

successor(ID) sends the value back to the sender using getSender()

## Debugging messages

Theses messages are transmitted to every node.

### toggle printing : `print(boolean)`

### erasing a value : `dump(ID)`

### fetching data : `get(ID)`

successor(ID) sends the value back to the sender using getSender()








