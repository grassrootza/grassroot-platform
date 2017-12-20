package za.org.grassroot.integration.location;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.notification.FreeFormMessageNotification;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.repository.AccountLogRepository;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.NotificationRepository;
import za.org.grassroot.core.repository.UserRepository;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

// todo : use JWT? though, these are open datasets (similar q on http vs https, esp if these are in same VPC)
@Component @Slf4j
@ConditionalOnProperty(name = "grassroot.geo.apis.enabled", matchIfMissing = false)
public class LocationInfoBrokerImpl implements LocationInfoBroker {

    private final Environment environment;
    private final RestTemplate restTemplate;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountLogRepository accountLogRepository;
    private final NotificationRepository notificationRepository;

    private boolean useDynamoDirect;
    private String geoApiHost;
    private Integer geoApiPort;
    private AmazonDynamoDB dynamoDBClient;

    @Autowired
    public LocationInfoBrokerImpl(Environment environment, RestTemplate restTemplate, UserRepository userRepository, AccountRepository accountRepository, AccountLogRepository accountLogRepository, NotificationRepository notificationRepository) {
        this.environment = environment;
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountLogRepository = accountLogRepository;
        this.notificationRepository = notificationRepository;
    }

    @PostConstruct
    public void init() {
        log.info("GeoAPI integration is active, setting up URLs etc");
        useDynamoDirect = environment.getProperty("grassroot.geo.dynamodb.direct", Boolean.class, false);
        geoApiHost = environment.getProperty("grassroot.geo.apis.host", "localhost");
        geoApiPort = environment.getProperty("grassroot.geo.apis.port", Integer.class, 80);

        if (useDynamoDirect) {
            log.info("okay we are using dynamo db directly for geo APIs ...");
            dynamoDBClient = AmazonDynamoDBClientBuilder.standard()
                    .withRegion(Regions.EU_WEST_1)
                    .withCredentials(new ProfileCredentialsProvider("geoApisDynamoDb")).build();
        }
    }

    private UriComponentsBuilder baseBuilder() {
        return UriComponentsBuilder.newInstance()
                .scheme("http")
                .host(geoApiHost)
                .port(geoApiPort);
    }

    @Override
    public List<Province> getAvailableProvincesForDataSet(String dataSetLabel) {
        List<Province> provinceList;
        if (!useDynamoDirect) {
            URI uriToCall = baseBuilder()
                    .path("/provinces/available/{dataset}")
                    .buildAndExpand(dataSetLabel).toUri();

            log.info("finding available provinces for: {}", uriToCall);
            provinceList = getFromUri(uriToCall).stream()
                    .map(Province::valueOf).collect(Collectors.toList());
        } else {
            provinceList = getFromDynamo(dataSetLabel, "provinces", true).stream()
                    .map(Province::valueOf).collect(Collectors.toList());
        }
        return provinceList;
    }

    @Override
    public List<Locale> getAvailableLocalesForDataSet(String dataSetLabel) {
        if (!useDynamoDirect) {
            URI uriToCall = baseBuilder()
                    .path("/languages/available/{dataset}")
                    .buildAndExpand(dataSetLabel).toUri();
            log.info("finding available languages, using URI: {}", uriToCall);
            return getFromUri(uriToCall).stream().map(Locale::new).collect(Collectors.toList());
        } else {
            return getFromDynamo(dataSetLabel, "languages", false).stream()
                    .map(Locale::new).collect(Collectors.toList());
        }
    }

    @Override
    public List<String> getAvailableInfoForProvince(String dataSetLabel, Province province, Locale locale) {
        if (!useDynamoDirect) {
            URI uriToCall = baseBuilder()
                    .path("/sets/available/{dataset}")
                    .queryParam("province", province.toString())
                    .queryParam("locale", locale.toLanguageTag())
                    .buildAndExpand(dataSetLabel).toUri();
            log.info("assembled URI string to get list of data sets = {}", uriToCall.toString());
            return getFromUri(uriToCall);
        } else {
            // todo : think about whether / how to differentiate by province in current design
            return getFromDynamo(dataSetLabel, "info_sets", false);
        }
    }

    @Override
    public List<String> retrieveRecordsForProvince(String dataSetLabel, String infoSetTag, Province province, Locale locale) {
        if (!useDynamoDirect) {
            URI uriToCall = baseBuilder()
                    .path("/records/{dataset}/{infoSet}")
                    .queryParam("province", province.toString())
                    .queryParam("locale", locale.toLanguageTag())
                    .buildAndExpand(dataSetLabel, infoSetTag).toUri();

            log.info("assembled URI to get records = {}", uriToCall.toString());

            return getFromUri(uriToCall);
        } else {
            DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
            Table geoApiTable = dynamoDB.getTable("geo_" + dataSetLabel.toLowerCase());
            log.info("querying from table, name: {}, number of rows: {}",
                    "geo_" + dataSetLabel.toLowerCase(), geoApiTable.describe().toString());
            log.info("looking for province, with name: {}, on infoSet: {}", province.name(), infoSetTag);

            Index provinceIndex = geoApiTable.getIndex("lowestGeoInfo"); // figure out how to adapt when province != lowest
            QuerySpec querySpec = new QuerySpec()
                    .withKeyConditionExpression("province = :prv and infoTag = :info")
                    .withValueMap(new ValueMap()
                            .withString(":prv", province.name())
                            .withString(":info", infoSetTag));

            try {
                ItemCollection<QueryOutcome> records = provinceIndex.query(querySpec);
                List<String> result = new ArrayList<>();
                records.iterator().forEachRemaining(i -> result.add(i.getString("description")));
                log.info("iterated through the results, number of results: {}", result.size());
                return result;
            } catch (Exception e) {
                log.error("Error!", e);
                throw new IllegalArgumentException("No results for that dataset, province and field");
            }
        }
    }

    @Async
    @Override
    @Transactional
    public void assembleAndSendRecordMessage(String dataSetLabel, String infoSetTag, Province province,
                                             String targetUid) {
        final Set<String> accountUids = getSponsoringAccountUids(dataSetLabel);
        if (accountUids == null || accountUids.isEmpty()) {
            log.info("error, message sending called for dataset {}, without sponsoring account", dataSetLabel);
        } else {
            User user = userRepository.findOneByUid(targetUid);
            List<String> records = retrieveRecordsForProvince(dataSetLabel, infoSetTag, province, user.getLocale());
            String message = dataSetLabel + " " + infoSetTag + String.join(", ", records);
            List<Account> accounts = accountRepository.findByUidIn(accountUids);
            if (accounts != null && !accounts.isEmpty()) {
                // todo : split among sponsoring accounts
                AccountLog accountLog = new AccountLog.Builder(accounts.get(0))
                        .user(user)
                        .accountLogType(AccountLogType.GEO_API_MESSAGE_SENT)
                        .description(message.substring(0, Math.min(255, message.length()))).build();
                accountLogRepository.saveAndFlush(accountLog);
                Set<Notification> messages = notificationsFromRecords(records, user, accountLog, 160);
                log.info("generated messages to send out, in total {} messages", messages.size());
                notificationRepository.save(messages);
            }
        }
    }

    private Set<Notification> notificationsFromRecords(List<String> records, User target,
                                                       AccountLog accountLog, int singleMessageLength) {
        int countChars = String.join(", ", records).length();
        if (countChars < singleMessageLength) {
            return Collections.singleton(new FreeFormMessageNotification(target, String.join(", ", records), accountLog));
        } else {
            Set<Notification> notifications = new HashSet<>();
            StringBuilder currentMsg = new StringBuilder(records.get(0));
            for (String r : records) {
                if ((currentMsg.length() + r.length() + 2) < singleMessageLength) {
                    currentMsg.append("; ").append(r);
                } else {
                    notifications.add(new FreeFormMessageNotification(target, currentMsg.toString(), accountLog));
                    currentMsg = new StringBuilder(r);
                }
            }
            return notifications;
        }
    }

    // consider in time altering this to persist it
    private Set<String> getSponsoringAccountUids(final String dataSet) {
        if (!useDynamoDirect) {
            URI uriToCall = baseBuilder()
                    .path("/account/{dataset}")
                    .buildAndExpand(dataSet).toUri();
            log.info("retrieving sponsoring account for dataset {}, with URL {}", dataSet, uriToCall);
            try {
                ResponseEntity<String[]> accountResponse = restTemplate.getForEntity(uriToCall, String[].class);
                return new HashSet<String>(Arrays.asList(accountResponse.getBody()));
            } catch (RestClientException e) {
                return null;
            }
        } else {
            DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
            Table geoApiTable = dynamoDB.getTable("geo_apis");
            GetItemSpec spec = new GetItemSpec()
                    .withPrimaryKey("data_set_label", dataSet)
                    .withProjectionExpression("account_uids");
            try {
                Item outcome = geoApiTable.getItem(spec);
                log.info("got the outcome, looks like: {}", outcome);
                return outcome != null ? outcome.getStringSet("account_uids") : new HashSet<>();
            } catch (Exception e) {
                log.error("Error getting account UIDs: {}", e);
                return null;
            }
        }
    }

    private List<String> getFromUri(URI uri) {
        try {
            ResponseEntity<String[]> availableInfo = restTemplate.getForEntity(uri, String[].class);
            return Arrays.asList(availableInfo.getBody());
        } catch (RestClientException e) {
            log.error("error calling geo API!", e);
            return new ArrayList<>();
        }
    }

    private List<String> getFromDynamo(String dataSetLabel, String field, boolean sort) {
        DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
        Table geoApiTable = dynamoDB.getTable("geo_apis");
        GetItemSpec spec = new GetItemSpec()
                .withPrimaryKey("data_set_label", dataSetLabel)
                .withProjectionExpression(field);
        try {
            log.info("trying to read the data set info with key {}, field {}", dataSetLabel, field);
            Item outcome = geoApiTable.getItem(spec);
            log.info("got the outcome, looks like: {}", outcome);
            Set<String> resultSet = outcome.getStringSet(field);
            return resultSet == null ? new ArrayList<>() :
                    sort ? resultSet.stream().sorted().collect(Collectors.toList()) : new ArrayList<>(resultSet);
        } catch (Exception e) {
            log.error("Error!", e);
            throw new IllegalArgumentException("No results for that dataset and field");
        }
    }
}
