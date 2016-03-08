package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import org.springframework.http.HttpStatus;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;

/**
 * Created by paballo on 2016/03/08.
 */
public class TokenWrapper extends UserResponseWrapper {
    private VerificationTokenCode verificationTokenCode;

    public TokenWrapper(){

    }

    public TokenWrapper(HttpStatus code, RestMessage message, RestStatus status, VerificationTokenCode verificationTokenCode){
        super(code,message,status);
        this.verificationTokenCode = verificationTokenCode;
    }

    public VerificationTokenCode getVerificationTokenCode(){
        return verificationTokenCode;
    }
}
