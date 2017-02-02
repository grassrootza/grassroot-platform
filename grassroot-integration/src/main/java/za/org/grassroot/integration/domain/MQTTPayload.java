package za.org.grassroot.integration.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
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
    private String taskType;
    private List<String> tokens;

    private LocalDateTime time;
    private LocalDateTime actionDateTime;

    public MQTTPayload(){
        // for serializing / deserializing
    }


    public MQTTPayload(String uid, String groupUid, String groupName, String displayName, String type) {
        this(uid, groupUid, groupName, displayName, LocalDateTime.now(), null, type);
    }

    public MQTTPayload(String uid, String groupUid, String groupName, String displayName,
                       LocalDateTime messageTime, LocalDateTime actionDateTime, String type) {
        this.uid =uid;
        this.groupUid =groupUid;
        this.displayName = displayName;
        this.groupName=groupName;
        this.time=messageTime;
        this.actionDateTime = actionDateTime;
        this.type= type;
    }

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

    public String getType() {
        return type;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public LocalDateTime getActionDateTime() {
        return actionDateTime;
    }

    public List<String> getTokens() {
        return tokens;
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

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public void setTokens(List<String> tokens) {
        this.tokens = tokens;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
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
