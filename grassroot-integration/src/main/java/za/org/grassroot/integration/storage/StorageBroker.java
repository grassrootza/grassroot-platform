package za.org.grassroot.integration.storage;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.enums.ActionLogType;

/**
 * Created by luke on 2017/02/21.
 */
public interface StorageBroker {

    // pass null to image if it is already stored
    boolean storeImage(ActionLogType actionLogType, String imageKey, MultipartFile image);

    byte[] fetchImage(String uid, ImageType imageType);

    boolean doesImageExist(String uid, ImageType imageType);

    void deleteImage(String uid);

}
