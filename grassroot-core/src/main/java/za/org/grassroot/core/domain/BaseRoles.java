package za.org.grassroot.core.domain;

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


    public static Role defaultGroupOrganizerRole(Long groupId, String groupName) {
        return new Role(ROLE_GROUP_ORGANIZER, groupId, groupName);
    }

    public static Role defaultGroupCommitteeRole(Long groupId, String groupName) {
        return new Role(ROLE_COMMITTEE_MEMBER, groupId, groupName);
    }
    public static Role defaultGroupOrdinaryMemberRole(Long groupId, String groupName) {
        return new Role(ROLE_ORDINARY_MEMBER, groupId, groupName);
    }


}
