package demo;

public class SchedulerMessage extends DirectMessage {

    // internal messages (fixFingers, checkAlive, notifySuccessor)
    public SchedulerMessage(Chord.directMessages messageType) {
        super(messageType);
    }
}
