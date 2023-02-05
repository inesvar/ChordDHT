package demo;

import java.io.Serializable;
import java.util.ArrayList;
import akka.actor.ActorRef;

public class MyMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    public final int ID;
    public final int number;
    public final String string;
    public final String string2;
    public final String value;
    public final ArrayList<ActorRef> fingerTable;
    public final ActorRef actorRef;

    // fix_fingers
    public MyMessage(String string) {
        this.string = string;
        this.string2 = null;
        this.ID = 0;
        this.value = null;
        this.fingerTable = null;
        this.actorRef = null;
        this.number=0;
    }

    // dump
    public MyMessage(String string, int ID) {
        this.string = string;
        this.string2 = null;
        this.ID = ID;
        this.value = null;
        this.fingerTable = null;
        this.actorRef = null;
        this.number=0;
    }

    // found, lookup
    public MyMessage(String string, int number, int ID) {
        this.string = string;
        this.string2 = null;
        this.ID = ID;
        this.value = null;
        this.fingerTable = null;
        this.actorRef = null;
        this.number=number;
    }

    // welcome
    public MyMessage(String string, ArrayList<ActorRef> fingerTable) {
        this.string = string;
        this.string2 = null;
        this.ID = 0;
        this.value = null;
        this.fingerTable = new ArrayList<>();
        for (int i = 0; i < fingerTable.size(); i++) {
            this.fingerTable.add(fingerTable.get(i));
        };
        this.actorRef = null;
        this.number=0;
    }

    // signal
    public MyMessage(String string, String string2, ActorRef actorRef) {
        this.string = string;
        this.string2 = string2;
        this.ID = 0;
        this.value = null;
        this.fingerTable = null;
        this.actorRef = actorRef;
        this.number=0;
    }

    // add, remove
    public MyMessage(String string, int ID, ActorRef actorRef) {
        this.string = string;
        this.string2 = null;
        this.ID = ID;
        this.value = null;
        this.fingerTable = null;
        this.actorRef = actorRef;
        this.number=0;
    }

    // store
    public MyMessage(String string, int ID, String value) {
        this.string = string;
        this.string2 = null;
        this.ID = ID;
        this.value = value;
        this.fingerTable = null;
        this.actorRef = null;
        this.number=0;
    }
}
