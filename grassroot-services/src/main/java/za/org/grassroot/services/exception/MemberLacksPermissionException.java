package za.org.grassroot.services.exception;

import lombok.Getter;
import za.org.grassroot.core.domain.Permission;

@Getter
public class MemberLacksPermissionException extends RuntimeException {

    private final Permission permissionRequired;

    public MemberLacksPermissionException(Permission permissionRequired) {
        this.permissionRequired = permissionRequired;
    }
}
