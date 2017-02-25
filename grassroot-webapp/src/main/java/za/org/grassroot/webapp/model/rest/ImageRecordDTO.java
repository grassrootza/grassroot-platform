package za.org.grassroot.webapp.model.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import za.org.grassroot.core.domain.ImageRecord;
import za.org.grassroot.core.domain.Task;
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

    public ImageRecordDTO(String taskUid, ImageRecord imageRecord) {
        this.actionLogUid = imageRecord.getActionLogUid();
        this.actionLogType = imageRecord.getActionLogType();
        this.taskUid = taskUid;
        this.bucket = imageRecord.getBucket();
        this.creationTime = imageRecord.getCreationTime().toEpochMilli();
        this.storageTime = imageRecord.getStoredTime() != null ? imageRecord.getStoredTime().toEpochMilli() : null;
        this.md5 = imageRecord.getMd5();
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
}
