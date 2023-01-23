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
	// Actor reference
	private ArrayList<ActorRef> actorRef;

	public ActorA() {
		actorRef = new ArrayList<>();
	}

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
				actorRef = m.refs;
				log.info("["+getSelf().path().name()+"] received message from ["+ getSender().path().name() +"]");
				log.info(m.nbOfRefs + " actor references added !");
			}
			else if (m.string.equals("start")) {
				actorRef.get(0).tell(new MyMessage("hello", actorRef.get(1)), getSelf());
			} else if (m.string.equals("hi!")) {
				log.info("["+getSelf().path().name()+"] received message from ["+ getSender().path().name() +"]");
			}
		}
	}


	/**
	 * alternative for AbstractActor
	 * @Override
	public Receive createReceive() {
		return receiveBuilder()
				// When receiving a new message containing a reference to an actor,
				// Actor updates his reference (attribute).
				.match(ActorRef.class, ref -> {
					this.actorRef = ref;
					log.info("Actor reference updated ! New reference is: {}", this.actorRef);
				})
				.build();
	}
	 */
}
