package za.org.grassroot.services.exception;

/**
 * @author Lesetse Kimwaga
 */
public class InvalidPhoneNumberException extends  RuntimeException{

    public InvalidPhoneNumberException(String message) {
        super(message);
    }
}
