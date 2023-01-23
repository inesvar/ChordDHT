package demo;

import java.io.Serializable;
import java.util.ArrayList;
import akka.actor.ActorRef;

public class MyMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    public final int sequenceNumber;
    public final int length;
    public final int nbOfRefs;
    public final int nbOfStrings;
    public final String string;
    public final ArrayList<ActorRef> refs;

    public MyMessage(ArrayList<ActorRef> refs) {
        nbOfStrings = 0;
        nbOfRefs = refs.size();
        this.string = null;
        this.refs = new ArrayList<>();
        for (int i = 0; i < nbOfRefs; i++) {
            this.refs.add(refs.get(i));
        }
        this.sequenceNumber = -1;
        this.length = -1;
    }

    public MyMessage(int sequenceNumber, int length, String string) {
        nbOfStrings = 1;
        nbOfRefs = 0;
        this.string = string;
        this.refs = null;
        this.sequenceNumber = sequenceNumber;
        this.length = length;
    }

    public MyMessage(String string) {
        nbOfStrings = 1;
        nbOfRefs = 0;
        this.string = string;
        this.refs = null;
        this.sequenceNumber = -1;
        this.length = -1;
    }
}
