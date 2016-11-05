package za.org.grassroot.integration.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.List;

/**
 * Created by paballo on 2016/11/03.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MQTTPayload {

    private String uid;
    private String phoneNumber;
    private String text;
    private String groupUid;
    private String userUid;
    private String displayName;
    private String groupName;
    private String type;
    private Date time;
    private List<String> tokens;
    private Date actionDateTime;

    @JsonIgnore
    private boolean sending;
    @JsonIgnore
    private boolean sent;
    @JsonIgnore
    private boolean delivered;
    @JsonIgnore
    private int noAttempts;
    @JsonIgnore
    private boolean seen;
    @JsonIgnore
    private boolean read;
    @JsonIgnore
    private boolean server;
    @JsonIgnore
    private boolean toKeep;


    public MQTTPayload(){}

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

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setGroupUid(String groupUid) {
        this.groupUid = groupUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public void setTokens(List<String> tokens) {
        this.tokens = tokens;
    }

    public void setActionDateTime(Date actionDateTime) {
        this.actionDateTime = actionDateTime;
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
                ", type='" + type + '\'' +
                ", time=" + time +
                '}';
    }
}
