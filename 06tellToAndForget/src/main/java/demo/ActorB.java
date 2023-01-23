package demo;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class ActorB extends UntypedAbstractActor{

	// Logger attached to actor
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	// Actor reference
	private ActorRef senderA;

	public ActorB() {}

	// Static function creating actor
	public static Props createActor() {
		return Props.create(ActorB.class, () -> {
			return new ActorB();
		});
	}


	@Override
	public void onReceive(Object message) throws Throwable {
		if (message instanceof MyMessage) {
			MyMessage m = (MyMessage) message;
			if (m.string.equals("hello")) {
				senderA = (ActorRef) getSender();
				log.info("["+getSelf().path().name()+"] received message from ["+ getSender().path().name() +"]");
				log.info("Actor reference added ! New reference is: {}", senderA);
				senderA.tell(new MyMessage("hi!"), getSelf());
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
