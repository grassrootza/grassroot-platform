package za.org.grassroot.services;

/**
 * @author Lesetse Kimwaga
 */
public class InvalidPhoneNumber extends  RuntimeException{

    public InvalidPhoneNumber(String message) {
        super(message);
    }
}
