package za.org.grassroot.integration.location;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.notification.FreeFormMessageNotification;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.repository.AccountLogRepository;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.NotificationRepository;
import za.org.grassroot.core.repository.UserRepository;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

    private String geoApiHost;
    private Integer geoApiPort;

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
        geoApiHost = environment.getProperty("grassroot.geo.apis.host", "localhost");
        geoApiPort = environment.getProperty("grassroot.geo.apis.port", Integer.class, 80);
    }

    private UriComponentsBuilder baseBuilder() {
        return UriComponentsBuilder.newInstance()
                .scheme("http")
                .host(geoApiHost)
                .port(geoApiPort);
    }

    @Override
    public List<ProvinceSA> getAvailableProvincesForDataSet(String dataSetLabel) {
        URI uriToCall = baseBuilder()
                .path("/provinces/available/{dataset}")
                .buildAndExpand(dataSetLabel).toUri();

        log.info("finding available provinces for: {}", uriToCall);

        return getResult(uriToCall).stream().map(ProvinceSA::valueOf).collect(Collectors.toList());
    }

    @Override
    public List<Locale> getAvailableLocalesForDataSet(String dataSetLabel) {
        URI uriToCall = baseBuilder()
                .path("/languages/available/{dataset}")
                .buildAndExpand(dataSetLabel).toUri();

        log.info("finding available languages, using URI: {}", uriToCall);

        return getResult(uriToCall).stream().map(Locale::new).collect(Collectors.toList());
    }

    @Override
    public List<String> getAvailableInfoForProvince(String dataSetLabel, ProvinceSA province, Locale locale) {
        URI uriToCall = baseBuilder()
                .path("/sets/available/{dataset}")
                .queryParam("province", province.toString())
                .queryParam("locale", locale.toLanguageTag())
                .buildAndExpand(dataSetLabel).toUri();

        log.info("assembled URI string to get list of data sets = {}", uriToCall.toString());

        return getResult(uriToCall);
    }

    @Override
    public List<String> retrieveRecordsForProvince(String dataSetLabel, String infoSetTag, ProvinceSA province, Locale locale) {
        URI uriToCall = baseBuilder()
                .path("/records/{dataset}/{infoSet}")
                .queryParam("province", province.toString())
                .queryParam("locale", locale.toLanguageTag())
                .buildAndExpand(dataSetLabel, infoSetTag).toUri();

        log.info("assembled URI to get records = {}", uriToCall.toString());

        return getResult(uriToCall);
    }

    @Async
    @Override
    public void assembleAndSendRecordMessage(String dataSetLabel, String infoSetTag, ProvinceSA province,
                                             String targetUid) {
        final String accountUid = getSponsoringAccountUid(dataSetLabel);
        if (StringUtils.isEmpty(accountUid)) {
            log.info("error, message sending called for dataset {}, without sponsoring account", dataSetLabel);
        } else {
            User user = userRepository.findOneByUid(targetUid);
            List<String> records = retrieveRecordsForProvince(dataSetLabel, infoSetTag, province, user.getLocale());
            String message = dataSetLabel + " " + infoSetTag + String.join(", ", records);
            Account account = accountRepository.findOneByUid(accountUid);
            AccountLog accountLog = new AccountLog.Builder(account)
                    .user(user)
                    .accountLogType(AccountLogType.GEO_API_MESSAGE_SENT)
                    .description(message.substring(0, Math.min(255, message.length()))).build();
            accountLogRepository.saveAndFlush(accountLog);
            FreeFormMessageNotification notification = new FreeFormMessageNotification(user, message, accountLog);
            notificationRepository.saveAndFlush(notification);
        }
    }

    // consider in time altering this to persist it
    private String getSponsoringAccountUid(final String dataSet) {
        URI uriToCall = baseBuilder()
                .path("/account/{dataset}")
                .buildAndExpand(dataSet).toUri();

        log.info("retrieving sponsoring account for dataset {}, with URL {}", dataSet, uriToCall);

        try {
            ResponseEntity<String> accountResponse = restTemplate.getForEntity(uriToCall, String.class);
            return accountResponse.getBody();
        } catch (RestClientException e) {
            return null;
        }
    }


    private List<String> getResult(URI uri) {
        try {
            ResponseEntity<String[]> availableInfo = restTemplate.getForEntity(uri, String[].class);
            return Arrays.asList(availableInfo.getBody());
        } catch (RestClientException e) {
            log.error("error calling geo API!", e);
            return new ArrayList<>();
        }
    }
}
