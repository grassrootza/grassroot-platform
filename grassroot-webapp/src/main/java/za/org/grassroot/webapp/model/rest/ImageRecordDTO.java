package za.org.grassroot.webapp.model.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import za.org.grassroot.core.domain.media.ImageRecord;
import za.org.grassroot.core.domain.TaskLog;
import za.org.grassroot.core.enums.ActionLogType;

/**
 * Created by luke on 2017/02/25.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageRecordDTO {

    private String actionLogUid;
    private ActionLogType actionLogType;
    private String taskUid;
    private String bucket;
    private Long creationTime;
    private Long storageTime;
    private String md5;
    private String userDisplayName;
    private String userPhoneNumber;
    private Double latitude;
    private Double longitude;
    private boolean analyzed;
    private Integer numberFaces;
    private boolean countModified;
    private Integer revisedFaces;

    public ImageRecordDTO(TaskLog taskLog, ImageRecord imageRecord) {
        this.actionLogUid = imageRecord.getActionLogUid();
        this.actionLogType = imageRecord.getActionLogType();
        this.taskUid = taskLog.getTask().getUid();
        this.bucket = imageRecord.getBucket();
        this.creationTime = imageRecord.getCreationTime().toEpochMilli();
        this.storageTime = imageRecord.getStoredTime() != null ? imageRecord.getStoredTime().toEpochMilli() : null;
        this.md5 = imageRecord.getMd5();
        this.latitude = taskLog.getLocation() != null ? taskLog.getLocation().getLatitude() : null;
        this.longitude = taskLog.getLocation() != null ? taskLog.getLocation().getLongitude() : null;
        this.userDisplayName = taskLog.getUser().getName();
        this.userPhoneNumber = taskLog.getUser().getPhoneNumber();
        this.analyzed = imageRecord.isAnalyzed();
        this.numberFaces = imageRecord.getAnalyzedFaces();
        this.countModified = imageRecord.isCountModified();
        this.revisedFaces = imageRecord.getRevisedFaces();
    }

    @JsonProperty("key")
    public String getActionLogUid() {
        return actionLogUid;
    }

    public ActionLogType actionLogType() {
        return actionLogType;
    }

    public String getTaskUid() {
        return taskUid;
    }

    public String getBucket() {
        return bucket;
    }

    public Long getCreationTime() {
        return creationTime;
    }

    public Long getStorageTime() {
        return storageTime;
    }

    public String getMd5() {
        return md5;
    }

    public Double getLatitude() { return latitude; }

    public Double getLongitude() { return longitude; }

    public String getUserDisplayName() { return userDisplayName; }

    public String getUserPhoneNumber() { return userPhoneNumber; }

    public boolean isAnalyzed() { return analyzed; }

    public Integer getNumberFaces() { return numberFaces; }

    public boolean isCountModified() {
        return countModified;
    }

    public Integer getRevisedFaces() {
        return revisedFaces;
    }
}
