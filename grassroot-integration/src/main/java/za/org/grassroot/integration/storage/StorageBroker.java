package za.org.grassroot.integration.storage;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.enums.ActionLogType;

import java.io.File;
import java.util.Set;

/**
 * Created by luke on 2017/02/21.
 */
public interface StorageBroker {

    boolean storeImage(ActionLogType actionLogType, String imageKey, MultipartFile image);

    boolean storeMedia(MediaFileRecord record, MultipartFile image);

    void recordImageAffiliation(ActionLogType actionLogType, String imageKey);

    byte[] fetchTaskImage(String uid, ImageType imageType);

    byte[] fetchThumbnail(String uid, ImageType imageType);

    boolean doesImageExist(String uid, ImageType imageType);

    void deleteImage(String uid);

    Set<MediaFileRecord> retrieveMediaRecordsForFunction(MediaFunction function, Set<String> mediaFileUids);

    File fetchFileFromRecord(MediaFileRecord record);

}
