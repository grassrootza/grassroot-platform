package za.org.grassroot.integration.storage;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
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
import za.org.grassroot.core.domain.media.ImageRecord;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.enums.ActionLogType;
import za.org.grassroot.core.repository.ImageRecordRepository;
import za.org.grassroot.core.repository.MediaFileRecordRepository;
import za.org.grassroot.integration.exception.NoMicroVersionException;
import za.org.grassroot.integration.exception.StoredMediaRetrievalFailure;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
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

    @Value("${grassroot.livewire.media.bucket:null}")
    private String liveWireMediaBucket;

    @Value("${grassroot.media.general.bucket:null}")
    private String generalMediaStorageBucket;

    private final S3ClientFactory s3ClientFactory;
    private final ImageRecordRepository imageRecordRepository; // for task images, which get analyzed etc
    private final MediaFileRecordRepository mediaFileRepository; // for other media, which just get stored and retrieved

    @Autowired
    public StorageBrokerImpl(ImageRecordRepository imageRecordRepository, MediaFileRecordRepository mediaFileRepository) {
        this.imageRecordRepository = imageRecordRepository;
        this.mediaFileRepository = mediaFileRepository;
        this.s3ClientFactory = new S3ClientFactory();
    }

    @Override
    @Transactional
    public boolean storeImage(ActionLogType actionLogType, String imageKey, MultipartFile image) {
        Objects.requireNonNull(actionLogType);
        Objects.requireNonNull(imageKey);
        Objects.requireNonNull(image);

        logger.debug("storing image in bucket: {}", taskImagesBucket);

        String md5result = uploadToS3(taskImagesBucket, imageKey, image);

        if (md5result != null) {
            ImageRecord imageRecord = storeImageRecord(actionLogType, imageKey, md5result);
            imageRecord.setStoredTime(Instant.now());
        }

        return md5result != null;
    }

    @Override
    @Transactional
    public boolean storeMedia(MediaFileRecord record, MultipartFile file) {
        Objects.requireNonNull(record);
        Objects.requireNonNull(file);

        logger.info("storing a media file in bucket {}, with key {}", record.getBucket(), record.getKey());
        String md5hash = uploadToS3(record.getBucket(), record.getKey(), file);

        if (md5hash != null) {
            record.setMd5(md5hash);
            record.setStoredTime(Instant.now());
        }

        return md5hash != null;
    }

    private String uploadToS3(String bucket, String key, MultipartFile file) {
        final AmazonS3 s3 = s3ClientFactory.createClient();
        final TransferManager transferManager = TransferManagerBuilder.standard().withS3Client(s3).build();
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            byte[] resultByte = DigestUtils.md5(file.getBytes());
            String streamMD5 = new String(Base64.encodeBase64(resultByte));
            metadata.setContentMD5(streamMD5);

            Upload upload = transferManager.upload(bucket, key, file.getInputStream(), metadata);
            upload.waitForCompletion();
            return streamMD5;
        } catch (AmazonServiceException | InterruptedException | IOException e) {
            logger.error("Error uploading file: {}", e.toString());
            return null;
        } finally {
            transferManager.shutdownNow();
        }
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
    public byte[] fetchTaskImage(String uid, ImageType imageType) {
        Objects.requireNonNull(uid);
        Objects.requireNonNull(imageType);

        final String bucket = selectTaskBucketBySize(imageType);
        logger.debug("for imageType {}, selected bucket {}", imageType, bucket);

        try {
            InputStream imageData = getObject(bucket, composeKey(uid, imageType)).getObjectContent();
            byte[] image = IOUtils.toByteArray(imageData);
            imageData.close();
            return image;
        } catch (StoredMediaRetrievalFailure e) {
            // todo: try add a check if it's something other than file not found on the bucket, and use that to discriminate error type
            logger.info("error in retrieving file: {}", e.getMessage());
            throw ImageType.MICRO.equals(imageType) ? new NoMicroVersionException() : new StoredMediaRetrievalFailure();
        } catch (IOException e) {
            throw new StoredMediaRetrievalFailure();
        }
    }

    @Override
    public byte[] fetchThumbnail(String uid, ImageType imageType) {
        try {
            return fetchTaskImage(uid, imageType);
        } catch (NoMicroVersionException|StoredMediaRetrievalFailure e) {
            return fetchTaskImage(uid, ImageType.FULL_SIZE);
        }
    }

    @Override
    public boolean doesImageExist(String uid, ImageType imageType) {
        AmazonS3 s3client = s3ClientFactory.createClient();
        logger.info("trying to find key in bucket: {}", selectTaskBucketBySize(imageType));
        try {
            return s3client.doesObjectExist(selectTaskBucketBySize(imageType), composeKey(uid, imageType));
        } catch (AmazonS3Exception e) {
            logger.error("S3 exception, of code: {}, for bucket: {}, with key: {}", e.getErrorCode(),
                    selectTaskBucketBySize(imageType), composeKey(uid, imageType));
            return false;
        }
    }

    @Async
    @Override
    public void deleteImage(String uid) {
        AmazonS3 s3client = s3ClientFactory.createClient();
        Stream.of(ImageType.values())
                .filter(t -> s3client.doesObjectExist(selectTaskBucketBySize(t), composeKey(uid, t)))
                .forEach(t -> {
                    try {
                        s3client.deleteObject(selectTaskBucketBySize(t), composeKey(uid, t));
                        logger.info("Deleted S3 object, with key: {}", composeKey(uid, t));
                    } catch (AmazonS3Exception e) {
                        logger.error("Error deleting objects! Error: {}", e.getErrorCode());
                    }
                });
        }

    @Override
    @Transactional(readOnly = true)
    public Set<MediaFileRecord> retrieveMediaRecordsForFunction(MediaFunction function, Set<String> mediaFileUids) {
        return mediaFileRepository.findByBucketAndKeyIn(selectBucketByFunction(function), mediaFileUids);
    }

    @Override
    public File fetchFileFromRecord(MediaFileRecord record) {
        try {
            S3Object s3Object = getObject(record.getBucket(), record.getKey());
            S3ObjectInputStream s3is = s3Object.getObjectContent();
            File outputFile = File.createTempFile(record.getKey(), "jpg"); // todo: extend to MIME types
            FileOutputStream fos = new FileOutputStream(outputFile);
            byte[] read_buf = new byte[1024];
            int read_len = 0;
            while ((read_len = s3is.read(read_buf)) > 0) {
                fos.write(read_buf, 0, read_len);
            }
            s3is.close();
            fos.close();
            return outputFile;
        } catch (StoredMediaRetrievalFailure e) {
            logger.error("Error fetching from S3", e);
            return null;
        } catch (IOException e) {
            logger.error("Error handling file", e);
            return null;
        }

    }

    // todo : optimize this (see SDK JavaDocs on possible performance issues)
    private S3Object getObject(String bucket, String key) {
        try {
            AmazonS3 s3Client = s3ClientFactory.createClient();
            return s3Client.getObject(new GetObjectRequest(bucket, key));
        } catch (SdkClientException e) {
            logger.error("Error fetching from S3", e);
            throw new StoredMediaRetrievalFailure();
        }
    }

    private String selectBucketByFunction(MediaFunction function) {
        return MediaFunction.TASK_IMAGE.equals(function) ? taskImagesAnalyzedBucket :
                MediaFunction.LIVEWIRE_MEDIA.equals(function) ? liveWireMediaBucket :
                        generalMediaStorageBucket;
    }

    private String selectTaskBucketBySize(ImageType size) {
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
