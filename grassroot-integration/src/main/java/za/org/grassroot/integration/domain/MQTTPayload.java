package za.org.grassroot.integration.domain;

import java.util.Date;

/**
 * Created by paballo on 2016/11/03.
 */
public class MQTTPayload {

    private String uid;
    private String phoneNumber;
    private String text;
    private String groupUid;
    private String userUid;
    private String displayName;
    private String groupName;
    private Date time;

    public String getUid() {
        return uid;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getText() {
        return text;
    }

    public String getGroupUid() {
        return groupUid;
    }

    public String getUserUid() {
        return userUid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getGroupName() {
        return groupName;
    }

    public Date getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "MQTTPayload{" +
                "uid='" + uid + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", text='" + text + '\'' +
                ", groupUid='" + groupUid + '\'' +
                ", userUid='" + userUid + '\'' +
                ", displayName='" + displayName + '\'' +
                ", groupName='" + groupName + '\'' +
                ", time=" + time +
                '}';
    }
}
