package demo;

import java.util.ArrayList;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Actor extends UntypedAbstractActor {

	// Logger attached to actor
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	// Actor references
	private ArrayList<ActorRef> refs;

	public Actor() {
		this.refs = new ArrayList<>();
	}

	// Static function creating actor
	public static Props createActor() {
		return Props.create(Actor.class, () -> {
			return new Actor();
		});
	}


	@Override
	public void onReceive(Object message) throws Throwable {
		if (message instanceof MyMessage) {
			MyMessage m = (MyMessage) message;
			if (m.nbOfStrings == 0) {
				for (ActorRef ref : m.refs) {
					this.refs.add(ref);
					log.info("["+getSelf().path().name()+"] received a reference to "+ ref);
				}
			} else if (m.nbOfStrings == 1) {
				if (m.string.equals("start")) {
					log.info("["+getSelf().path().name()+"] received a message "+m.string+" from "+ getSender().path().name());
					for (ActorRef ref : refs) {
						ref.tell(new MyMessage("m"), getSelf());
					}
				} else { 
					log.info("["+getSelf().path().name()+"] received a message "+m.string+" from "+ getSender().path().name());
					for (ActorRef ref : refs) {
						ref.tell(new MyMessage(m.string), getSelf());
					}
				}
			}
		}
	}
}
