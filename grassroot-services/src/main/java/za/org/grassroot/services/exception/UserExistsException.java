package za.org.grassroot.services.exception;

/**
 *
 * An exception that is thrown by classes wanting to trap unique constraint violations
 * @author Lesetse Kimwaga
 */
public class UserExistsException extends Exception {

    public UserExistsException(String message) {
        super(message);
    }
}
