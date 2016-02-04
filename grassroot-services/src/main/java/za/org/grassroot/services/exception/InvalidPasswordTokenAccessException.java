package za.org.grassroot.services.exception;

/**
 * @author Lesetse Kimwaga
 */
public class InvalidPasswordTokenAccessException  extends RuntimeException{

    public InvalidPasswordTokenAccessException(String message) {
        super(message);
    }
}
