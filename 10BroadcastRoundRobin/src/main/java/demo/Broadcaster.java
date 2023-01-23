package demo;

import java.util.ArrayList;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Broadcaster extends UntypedAbstractActor{

	// Logger attached to actor
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

	private ArrayList<ActorRef> refs;

	public Broadcaster() {
		refs = new ArrayList<>();
	}

	// Static function creating actor
	public static Props createActor() {
		return Props.create(Broadcaster.class, () -> {
			return new Broadcaster();
		});
	}


	@Override
	public void onReceive(Object message) throws Throwable {
		if (message instanceof String) {
			String m = (String) message;
			if (m.equals("join")) {
				log.info("["+getSelf().path().name()+"] received message "+m+" from ["+ getSender().path().name() +"]");
				refs.add((ActorRef) getSender());
				log.info("Actor reference added !");
			} else if (m.equals("m")) {
				log.info("["+getSelf().path().name()+"] received message "+ m +" from ["+ getSender().path().name() +"]");
				for (ActorRef ref : refs) {
					ref.tell(m, getSelf());
				}
			}
		}

	}
}
