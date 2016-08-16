package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import org.springframework.http.HttpStatus;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;

/**
 * Created by paballo on 2016/03/15.
 */
public class AuthenticationResponseWrapper extends GenericResponseWrapper {

    private String displayName;
    private String language;
    private boolean hasGroups;


    public AuthenticationResponseWrapper(HttpStatus code, RestMessage message, RestStatus status, Object data,String displayName, String language, boolean hasGroups){
        super(code,message,status, data);
        this.hasGroups = hasGroups;
        this.displayName =displayName;
        this.language = language;

    }

    public boolean isHasGroups(){
        return hasGroups;
    }

    public String getDisplayName(){
        return displayName;
    }

}
