package za.org.grassroot.integration.storage;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.util.IOUtils;
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
import za.org.grassroot.integration.exception.ImageRetrievalFailure;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.specifications.ImageRecordSpecifications.actionLogType;
import static za.org.grassroot.core.specifications.ImageRecordSpecifications.actionLogUid;

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
    public ImageRecord fetchLogImageDetails(String actionLogUid, ActionLogType actionLogType) {
        return imageRecordRepository.findOne(where(actionLogUid(actionLogUid)).and(actionLogType(actionLogType)));
    }

    @Override
    public byte[] fetchImage(String uid, ActionLogType type) {
        try {
            AmazonS3 s3Client = s3ClientFactory.createClient();
            S3Object s3Image = s3Client.getObject(new GetObjectRequest(mapLogToBucket(type), uid));
            InputStream imageData = s3Image.getObjectContent();
            byte[] image = IOUtils.toByteArray(imageData);
            imageData.close();
            return image;
        } catch (SdkClientException|IOException e) {
            throw new ImageRetrievalFailure();
        }
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
