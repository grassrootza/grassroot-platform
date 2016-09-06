package za.org.grassroot.core.domain;

import java.util.Arrays;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public final class BaseRoles {

    public static final String ROLE_SYSTEM_ADMIN     = "ROLE_SYSTEM_ADMIN";
    public static final String ROLE_ACCOUNT_ADMIN    = "ROLE_ACCOUNT_ADMIN";
    public static final String ROLE_NEW_USER         = "ROLE_NEW_USER";
    public static final String ROLE_GROUP_ORGANIZER  = "ROLE_GROUP_ORGANIZER";
    public static final String ROLE_COMMITTEE_MEMBER = "ROLE_COMMITTEE_MEMBER";
    public static final String ROLE_ORDINARY_MEMBER  = "ROLE_ORDINARY_MEMBER";

    // note: ordering here is important, as will be default order for drop downs etc
    public static final List<String> groupRoles = Arrays.asList(ROLE_ORDINARY_MEMBER,
            ROLE_COMMITTEE_MEMBER,
            ROLE_GROUP_ORGANIZER);

}
