package demo;

import java.util.ArrayList;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class ActorA extends UntypedAbstractActor{

	// Logger attached to actor
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	// Actor references
	private ActorRef transmitter, actorB;

	public ActorA() {}

	// Static function creating actor
	public static Props createActor() {
		return Props.create(ActorA.class, () -> {
			return new ActorA();
		});
	}

	@Override
	public void onReceive(Object message) throws Throwable {
		if (message instanceof MyMessage) {
			MyMessage m = (MyMessage) message;
			if (m.nbOfStrings == 0) {
				transmitter = m.refs.get(0);
				actorB = m.refs.get(1);
				log.info("["+getSelf().path().name()+"] received references from ["+ getSender().path().name() +"]");
				log.info(m.nbOfRefs + " actor references added !");
			}
			else if (m.string.equals("start")) {
				log.info("["+getSelf().path().name()+"] received message \"start\" from ["+ getSender().path().name() +"]");
				transmitter.tell(new MyMessage("hello", actorB), getSelf());
			} else if (m.string.equals("hi!")) {
				log.info("["+getSelf().path().name()+"] received message \"hi!\" from ["+ getSender().path().name() +"]");
			}
		}
	}
}
