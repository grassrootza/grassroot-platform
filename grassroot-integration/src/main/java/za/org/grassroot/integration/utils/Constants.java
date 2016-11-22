package za.org.grassroot.integration.utils;

import java.time.format.DateTimeFormatter;

/**
 * Created by paballo on 2016/09/26.
 */
public class Constants {

    public static final String GROUP_UID = "groupUid";
    public static final String GROUP_NAME ="groupName";
    public static final String TITLE = "title";
    public static final String ENTITY_TYPE="entity_type";
    public static final String BODY="body";

    public static final DateTimeFormatter CHAT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
}
