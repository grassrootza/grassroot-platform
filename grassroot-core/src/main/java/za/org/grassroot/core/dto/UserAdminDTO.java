package za.org.grassroot.core.dto;

import lombok.Getter;
import za.org.grassroot.core.domain.User;

@Getter
public class UserAdminDTO {
    protected String userUid;
    protected String displayName;

    public UserAdminDTO(User user){
        this.userUid = user.getUid();
        this.displayName = user.getDisplayName();
    }
}
