package za.org.grassroot.services.task;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.ImageRecord;
import za.org.grassroot.core.domain.TaskLog;
import za.org.grassroot.core.enums.TaskType;

import java.util.List;

/**
 * Created by luke on 2017/02/21.
 */
public interface TaskImageBroker {

    String storeImageForTask(String userUid, String taskUid, TaskType taskType, MultipartFile file, Double latitude, Double longitude);

    List<ImageRecord> fetchImagesForTask(String userUid, String taskUid, TaskType taskType);

    // replace with getter once have embedded new TaskLog interface
    TaskLog fetchLogForImage(String logUid, TaskType taskType);

    long countImagesForTask(String userUid, String taskUid, TaskType taskType);

    byte[] fetchImageForTask(String userUid, TaskType taskType, String logUid);

    byte[] fetchMicroThumbnailForTask(String userUid, TaskType taskType, String logUid);

}
