package za.org.grassroot.core.domain;

import org.springframework.security.acls.domain.AclFormattingUtils;
import org.springframework.security.core.GrantedAuthority;

import java.util.HashMap;
import java.util.Map;

public enum Permission implements GrantedAuthority, org.springframework.security.acls.model.Permission {

    PERMISSION_SEE_ALL_GROUPS(2),
    PERMISSION_SEE_ALL_USERS(4),
    PERMISSION_RESET_USER_PASSWORD(8),
    PERMISSION_REMOVE_USER(16),
    PERMISSION_CREATE_NEW_GROUP(32),
    PERMISSION_DELETE_GROUP(64),
    PERMISSION_ASSIGN_PERMISSION_TEMPLATE(128),
    PERMISSION_SEE_PAID_FOR_GROUPS(256),
    PERMISSION_DENOTE_PAID_GROUP(512),
    PERMISSION_REVOKE_PAID_GROUP(1024),
    PERMISSION_VIEW_ACCOUNT_DETAILS(2048),
    PERMISSION_UPDATE_OWN_PROFILE(4096),

    GROUP_PERMISSION_UPDATE_GROUP_DETAILS(8192),
    GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE(16384),
    GROUP_PERMISSION_FORCE_PERMISSION_CHANGE(32768),
    GROUP_PERMISSION_CREATE_SUBGROUP(65536),
    GROUP_PERMISSION_AUTHORIZE_SUBGROUP(131072),
    GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION(262144),
    GROUP_PERMISSION_DELINK_SUBGROUP(524288),
    GROUP_PERMISSION_ADD_GROUP_MEMBER(1048576),
    GROUP_PERMISSION_FORCE_ADD_MEMBER(2097152),
    GROUP_PERMISSION_DELETE_GROUP_MEMBER(4194304),
    GROUP_PERMISSION_FORCE_DELETE_MEMBER(8388608),
    GROUP_PERMISSION_SEE_MEMBER_DETAILS(16777216),
    GROUP_PERMISSION_CREATE_GROUP_MEETING(33554432),
    GROUP_PERMISSION_VIEW_MEETING_RSVPS(67108864),
    GROUP_PERMISSION_CREATE_GROUP_VOTE(134217728),
    GROUP_PERMISSION_READ_UPCOMING_EVENTS(268435456),
    GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY(536870912),
    GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK(1073741824);

    private final int mask;

    private static final Map<Integer, Permission> valuesByMask = new HashMap<>();

    static {
        for (Permission permission : Permission.values()) {
            int permissionMask = permission.getMask();
            if (valuesByMask.containsKey(permissionMask)) {
                throw new IllegalStateException("There is already a Permission enum with mask: " + permissionMask);
            }
            valuesByMask.put(permissionMask, permission);
        }
    }

    Permission(int mask) {
        this.mask = mask;
    }

    @Override
    public String getAuthority() {
        return name();
    }

    public String getName() {
        return name();
    }

    @Override
    public int getMask() {
        return mask;
    }

    public static Permission valueOfMask(int mask) {
        Permission permission = valuesByMask.get(mask);
        if (permission == null) {
            throw new IllegalArgumentException("No Permission under mask " + mask);
        }
        return permission;
    }

    @Override
    public String getPattern() {
        return AclFormattingUtils.printBinary(mask, '*');
    }
}
