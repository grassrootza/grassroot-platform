package za.org.grassroot.services.task;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.ImageRecord;
import za.org.grassroot.core.enums.TaskType;

import java.io.File;
import java.util.List;

/**
 * Created by luke on 2017/02/21.
 */
public interface TaskImageBroker {

    String storeImageForTask(String userUid, String taskUid, TaskType taskType, MultipartFile file);

    List<ImageRecord> fetchImagesForTask(String userUid, String taskUid, TaskType taskType);

    byte[] fetchImageForTask(String userUid, TaskType taskType, String logUid);

}
