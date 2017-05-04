package za.org.grassroot.services.exception;

/**
 * Exception class to throw when call presumes a user and none exists; previously relied on NoSuchElement, but now not using List
 */
public class NoSuchUserException extends NullPointerException {
    public NoSuchUserException(String message) { super(message); }
}
