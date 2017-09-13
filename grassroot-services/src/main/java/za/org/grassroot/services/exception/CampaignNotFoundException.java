package za.org.grassroot.services.exception;


public class CampaignNotFoundException extends RuntimeException {

    public CampaignNotFoundException(String message){
        super(message);
    }
}
