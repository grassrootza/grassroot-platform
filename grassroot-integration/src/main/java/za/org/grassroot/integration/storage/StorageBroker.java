package za.org.grassroot.integration.storage;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.enums.ActionLogType;

import java.io.File;

/**
 * Created by luke on 2017/02/21.
 */
public interface StorageBroker {

    boolean storeImage(ActionLogType actionLogType, String actionLogUid, MultipartFile image);

    File retrieveImage(String key);

}
