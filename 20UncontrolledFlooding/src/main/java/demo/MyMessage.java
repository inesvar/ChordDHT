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
    

    /*public MyMessage(String string, ArrayList<ActorRef> refs) {
        nbOfStrings = 1;
        nbOfRefs = refs.size();
        this.string = string;
        this.refs = org.apache.commons.lang3.SerializationUtils.clone(refs);
    }*/

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
        ArrayList<ActorRef> refs = new ArrayList<ActorRef>();
        refs.add(ref);
        this.refs = refs;
    }

    public MyMessage(String string) {
        nbOfStrings = 1;
        nbOfRefs = 0;
        this.string = string;
        this.refs = null;
    }
}
