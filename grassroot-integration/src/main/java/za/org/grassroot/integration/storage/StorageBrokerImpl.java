package za.org.grassroot.integration.storage;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.ImageRecord;
import za.org.grassroot.core.enums.ActionLogType;
import za.org.grassroot.core.repository.ImageRecordRepository;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2017/02/21.
 */
@Service
public class StorageBrokerImpl implements StorageBroker {

    private static final Logger logger = LoggerFactory.getLogger(StorageBrokerImpl.class);

    @Value("${grassroot.event.images.bucket:null}")
    private String eventImagesBucket;

    @Value("${grassroot.todo.images.bucket:null}")
    private String todoImagesBucket;

    private final S3ClientFactory s3ClientFactory;
    private final ImageRecordRepository imageRecordRepository;

    @Autowired
    public StorageBrokerImpl(ImageRecordRepository imageRecordRepository) {
        this.imageRecordRepository = imageRecordRepository;
        s3ClientFactory = new S3ClientFactory();
    }

    @Override
    @Transactional
    public boolean storeImage(ActionLogType actionLogType, String actionLogUid, MultipartFile image) {
        Objects.requireNonNull(actionLogType);
        Objects.requireNonNull(actionLogUid);
        Objects.requireNonNull(image);

        final AmazonS3 s3 = s3ClientFactory.createClient();
        final TransferManager transferManager = TransferManagerBuilder.standard().withS3Client(s3).build();
        final String bucket = mapLogToBucket(actionLogType);

        logger.info("storing image in bucket: {}", bucket);

        boolean completed;

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(image.getSize());
            metadata.setContentType(image.getContentType());

            byte[] resultByte = DigestUtils.md5(image.getBytes());
            String streamMD5 = new String(Base64.encodeBase64(resultByte));
            metadata.setContentMD5(streamMD5);

            ImageRecord imageRecord = imageRecordRepository.save(new ImageRecord.Builder()
                    .actionLogType(actionLogType)
                    .actionLogUid(actionLogUid)
                    .md5(streamMD5)
                    .bucket(bucket)
                    .build());

            Upload upload = transferManager.upload(bucket, actionLogUid, image.getInputStream(), metadata);
            upload.waitForCompletion();
            imageRecord.setStoredTime(Instant.now());
            completed = true;
        } catch (AmazonServiceException|InterruptedException|IOException e) {
            completed = false;
            logger.error("Error uploading file: {}", e.toString());
        }

        transferManager.shutdownNow();
        return completed;
    }

    @Override
    public File retrieveImage(String key) {
        AmazonS3 s3 = s3ClientFactory.createClient();
        TransferManager transferManager = TransferManagerBuilder.standard().withS3Client(s3).build();

        File f = new File("temp");
        try {
            Download download = transferManager.download("bucket", key, f);
            download.waitForCompletion();
        } catch (AmazonServiceException|InterruptedException e) {
            logger.error("Error downloading file: {}", e.toString());
        }

        transferManager.shutdownNow();
        return f;
    }

    private String mapLogToBucket(ActionLogType type) {
        switch (type) {
            case EVENT_LOG:
                return eventImagesBucket;
            case TODO_LOG:
                return todoImagesBucket;
            default:
                return "null";
        }
    }
}
