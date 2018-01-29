package za.org.grassroot.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.repository.MediaFileRecordRepository;
import za.org.grassroot.integration.storage.StorageBroker;

@Service
public class MediaFileBrokerImpl implements MediaFileBroker {

    private static final Logger logger = LoggerFactory.getLogger(MediaFileBrokerImpl.class);

    @Value("${grassroot.task.images.bucket:null}")
    private String taskImagesBucket;

    @Value("${grassroot.livewire.media.bucket:null}")
    private String liveWireMediaBucket;

    @Value("${grassroot.media.user-photo.bucket:null}")
    private String userProfilePhotoBucket;

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
    public MediaFileRecord load(MediaFunction function, String imageKey) {
        return recordRepository.findByBucketAndKey(getBucketForFunction(function), imageKey);
    }

    @Override
    public boolean doesFileExist(MediaFunction function, String imageKey) {
        return recordRepository.findByBucketAndKey(getBucketForFunction(function), imageKey) != null;
    }

    @Override
    @Transactional
    public String storeFile(MultipartFile file, MediaFunction function, String mimeType, String imageKey) {
        logger.info("storing a file, with content type: {}, passed mime type: {}, original name: {}",
                file.getContentType(), mimeType, file.getOriginalFilename());

        String bucket = getBucketForFunction(function);
        MediaFileRecord record = recordRepository.findByBucketAndKey(bucket, imageKey);
        if (record == null)
            record = new MediaFileRecord(bucket, mimeType, imageKey);

        logger.info("created media record ...");

        if (storageBroker.storeMedia(record, file)) {
            record.setMimeType(mimeType);
            logger.info("media record stored and has mime type");
            recordRepository.save(record);
            return record.getUid();
        } else {
            logger.error("Error storing media file, returning null");
            return null;
        }
    }

    private String getBucketForFunction(MediaFunction function) {
        switch (function) {
            case TASK_IMAGE:
                return taskImagesBucket;
            case LIVEWIRE_MEDIA:
                return liveWireMediaBucket;
            case USER_PROFILE_IMAGE:
                return defaultMediaBucket;
            case BROADCAST_IMAGE:
                return defaultMediaBucket;
            default:
                return defaultMediaBucket;
        }
    }
}
