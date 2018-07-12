package za.org.grassroot.integration.location;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component @Slf4j
public class LocationInfoBrokerImpl implements LocationInfoBroker {

    private static final String IZWE_LAMI_LABEL = "IZWE_LAMI_CONS";

    private final Environment environment;
    private final RestTemplate restTemplate;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountLogRepository accountLogRepository;
    private final NotificationRepository notificationRepository;

    private boolean useDynamoDirect;
    private AmazonDynamoDB dynamoDBClient;

    private String geoApiHost;
    private Integer geoApiPort;

    private String placeLookupLambda;
    private String izweLamiLambda;

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
        log.info("GeoAPI integration is active, setting up URLs, tables");

        useDynamoDirect = environment.getProperty("grassroot.geo.dynamodb.direct", Boolean.class, false);
        geoApiHost = environment.getProperty("grassroot.geo.apis.host", "localhost");
        geoApiPort = environment.getProperty("grassroot.geo.apis.port", Integer.class, 80);

        placeLookupLambda = environment.getProperty("grassroot.places.lambda.url", "http://localhost:3000");
        izweLamiLambda = environment.getProperty("grassroot.izwelami.lambda.url", "http://localhost:3001");

        if (useDynamoDirect) {
            log.info("Using dynamo db directly for geo APIs ...");
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
    public List<TownLookupResult> lookupPostCodeOrTown(String postCodeOrTown, Province province) {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(placeLookupLambda + "/lookup")
                    .queryParam("searchTerm", postCodeOrTown.trim());

            if (province != null) {
                uriBuilder.queryParam("province", Province.CANONICAL_NAMES_ZA.getOrDefault(province, ""));
            }

            ResponseEntity<TownLookupResult[]> lookupResult = restTemplate.getForEntity(uriBuilder.build().toUri(), TownLookupResult[].class);
            log.info("list: {}, lookup result: {}", Arrays.asList(lookupResult.getBody()), lookupResult);
            return Arrays.asList(lookupResult.getBody());
        } catch (RestClientException e) {
            log.error("Error constructing or executing lookup URL: ", e);
            return new ArrayList<>();
        }
    }

    @Override
    public TownLookupResult lookupPlaceDetails(String placeId) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(placeLookupLambda)
                    .pathSegment("details")
                    .pathSegment("{placeId}")
                    .buildAndExpand(placeId).toUri();
            log.info("calling url: {}", uri);
            ResponseEntity<TownLookupResult> responseEntity = restTemplate.getForEntity(uri, TownLookupResult.class);
            log.info("found place: {}", responseEntity.getBody());
            return responseEntity.getBody();
        } catch (RestClientException e) {
            log.error("Error constructing or executing lookup URL: {}", e);
            return null;
        }
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
                    .map(s -> s.startsWith("ZA_") ? s : "ZA_" + s)
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
    public Map<String, String> getAvailableInfoAndLowestLevelForDataSet(String dataSetLabel) {
        List<String> infoSets = getFromDynamo(dataSetLabel, "info_set_geo_level", true);
        return infoSets.stream().map(s -> s.replaceAll("([0-9]_)", ""))
                .peek(s -> log.info("replaced string: {}", s))
                .collect(Collectors.toMap(
                        s -> s.substring(0, s.indexOf("_")), s -> s.substring(s.indexOf("_") + 1, s.length())));
    }

    @Override
    public List<String> retrieveRecordsForProvince(String dataSetLabel, String infoSetTag, Province province, Locale locale) {
        if (!useDynamoDirect) {
            URI uriToCall = baseBuilder()
                    .path("/records/{dataset}/{infoSet}")
                    .queryParam("province", province.toString().substring("ZA_".length()))
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
                            .withString(":prv", province.name().substring("ZA_".length()))
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
            log.info("retrieved records from geo api, looks like: {}", records);
            String logMessage = dataSetLabel + " " + infoSetTag + String.join(", ", records);
            sendAndLogRecords(dataSetLabel, records, accountUids, logMessage, user);

        }
    }

    @Async
    @Override
    public void assembleAndSendForPlace(String dataSetLabel, String infoSetTag, String placeId, String targetUserUid) {
        TownLookupResult place = lookupPlaceDetails(placeId);
        final Set<String> accountUids = getSponsoringAccountUids(dataSetLabel);
        if (accountUids == null || accountUids.isEmpty())
            throw new IllegalArgumentException("Error! Dataset without sponsoring account called");

        log.info("data set {}, info set {}, place ID {}", dataSetLabel, infoSetTag, placeId);
        if (IZWE_LAMI_LABEL.equals(dataSetLabel)) {
            assembleAndSendHealthClinics(place, targetUserUid, accountUids);
        } else {
            throw new IllegalArgumentException("Error! Unsupported data set for place lookup");
        }
    }

    @Override
    public List<String> getDatasetLabelsForAccount(String accountUid) {
        return getDataSetsForAccount(accountUid);
    }

    private void assembleAndSendHealthClinics(TownLookupResult place, String targetUserUid, Set<String> accountUids) {
        UriComponentsBuilder componentsBuilder = UriComponentsBuilder.fromHttpUrl(izweLamiLambda)
                .path("/closest")
                .queryParam("latitude", place.getLatitude())
                .queryParam("longitude", place.getLongitude())
                .queryParam("size", 5);
        ResponseEntity<RangedInformation[]> results = restTemplate.getForEntity(componentsBuilder.build().toUri(), RangedInformation[].class);
        List<String> records = Arrays.stream(results.getBody())
                .sorted(Comparator.comparing(RangedInformation::getDistance))
                .map(RangedInformation::getInformation).collect(Collectors.toList());
        log.info("okay, got these results: {}, and records: {}", results.getBody(), records);

        if (!records.isEmpty()) {
            User targetUser = userRepository.findOneByUid(targetUserUid);
            sendAndLogRecords(IZWE_LAMI_LABEL, records, accountUids, "Place lookup results for Izwe Lami HCFs", targetUser);
        }
    }

    private void sendAndLogRecords(String dataSet, List<String> records, Set<String> accountUids, String logMessage, User user) {
        List<Account> accounts = accountRepository.findByUidIn(accountUids);
        if (accounts != null && !accounts.isEmpty()) {
            AccountLog accountLog = new AccountLog.Builder(accounts.get(0))
                    .user(user)
                    .accountLogType(AccountLogType.GEO_API_MESSAGE_SENT)
                    .description(logMessage.substring(0, Math.min(255, logMessage.length()))).build();
            accountLogRepository.saveAndFlush(accountLog);
            Set<Notification> messages = notificationsFromRecords(dataSet, records, user, accountLog, 160);
            log.info("generated messages to send out, in total {} messages", messages.size());
            notificationRepository.saveAll(messages);
        }
    }

    private Set<Notification> notificationsFromRecords(String dataSet, List<String> records, User target,
                                                       AccountLog accountLog, int singleMessageLength) {
        Set<Notification> notifications = new HashSet<>();

        final String openingMessage = getOpeningNote(dataSet, target.getLocale());
        boolean hasOpeningMessage = openingMessage != null;

        if (hasOpeningMessage) {
            log.info("Have an opening message for records: {}", openingMessage);
            Notification opening = new FreeFormMessageNotification(target, openingMessage, accountLog);
            notifications.add(opening);
        }

        List<Notification> infoMessages = new ArrayList<>();
        int countChars = String.join(", ", records).length();
        log.info("okay, have this many chars for records: {}", countChars);

        if (countChars < singleMessageLength) {
            infoMessages.add(new FreeFormMessageNotification(target, String.join(", ", records), accountLog));
        } else {
            StringBuilder currentMsg = new StringBuilder();
            log.debug("initiated, first message: {}", currentMsg);
            for (String r : records) {
                if ((currentMsg.length() + r.length() + 2) < singleMessageLength) {
                    final String separator = currentMsg.length() > 0 ? "; " : "";
                    currentMsg.append(separator).append(r);
                    log.debug("continuing assembly, new message: {}", currentMsg);
                } else {
                    log.info("appended message: {}, creating new one", currentMsg.toString());
                    infoMessages.add(new FreeFormMessageNotification(target, currentMsg.toString(), accountLog));
                    currentMsg = new StringBuilder(r);
                }
            }
            if (currentMsg.length() > 0) {
                log.info("adding final message: {}", currentMsg);
                infoMessages.add(new FreeFormMessageNotification(target, currentMsg.toString(), accountLog));
            }
        }

        if (hasOpeningMessage) {
            infoMessages.forEach(n -> n.setSendOnlyAfter(Instant.now().plus(15, ChronoUnit.SECONDS)));
        }

        notifications.addAll(infoMessages);

        final String finalMessage = getFinalMessage(dataSet, target.getLocale());
        if (!StringUtils.isEmpty(finalMessage)) {
            Notification finalNotification = new FreeFormMessageNotification(target, finalMessage, accountLog);
            finalNotification.setSendOnlyAfter(Instant.now().plus(hasOpeningMessage ? 30 : 15, ChronoUnit.SECONDS));
            notifications.add(finalNotification);
        }

        return notifications;
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
                return new HashSet<>(Arrays.asList(accountResponse.getBody()));
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

    private List<String> getDataSetsForAccount(final String accountUid) {
        DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);

        ScanRequest scanRequest = new ScanRequest()
                .withTableName("geo_apis");

        // would be nicer to do this with a filter expression but AWS SDK docs are a complete disaster so not clear at
        // all how to do a contains query
        log.info("Scanning for data sets matching account uid: {}", accountUid);
        try {
            ScanResult result = dynamoDBClient.scan(scanRequest);
            List<String> dataSets = getDataSetsFromResults(result, accountUid);
            log.info("Finished, data sets: {}", dataSets);
            return dataSets;
        } catch (AmazonServiceException e) {
            log.error("Error getting data sets: ", e);
            return new ArrayList<>();
        }
    }

    private List<String> getDataSetsFromResults(ScanResult result, String accountUid) {
        return result.getItems() == null ? new ArrayList<>() : result.getItems().stream()
                .filter(item -> {
                    List<String> accountUids = item.get("account_uids").getSS();
                    return accountUids != null && accountUids.contains(accountUid);
                })
                .map(item -> item.get("data_set_label").getS()).collect(Collectors.toList());
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
        } catch (AmazonServiceException e) {
            log.error("Error!", e);
            throw new IllegalArgumentException("No results for that dataset and field");
        }
    }

    private String getOpeningNote(String dataSetLabel, Locale locale) {
        return getFromSpec("opening_messages", dataSetLabel, locale);
    }

    private String getFinalMessage(String dataSetLabel, Locale locale) {
        return getFromSpec("final_messages", dataSetLabel, locale);
    }

    private String getFromSpec(String fieldName, String dataSetLabel, Locale locale) {
        DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
        Table geoApiTable = dynamoDB.getTable("geo_apis");
        GetItemSpec spec = new GetItemSpec()
                .withPrimaryKey("data_set_label", dataSetLabel)
                .withProjectionExpression(fieldName);
        try {
            log.info("getting {} messages for dataset : {} and locale : {}", fieldName, dataSetLabel, locale);
            Item outcome = geoApiTable.getItem(spec);
            log.info("result: {}", outcome);
            Map<String, String> results = outcome.getMap(fieldName);
            return results == null || results.isEmpty() ? null :
                    locale != null && results.containsKey(locale.getLanguage())
                            ? results.get(locale.getLanguage()) : results.getOrDefault("en", null);
        } catch (AmazonServiceException e) {
            log.error("Could not retrieve final messages");
            return null;
        }
    }
}
