package za.org.grassroot.webapp.model.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import za.org.grassroot.core.domain.media.ImageRecord;
import za.org.grassroot.core.domain.task.TaskLog;
import za.org.grassroot.core.enums.ActionLogType;

/**
 * Created by luke on 2017/02/25.
 */
@Getter
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
    private String caption;

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
        this.caption = taskLog.getTag();
    }

    @JsonProperty("key")
    public String getActionLogUid() {
        return actionLogUid;
    }

    public ActionLogType actionLogType() {
        return actionLogType;
    }

}
