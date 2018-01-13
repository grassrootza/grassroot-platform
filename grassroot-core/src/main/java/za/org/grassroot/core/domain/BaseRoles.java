package za.org.grassroot.core.domain;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public final class BaseRoles {

    public static final String ROLE_SYSTEM_ADMIN     = "ROLE_SYSTEM_ADMIN";
    public static final String ROLE_ACCOUNT_ADMIN    = "ROLE_ACCOUNT_ADMIN";
    public static final String ROLE_NEW_USER         = "ROLE_NEW_USER";
    public static final String ROLE_ALPHA_TESTER     = "ROLE_ALPHA_TESTER";
    public static final String ROLE_GROUP_ORGANIZER  = "ROLE_GROUP_ORGANIZER";
    public static final String ROLE_COMMITTEE_MEMBER = "ROLE_COMMITTEE_MEMBER";
    public static final String ROLE_ORDINARY_MEMBER  = "ROLE_ORDINARY_MEMBER";
    public static final String ROLE_GROUP_OBSERVER   = "ROLE_GROUP_OBSERVER";

    // note: ordering here is important, as will be default order for drop downs etc
    public static final List<String> groupRoles = Arrays.asList(ROLE_ORDINARY_MEMBER,
            ROLE_COMMITTEE_MEMBER,
            ROLE_GROUP_ORGANIZER);

    public static final Comparator<Role> sortSystemRole = new Comparator<Role>() {
        @Override
        public int compare(Role role, Role t1) {
            if (StringUtils.isEmpty(role.getName())) {
                return StringUtils.isEmpty(t1.getName()) ? 0 : -1;
            }

            if (role.getName().equals(t1.getName())) {
                return 0;
            }

            if (StringUtils.isEmpty(t1.getName())) {
                // we know role is not null so by definition it is greater
                return 1;
            }

            switch (role.getName()) {
                case ROLE_SYSTEM_ADMIN:
                    return 1;
                case ROLE_ACCOUNT_ADMIN: // we know it's not equal or null
                    return t1.getName().equals(ROLE_SYSTEM_ADMIN) ? -1 : 1;
                case ROLE_NEW_USER: // we know it's not equal, and t1 is not null, so must be greater
                    return -1;
            }

            return 0; // shouldn't get here, but return 0 to be safe
        }
    };

}
