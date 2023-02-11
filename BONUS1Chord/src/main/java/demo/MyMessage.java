package demo;

import java.io.Serializable;
import java.util.ArrayList;
import akka.actor.ActorRef;

public class MyMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public final Chord.messages messageType;
    public final int ID;
    public final int fingerNumber;
    public final String value;
    public final ArrayList<ActorRef> fingerTable;
    public final ActorRef actorRef;

    // fix_fingers, debugging messages
    public MyMessage(Chord.messages messageType) {
        this.messageType = messageType;
        this.ID = 0;
        this.value = null;
        this.fingerTable = null;
        this.actorRef = null;
        this.fingerNumber=0;
    }

    // dump
    public MyMessage(Chord.messages messageType, int ID) {
        this.messageType = messageType;
        this.ID = ID;
        this.value = null;
        this.fingerTable = null;
        this.actorRef = null;
        this.fingerNumber=0;
    }

    // found, lookup
    public MyMessage(Chord.messages messageType, int fingerNumber, int ID) {
        this.messageType = messageType;
        this.ID = ID;
        this.value = null;
        this.fingerTable = null;
        this.actorRef = null;
        this.fingerNumber=fingerNumber;
    }

    // welcome
    public MyMessage(Chord.messages messageType, ArrayList<ActorRef> fingerTable) {
        this.messageType = messageType;
        this.ID = 0;
        this.value = null;
        this.fingerTable = new ArrayList<>();
        for (int i = 0; i < fingerTable.size(); i++) {
            this.fingerTable.add(fingerTable.get(i));
        };
        this.actorRef = null;
        this.fingerNumber=0;
    }

    // signal
    public MyMessage(Chord.messages messageType, ActorRef actorRef) {
        this.messageType = messageType;
        this.ID = 0;
        this.value = null;
        this.fingerTable = null;
        this.actorRef = actorRef;
        this.fingerNumber=0;
    }

    // add, remove
    public MyMessage(Chord.messages messageType, int ID, ActorRef actorRef) {
        this.messageType = messageType;
        this.ID = ID;
        this.value = null;
        this.fingerTable = null;
        this.actorRef = actorRef;
        this.fingerNumber=0;
    }

    // store
    public MyMessage(Chord.messages messageType, int ID, String value) {
        this.messageType = messageType;
        this.ID = ID;
        this.value = value;
        this.fingerTable = null;
        this.actorRef = null;
        this.fingerNumber=0;
    }
}
