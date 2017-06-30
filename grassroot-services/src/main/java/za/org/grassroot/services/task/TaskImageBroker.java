package za.org.grassroot.services.task;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.ImageRecord;
import za.org.grassroot.core.domain.TaskLog;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.TaskType;

import java.util.List;

/**
 * Created by luke on 2017/02/21.
 */
public interface TaskImageBroker {

    String storeImagePreTask(TaskType taskType, MultipartFile file);

    // use this one for an image passed directly to us
    String storeImageForTask(String userUid, String taskUid, TaskType taskType, MultipartFile file, Double latitude, Double longitude);

    // use this one for where image has previously been uploaded (e.g., via microservice)
    void recordImageForTask(String userUid, String taskUid, TaskType taskType, String imageKey, EventLogType logType);

    String getShortUrl(String imageKey);

    List<ImageRecord> fetchImagesForTask(String userUid, String taskUid, TaskType taskType);

    ImageRecord fetchImageRecord(String logUid, TaskType taskType);

    TaskLog fetchLogForImage(String logUid, TaskType taskType); // replace with getter once have embedded new TaskLog interface

    void updateImageFaceCount(String userUid, String logUid, TaskType taskType, int faceCount);

    long countImagesForTask(String userUid, String taskUid, TaskType taskType);

    byte[] fetchImageForTask(String userUid, TaskType taskType, String logUid, boolean checkAnalyzed);

    byte[] fetchMicroThumbnailForTask(String userUid, TaskType taskType, String logUid);

    String removeTaskImageRecord(String userUid, TaskType taskType, String logUid, boolean removeFromStorage);

}
