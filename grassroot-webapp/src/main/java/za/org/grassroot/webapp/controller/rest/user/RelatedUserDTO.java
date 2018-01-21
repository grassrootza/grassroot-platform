package za.org.grassroot.webapp.controller.rest.user;

import lombok.Getter;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.Province;

@Getter
public class RelatedUserDTO {

    public String uid;
    public String name;
    public String phone;
    public String email;
    public Province province;

    public RelatedUserDTO(User user) {
        this.uid = user.getUid();
        this.name = user.getName();
        this.phone = user.getPhoneNumber();
        this.email = user.getEmailAddress();
        this.province = user.getProvince();
    }

}
