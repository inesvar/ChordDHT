package demo;

import java.io.Serializable;
import akka.actor.ActorRef;

public class DirectMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public final Chord.directMessages messageType;
    public final int ID;
    public final ActorRef actorRef;
    public final int fingerNumber;
    public final String value;

    
    // notify, debugging messages
    public DirectMessage(Chord.directMessages messageType) {
        this.messageType = messageType;
        this.ID = 0;
        this.value = null;
        this.actorRef = null;
        this.fingerNumber=0;
    }

    // allowPrint
    public DirectMessage(Chord.directMessages messageType, int ID) {
        this.messageType = messageType;
        this.ID = ID;
        this.value = null;
        this.actorRef = null;
        this.fingerNumber=0;
    }

    // found
    public DirectMessage(Chord.directMessages messageType, int ID, int fingerNumber) {
        this.messageType = messageType;
        this.ID = ID;
        this.value = null;
        this.actorRef = null;
        this.fingerNumber=fingerNumber;
    }

    // successorDie, predecessorDie, welcome, stabilize
    public DirectMessage(Chord.directMessages messageType, ActorRef actorRef) {
        this.messageType = messageType;
        this.ID = 0;
        this.value = null;
        this.actorRef = actorRef;
        this.fingerNumber=0;
    }

    // got
    public DirectMessage(Chord.directMessages messageType, int ID, String value) {
        this.messageType = messageType;
        this.ID = ID;
        this.value = value;
        this.actorRef = null;
        this.fingerNumber=0;
    }
}
