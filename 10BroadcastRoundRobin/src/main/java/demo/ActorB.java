package demo;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class ActorB extends UntypedAbstractActor {

	// Logger attached to actor
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	// Broadcaster reference
	private ActorRef broadcaster;

	public ActorB(ActorRef broadcaster) {
		this.broadcaster = broadcaster;
		broadcaster.tell("join", getSelf());
	}

	// Static function creating actor
	public static Props createActor(ActorRef broadcaster) {
		return Props.create(ActorB.class, () -> {
			return new ActorB(broadcaster);
		});
	}


	@Override
	public void onReceive(Object message) throws Throwable {
		if (message instanceof String) {
			String m = (String) message;
			if (m.equals("m")) {
				log.info("["+getSelf().path().name()+"] received message "+m+" from ["+ getSender().path().name() +"]");
			}
		}
	}
}
