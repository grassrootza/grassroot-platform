package za.org.grassroot.services.exception;

public class EventStartTimeNotInFutureException extends RuntimeException {
    public EventStartTimeNotInFutureException(String message) {
        super(message);
    }
}
