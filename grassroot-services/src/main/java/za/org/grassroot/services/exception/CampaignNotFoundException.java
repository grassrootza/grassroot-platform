package za.org.grassroot.services.exception;


public class CampaignNotFoundException extends RuntimeException {

    private static final long serialVersionUID = -3451576203763386274L;
    private final String errorCode;

    public CampaignNotFoundException(String errorCode){
        this.errorCode = errorCode;
    }
    public String getErrorCode() {
        return errorCode;
    }
}
