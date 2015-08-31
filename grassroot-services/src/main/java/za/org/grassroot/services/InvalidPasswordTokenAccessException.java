package za.org.grassroot.services;

/**
 * @author Lesetse Kimwaga
 */
public class InvalidPasswordTokenAccessException  extends RuntimeException{

    public InvalidPasswordTokenAccessException(String message) {
        super(message);
    }
}
