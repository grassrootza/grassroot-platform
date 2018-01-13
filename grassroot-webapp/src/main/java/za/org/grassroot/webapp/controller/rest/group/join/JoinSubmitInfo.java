package za.org.grassroot.webapp.controller.rest.group.join;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.enums.Province;

import java.util.List;

@AllArgsConstructor @Getter
public class JoinSubmitInfo {

    String name;
    String email;
    String phone;
    String province;
    List<String> topics;

    public Province safeProvince() {
        return !StringUtils.isEmpty(province) && EnumUtils.isValidEnum(Province.class, province) ?
                Province.valueOf(province) : null;
    }

}
