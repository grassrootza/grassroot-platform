package za.org.grassroot.services.exception;

/**
 * Created by paballo on 2016/08/12.
 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
