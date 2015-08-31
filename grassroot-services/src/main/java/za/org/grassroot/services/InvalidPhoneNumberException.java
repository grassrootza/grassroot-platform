package za.org.grassroot.services;

/**
 * @author Lesetse Kimwaga
 */
public class InvalidPhoneNumberException extends  RuntimeException{

    public InvalidPhoneNumberException(String message) {
        super(message);
    }
}
