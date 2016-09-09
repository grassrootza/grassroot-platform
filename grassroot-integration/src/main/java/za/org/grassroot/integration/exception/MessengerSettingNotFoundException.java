package za.org.grassroot.integration.exception;

/**
 * Created by paballo on 2016/09/09.
 */
public class MessengerSettingNotFoundException extends RuntimeException {

    public MessengerSettingNotFoundException(String message){
        super(message);
    }
}

