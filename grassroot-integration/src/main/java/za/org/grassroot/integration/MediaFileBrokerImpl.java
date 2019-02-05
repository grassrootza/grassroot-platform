package za.org.grassroot.integration;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.repository.MediaFileRecordRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.storage.StorageBroker;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MediaFileBrokerImpl implements MediaFileBroker {

    private static final Logger logger = LoggerFactory.getLogger(MediaFileBrokerImpl.class);

    @Value("${grassroot.task.images.bucket:null}")
    private String taskImagesBucket;

    @Value("${grassroot.livewire.media.bucket:grassroot-livewire-media}")
    private String liveWireMediaBucket;

    @Value("${grassroot.media.default.bucket:null}")
    private String defaultMediaBucket;

    @Value("${grassroot.campaign.media.enabled:false}")
    public boolean campaignMediaEnabled; // i.e., if we have these recorded in NoSQL table (over time will migrate all media to that pattern)

    private final StorageBroker storageBroker;
    private final UserRepository userRepository;
    private final MediaFileRecordRepository recordRepository;

    private AmazonDynamoDB dynamoDBClient;

    public MediaFileBrokerImpl(StorageBroker storageBroker, UserRepository userRepository, MediaFileRecordRepository recordRepository) {
        this.storageBroker = storageBroker;
        this.userRepository = userRepository;
        this.recordRepository = recordRepository;
    }

    @PostConstruct
    public void init() {
        if (campaignMediaEnabled) {
            dynamoDBClient = AmazonDynamoDBClientBuilder.standard()
                    .withRegion(Regions.EU_WEST_1)
                    .withCredentials(new ProfileCredentialsProvider("default")).build();
        }
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
            record = new MediaFileRecord(bucket, contentType, imageKey, nameToUse, null);

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
    public String recordFile(String userUid, String bucket, String mimeType, String imageKey, String fileName) {
        logger.info("Storing a file with bucket {}, key {}, mime type {}", bucket, imageKey, mimeType);
        MediaFileRecord record = new MediaFileRecord(bucket, mimeType, imageKey, fileName, userUid);
        record.setStoredTime(Instant.now());
        record = recordRepository.save(record);
        return record.getUid();
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

    @Override
    public List<MediaFileRecord> fetchInboundMediaRecordsForCampaign(String campaignUid) {
        if (!campaignMediaEnabled) {
            logger.info("Campaign media not enabled, returning empty list");
            return new ArrayList<>();
        }

        logger.info("Fetching campaign media records, for campaign id: {}", campaignUid);
        DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
        Table mediaFileTable = dynamoDB.getTable("inbound_media_file_records");
        Index timeSortedIndex = mediaFileTable.getIndex("assoc_entity_id-stored_timestamp-index");

        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put(":id", campaignUid);

        QuerySpec querySpec = new QuerySpec()
                .withKeyConditionExpression("assoc_entity_id = :id")
                .withValueMap(valueMap)
                .withScanIndexForward(false);

        try {
            Iterator<Item> outcome = timeSortedIndex.query(querySpec).iterator();
            List<MediaFileRecord> records = new ArrayList<>();
            while (outcome.hasNext()) {
                Item item = outcome.next();
                final String submittingUserUid = item.getString("submitting_user_id");
                final String key = item.getString("folder") + "/" + item.getString("media_file_id");
                logger.info("Converting media file with key {}, submitted by user with uid: {}", key, submittingUserUid);

                MediaFileRecord record = new MediaFileRecord(
                        item.getString("bucket"),
                        item.getString("media_type"),
                        key,
                        item.getString("media_file_id"),
                        submittingUserUid);

                record.setStoredTime(Instant.ofEpochMilli(item.getLong("stored_timestamp")));
                record.setPreSignedUrl(storageBroker.getPresignedUrl(item.getString("bucket"), key));

                // when we start having large numbers of submissions this will get non-performant, so fix it then
                if (!StringUtils.isEmpty(submittingUserUid)) {
                    User submittingUser = userRepository.findOneByUid(submittingUserUid); // todo: watch this and use projection if gets slow
                    record.setCreatedByUserName(submittingUser != null ? submittingUser.getName() : null);
                }

                logger.info("Converted DynamoDB record to: {}", record);
                records.add(record);
            }
            return records;
        } catch (AmazonServiceException e) {
            logger.error("Error retrieving media files! Error: ", e);
            return new ArrayList<>();
        }
    }

}
