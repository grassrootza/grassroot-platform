package za.org.grassroot.services.exception;


public class CampaignMessageNotFoundException extends RuntimeException {

    private static final long serialVersionUID = -2527938530908007026L;
    private final String errorCode;

    public CampaignMessageNotFoundException(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
