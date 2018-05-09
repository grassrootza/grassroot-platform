package za.org.grassroot.integration;

import com.amazonaws.SdkClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.repository.MediaFileRecordRepository;
import za.org.grassroot.integration.storage.StorageBroker;

import java.util.Objects;

@Service
public class MediaFileBrokerImpl implements MediaFileBroker {

    private static final Logger logger = LoggerFactory.getLogger(MediaFileBrokerImpl.class);

    @Value("${grassroot.task.images.bucket:null}")
    private String taskImagesBucket;

    @Value("${grassroot.livewire.media.bucket:null}")
    private String liveWireMediaBucket;

    @Value("${grassroot.media.default.bucket:null}")
    private String defaultMediaBucket;

    private final StorageBroker storageBroker;
    private final MediaFileRecordRepository recordRepository;

    public MediaFileBrokerImpl(StorageBroker storageBroker, MediaFileRecordRepository recordRepository) {
        this.storageBroker = storageBroker;
        this.recordRepository = recordRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MediaFileRecord load(String uid) {
        return recordRepository.findOneByUid(uid);
    }

    @Override
    @Transactional(readOnly = true)
    public MediaFileRecord load(MediaFunction function, String imageKey) {
        return recordRepository.findByBucketAndKey(getBucketForFunction(function), imageKey);
    }

    @Override
    public boolean doesFileExist(MediaFunction function, String imageKey) {
        return imageKey != null && recordRepository.findByBucketAndKey(getBucketForFunction(function), imageKey) != null;
    }

    @Override
    @Transactional
    public String storeFile(MultipartFile file, MediaFunction function, String mimeType, String imageKey, String fileName) {
        String bucket = getBucketForFunction(Objects.requireNonNull(function));

        logger.info("storing a file, with function {}, bucket {}, content type: {}, passed mime type: {}, file name: {}, original name: {}",
                function, bucket, file.getContentType(), mimeType, fileName, file.getOriginalFilename());

        MediaFileRecord record = recordRepository.findByBucketAndKey(bucket, imageKey);
        String contentType = StringUtils.isEmpty(mimeType) ? file.getContentType() : mimeType;
        String nameToUse = StringUtils.isEmpty(fileName) ? file.getOriginalFilename() : fileName;
        if (record == null)
            record = new MediaFileRecord(bucket, contentType, imageKey, nameToUse);

        boolean fileStored = false;
        try {
            fileStored = storageBroker.storeMedia(record, file);
        } catch (SdkClientException e){
            logger.error("AWS SDK exception storing file ...",e.getMessage());
        }

        if (fileStored) {
            logger.info("media record stored and has mime type");
            record = recordRepository.save(record);
            return record.getUid();
        } else {
            logger.error("Error storing media file, returning null");
            return null;
        }
    }

    @Override
    @Transactional
    public void deleteFile(LiveWireAlert liveWireAlert, String imageKey, MediaFunction function){
        String bucket = getBucketForFunction(Objects.requireNonNull(function));
        MediaFileRecord record = recordRepository.findByBucketAndKey(bucket, imageKey);

        liveWireAlert.getMediaFiles().remove(record);
    }

    @Override
    public String getBucketForFunction(MediaFunction function) {
        switch (function) {
            case TASK_IMAGE:
                return taskImagesBucket;
            case LIVEWIRE_MEDIA:
                return liveWireMediaBucket;
            case USER_PROFILE_IMAGE:
                return defaultMediaBucket;
            case BROADCAST_IMAGE:
                return defaultMediaBucket;
            case CAMPAIGN_IMAGE:
                return defaultMediaBucket;
            default:
                return defaultMediaBucket;
        }
    }
}
