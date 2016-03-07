package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.VerificationTokenCode;

/**
 * Created by paballo on 2016/03/07.
 */
public class UserRegistrationResponseWrapper {

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public VerificationTokenCode getVerificationTokenCode() {
        return verificationTokenCode;
    }

    public void setVerificationTokenCode(VerificationTokenCode verificationTokenCode) {
        this.verificationTokenCode = verificationTokenCode;
    }

    String message;
     VerificationTokenCode verificationTokenCode;


}
