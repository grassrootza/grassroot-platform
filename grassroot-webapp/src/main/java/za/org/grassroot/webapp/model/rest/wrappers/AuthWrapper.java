package za.org.grassroot.webapp.model.rest.wrappers;

import org.springframework.http.HttpStatus;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.dto.TokenDTO;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;

/**
 * Created by paballo on 2016/03/15.
 */
public class AuthWrapper extends GenericResponseWrapper {

    private String userUid;
    private String displayName;
    private String language;
    private boolean hasGroups;
    private int unreadNotificationCount;

    public static AuthWrapper create(boolean isNewRegistration, VerificationTokenCode token, User user,
                                     boolean hasGroups, int unreadNotificationCount) {
        RestMessage message = isNewRegistration ? RestMessage.USER_REGISTRATION_SUCCESSFUL : RestMessage.LOGIN_SUCCESS;
        AuthWrapper wrapper = new AuthWrapper(HttpStatus.OK, message,
                RestStatus.SUCCESS,
                new TokenDTO(token),
                user.getUid(),
                user.nameToDisplay(),
                user.getLanguageCode(),
                hasGroups);
        wrapper.setUnreadNotificationCount(unreadNotificationCount);
        return wrapper;
    }

    private AuthWrapper(HttpStatus code,
                       RestMessage message,
                       RestStatus status,
                       Object data,
                       String userUid,
                       String displayName,
                       String language,
                       boolean hasGroups){
        super(code,message,status, data);
        this.userUid = userUid;
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

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUserUid() {
        return userUid;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setHasGroups(boolean hasGroups) {
        this.hasGroups = hasGroups;
    }

    public int getUnreadNotificationCount() {
        return unreadNotificationCount;
    }

    public void setUnreadNotificationCount(int unreadNotificationCount) {
        this.unreadNotificationCount = unreadNotificationCount;
    }
}
