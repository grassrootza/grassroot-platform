package za.org.grassroot.integration.storage;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.enums.ActionLogType;

import javax.swing.*;

/**
 * Created by luke on 2017/02/21.
 */
public interface StorageBroker {

    boolean storeImage(ActionLogType actionLogType, String imageKey, MultipartFile image);

    void recordImageAffiliation(ActionLogType actionLogType, String imageKey);

    byte[] fetchImage(String uid, ImageType imageType);

    byte[] fetchThumbnail(String uid, ImageType imageType);

    boolean doesImageExist(String uid, ImageType imageType);

    void deleteImage(String uid);

}
