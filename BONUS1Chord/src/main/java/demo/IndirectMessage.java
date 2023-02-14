package demo;

import java.io.Serializable;
import akka.actor.ActorRef;

public class IndirectMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public final Chord.indirectMessages messageType;
    public final int ID;
    public final ActorRef actorRef;
    public final int fingerNumber;
    public final String value;


    // dump, get, remove
    public IndirectMessage(Chord.indirectMessages messageType, int ID) {
        this.messageType = messageType;
        this.ID = ID;
        this.value = null;
        this.actorRef = null;
        this.fingerNumber=0;
    }

    // lookUp
    public IndirectMessage(Chord.indirectMessages messageType, int ID, int fingerNumber) {
        this.messageType = messageType;
        this.ID = ID;
        this.value = null;
        this.actorRef = null;
        this.fingerNumber=fingerNumber;
    }

    // add
    public IndirectMessage(Chord.indirectMessages messageType, int ID, ActorRef actorRef) {
        this.messageType = messageType;
        this.ID = ID;
        this.value = null;
        this.actorRef = actorRef;
        this.fingerNumber=0;
    }

    // store
    public IndirectMessage(Chord.indirectMessages messageType, int ID, String value) {
        this.messageType = messageType;
        this.ID = ID;
        this.value = value;
        this.actorRef = null;
        this.fingerNumber=0;
    }
}
