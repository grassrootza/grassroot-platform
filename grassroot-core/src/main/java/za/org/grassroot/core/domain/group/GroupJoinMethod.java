package za.org.grassroot.core.domain.group;

import java.util.Arrays;
import java.util.List;

public enum GroupJoinMethod {

    ADDED_BY_SYS_ADMIN,
    SELF_JOINED,
    BULK_IMPORT,

    ADDED_AT_CREATION,
    ADDED_BY_OTHER_MEMBER,

    CAMPAIGN_GENERAL,
    CAMPAIGN_PETITION,

    USSD_JOIN_CODE,
    SMS_JOIN_WORD,
    URL_JOIN_WORD,
    SEARCH_JOIN_WORD,
    JOIN_CODE_OTHER,

    BROADCAST,
    BROADCAST_FB,
    BROADCAST_TW,

    FILE_IMPORT,
    ADDED_SUBGROUP,
    COPIED_INTO_GROUP;

    public static List<GroupJoinMethod> JOIN_CODE_METHODS = Arrays.asList(USSD_JOIN_CODE, SMS_JOIN_WORD, URL_JOIN_WORD, SEARCH_JOIN_WORD, JOIN_CODE_OTHER);

}
