package za.org.grassroot.integration.storage;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.ImageRecord;
import za.org.grassroot.core.enums.ActionLogType;
import za.org.grassroot.core.repository.ImageRecordRepository;
import za.org.grassroot.integration.exception.ImageRetrievalFailure;
import za.org.grassroot.integration.exception.NoMicroVersionException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Created by luke on 2017/02/21.
 */
@Service
public class StorageBrokerImpl implements StorageBroker {

    private static final Logger logger = LoggerFactory.getLogger(StorageBrokerImpl.class);

    @Value("${grassroot.task.images.bucket:null}")
    private String taskImagesBucket;

    @Value("${grassroot.task.images.analyzed.bucket:null}")
    private String taskImagesAnalyzedBucket;

    @Value("${grassroot.task.images.resized.bucket:null}")
    private String taskImagesResizedBucket;

    private final S3ClientFactory s3ClientFactory;
    private final ImageRecordRepository imageRecordRepository;

    @Autowired
    public StorageBrokerImpl(ImageRecordRepository imageRecordRepository) {
        this.imageRecordRepository = imageRecordRepository;
        this.s3ClientFactory = new S3ClientFactory();
    }

    @Override
    @Transactional
    public boolean storeImage(ActionLogType actionLogType, String imageKey, MultipartFile image) {
        Objects.requireNonNull(actionLogType);
        Objects.requireNonNull(imageKey);
        Objects.requireNonNull(image);

        final AmazonS3 s3 = s3ClientFactory.createClient();
        final TransferManager transferManager = TransferManagerBuilder.standard().withS3Client(s3).build();
        boolean completed;


        logger.debug("storing image in bucket: {}", taskImagesBucket);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(image.getSize());
            metadata.setContentType(image.getContentType());

            byte[] resultByte = DigestUtils.md5(image.getBytes());
            String streamMD5 = new String(Base64.encodeBase64(resultByte));
            metadata.setContentMD5(streamMD5);

            Upload upload = transferManager.upload(taskImagesBucket, imageKey, image.getInputStream(), metadata);
            upload.waitForCompletion();

            ImageRecord imageRecord = storeImageRecord(actionLogType, imageKey, streamMD5);
            imageRecord.setStoredTime(Instant.now());
            completed = true;
        } catch (AmazonServiceException | InterruptedException | IOException e) {
            completed = false;
            logger.error("Error uploading file: {}", e.toString());
        }

        transferManager.shutdownNow();
        return completed;
    }

    @Override
    public void recordImageAffiliation(ActionLogType actionLogType, String imageKey) {
        storeImageRecord(actionLogType, imageKey, null);
    }

    private ImageRecord storeImageRecord(ActionLogType actionLogType, String imageKey, String streamMd5) {
        ImageRecord.Builder builder = new ImageRecord.Builder()
                .actionLogType(actionLogType)
                .actionLogUid(imageKey)
                .bucket(taskImagesBucket);

        if (!StringUtils.isEmpty(streamMd5)) {
            builder.md5(streamMd5);
        }

        return imageRecordRepository.save(builder.build());
    }

    @Override
    public byte[] fetchImage(String uid, ImageType imageType) {
        Objects.requireNonNull(uid);
        Objects.requireNonNull(imageType);

        final String bucket = selectBucket(imageType);
        logger.info("for imageType {}, selected bucket {}", imageType, bucket);

        try {
            // todo : optimize this (see SDK JavaDocs on possible performance issues)
            AmazonS3 s3Client = s3ClientFactory.createClient();
            S3Object s3Image = s3Client.getObject(new GetObjectRequest(bucket, composeKey(uid, imageType)));
            InputStream imageData = s3Image.getObjectContent();
            byte[] image = IOUtils.toByteArray(imageData);
            imageData.close();
            return image;
        } catch (SdkClientException e) {
            // todo: try add a check if it's something other than file not found on the bucket, and use that to discriminate error type
            logger.info("error in retrieving file: {}", e.getMessage());
            throw ImageType.MICRO.equals(imageType) ? new NoMicroVersionException() : new ImageRetrievalFailure();
        } catch (IOException e) {
            throw new ImageRetrievalFailure();
        }
    }

    @Override
    public byte[] fetchThumbnail(String uid, ImageType imageType) {
        try {
            return fetchImage(uid, imageType);
        } catch (NoMicroVersionException|ImageRetrievalFailure e) {
            return fetchImage(uid, ImageType.FULL_SIZE);
        }
    }

    @Override
    public boolean doesImageExist(String uid, ImageType imageType) {
        AmazonS3 s3client = s3ClientFactory.createClient();
        logger.info("trying to find key in bucket: {}", selectBucket(imageType));
        try {
            return s3client.doesObjectExist(selectBucket(imageType), composeKey(uid, imageType));
        } catch (AmazonS3Exception e) {
            logger.error("S3 exception, of code: {}, for bucket: {}, with key: {}", e.getErrorCode(),
                    selectBucket(imageType), composeKey(uid, imageType));
            return false;
        }
    }

    @Async
    @Override
    public void deleteImage(String uid) {
        AmazonS3 s3client = s3ClientFactory.createClient();
        Stream.of(ImageType.values())
                .filter(t -> s3client.doesObjectExist(selectBucket(t), composeKey(uid, t)))
                .forEach(t -> {
                    try {
                        s3client.deleteObject(selectBucket(t), composeKey(uid, t));
                        logger.info("Deleted S3 object, with key: {}", composeKey(uid, t));
                    } catch (AmazonS3Exception e) {
                        logger.error("Error deleting objects! Error: {}", e.getErrorCode());
                    }
                });
        }

    private String selectBucket(ImageType size) {
        return ImageType.ANALYZED.equals(size) ? taskImagesAnalyzedBucket :
                ImageType.FULL_SIZE.equals(size) ? taskImagesBucket :
                        taskImagesResizedBucket;
    }

    private String composeKey(String uid, ImageType size) {
        switch (size) {
            case LARGE_THUMBNAIL:
                return "midsize/" + uid;
            case MICRO:
                return "micro/" + uid;
            case FULL_SIZE:
            default:
                return uid;
        }
    }

}
