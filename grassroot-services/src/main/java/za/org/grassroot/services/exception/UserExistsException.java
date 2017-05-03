package za.org.grassroot.services.exception;

/**
 * An exception that is thrown by classes wanting to trap unique constraint violations
 */
public class UserExistsException extends RuntimeException {
    public UserExistsException(String message) {
        super(message);
    }
}
