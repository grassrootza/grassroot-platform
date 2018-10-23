package za.org.grassroot.integration;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;

import java.util.List;

public interface MediaFileBroker {

    MediaFileRecord load(String uid);

    MediaFileRecord load(MediaFunction function, String imageKey);

    boolean doesFileExist(MediaFunction function, String imageKey);

    String storeFile(MultipartFile file, MediaFunction function, String mimeType, String imageKey, String fileName);

    void deleteFile(LiveWireAlert liveWireAlert, String imageKey, MediaFunction function);

    String getBucketForFunction(MediaFunction function);

    List<MediaFileRecord> fetchInboundMediaRecordsForCampaign(String campaignUid);

}
