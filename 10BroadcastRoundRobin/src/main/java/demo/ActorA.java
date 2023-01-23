package demo;

import java.time.Duration;
import java.util.ArrayList;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class ActorA extends UntypedAbstractActor {

	// Logger attached to actor
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	// Broadcaster reference
	private ActorRef broadcaster;

	public ActorA(ActorRef broadcaster) {
		this.broadcaster = broadcaster;
		getContext().system().scheduler().scheduleOnce(Duration.ofMillis(1000), getSelf(), "go", getContext().system().dispatcher(), ActorRef.noSender());
	}

	// Static function creating actor
	public static Props createActor(ActorRef broadcaster) {
		return Props.create(ActorA.class, () -> {
			return new ActorA(broadcaster);
		});
	}


	@Override
	public void onReceive(Object message) throws Throwable {
		if (message instanceof String) {
			String m = (String) message;
			if (m.equals("go")) {
				log.info("["+getSelf().path().name()+"] received message "+m+" from ["+ getSender().path().name() +"]");
				broadcaster.tell("m", getSelf());
			}
		}
	}
}
