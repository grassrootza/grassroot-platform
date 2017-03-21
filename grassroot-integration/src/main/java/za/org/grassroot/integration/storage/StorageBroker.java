package za.org.grassroot.integration.storage;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.ImageRecord;
import za.org.grassroot.core.enums.ActionLogType;

/**
 * Created by luke on 2017/02/21.
 */
public interface StorageBroker {

    boolean storeImage(ActionLogType actionLogType, String actionLogUid, MultipartFile image);

    byte[] fetchImage(String uid, ImageType imageType);

    boolean doesImageExist(String uid, ImageType imageType);

}
