package demo;

import java.io.Serializable;
import java.util.ArrayList;
import akka.actor.ActorRef;

public class MyMessage implements Serializable {
    private static final long serialVersionUID = 1L;
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
    }

    public MyMessage(String string, ActorRef ref) {
        nbOfStrings = 1;
        nbOfRefs = 1;
        this.string = string;
        this.refs = new ArrayList<ActorRef>();
        this.refs.add(ref);
    }

    public MyMessage(String string) {
        nbOfStrings = 1;
        nbOfRefs = 0;
        this.string = string;
        this.refs = null;
    }
}
