package za.org.grassroot.services.task;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.enums.TaskType;

/**
 * Created by luke on 2017/02/21.
 */
public interface TaskImageBroker {

    String storeImageForTask(String userUid, String taskUid, TaskType taskType, MultipartFile file);

}
