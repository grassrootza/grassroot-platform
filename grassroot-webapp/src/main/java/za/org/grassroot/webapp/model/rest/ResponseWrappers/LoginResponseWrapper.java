package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import org.springframework.http.HttpStatus;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;

/**
 * Created by paballo on 2016/03/08.
 */
public class LoginResponseWrapper extends TokenWrapper{
    private String displayName;

    public LoginResponseWrapper(HttpStatus code, RestMessage message, RestStatus status, VerificationTokenCode token, String displayName){
        super(code,message,status,token);
        this.displayName =displayName;

    }

    public String getDisplayName(){
        return displayName;
    }


}
