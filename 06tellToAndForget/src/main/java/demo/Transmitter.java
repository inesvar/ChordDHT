package demo;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Transmitter extends UntypedAbstractActor{

	// Logger attached to actor
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	// Actor reference
	private ActorRef senderA;
	private ActorRef destinationB;
	private String message;

	public Transmitter() {}

	// Static function creating actor
	public static Props createActor() {
		return Props.create(Transmitter.class, () -> {
			return new Transmitter();
		});
	}


	@Override
	public void onReceive(Object message) throws Throwable {
		if (message instanceof MyMessage) {
			MyMessage m = (MyMessage) message;
			if (m.nbOfStrings == 1 & m.string.equals("hello")) {
				this.message = m.string;
				log.info("["+getSelf().path().name()+"] received message from ["+ getSender().path().name() +"]");
				destinationB = (ActorRef) m.refs.get(0);
				senderA = (ActorRef) getSender();
				log.info("Actor reference added ! New reference is: {}", destinationB);
				destinationB.tell(new MyMessage(this.message), senderA);
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
