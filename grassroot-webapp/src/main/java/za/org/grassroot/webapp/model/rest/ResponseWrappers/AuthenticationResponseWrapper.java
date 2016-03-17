package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import org.springframework.http.HttpStatus;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;

/**
 * Created by paballo on 2016/03/15.
 */
public class AuthenticationResponseWrapper extends GenericResponseWrapper {

    private boolean hasGroups;

    public AuthenticationResponseWrapper(HttpStatus code, RestMessage message, RestStatus status, Object data, boolean hasGroups){
        super(code,message,status, data);
        this.hasGroups = hasGroups;

    }

    public boolean isHasGroups(){
        return hasGroups;
    }
}
