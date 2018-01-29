package za.org.grassroot.integration;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;

public interface MediaFileBroker {

    MediaFileRecord load(String uid);

    MediaFileRecord load(MediaFunction function, String imageKey);

    boolean doesFileExist(MediaFunction function, String imageKey);

    String storeFile(MultipartFile file, MediaFunction function, String mimeType, String imageKey);


}
