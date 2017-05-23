package za.org.grassroot.services.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GcmRegistrationRepository;
import za.org.grassroot.core.repository.GroupChatSettingsRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;

import java.io.IOException;
import java.util.*;

import static org.springframework.http.HttpMethod.POST;

/**
 * Created by paballo on 2016/04/05.
 */
@Service
public class GcmRegistrationBrokerImpl implements GcmRegistrationBroker {

    private static final Logger log = LoggerFactory.getLogger(GcmRegistrationBrokerImpl.class);

    private final UserRepository userRepository;
    private final GcmRegistrationRepository gcmRegistrationRepository;
    private final GroupRepository groupRepository;
    private final RestTemplate restTemplate;
    private final GroupChatSettingsRepository groupChatSettingsRepository;

    private final static String INSTANCE_ID_FIXED_PATH = "/iid/v1/";

    @Value("${gcm.topics.url:iid.googleapis.com}") private String INSTANCE_ID_SERVICE_GATEWAY;
    @Value("${gcm.sender.key:12345}") private String AUTH_KEY;
    @Value("${gcm.topics.authorization:Authorization}") private String HEADER_AUTH;
    @Value("${gcm.topics.max.retries:3}") private int MAX_RETRIES;
    @Value("${gcm.topics.backoff.initial.delay:1000}") private int BACKOFF_INITIAL_DELAY ;
    @Value("${gcm.topics.backoff.max.delay:60000}") private int MAX_BACKOFF_DELAY;
    @Value("${gcm.topics.destination:to}") private String DESTINATION;
    @Value("${gcm.topics.tokens:registration_tokens}") private String REGISTRATION_TOKENS;
    @Value("${gcm.topics.batch.remove:':batchRemove'}") private String BATCH_REMOVE;
    @Value("${gcm.topics.batch.add:':batchAdd'}") private String BATCH_ADD;
    @Value("${gcm.topics.path:/topics/}") private String TOPICS;

    private static final ObjectMapper mapper = new ObjectMapper();
    private final static Random random = new Random();

    @Autowired
    public GcmRegistrationBrokerImpl(UserRepository userRepository, GcmRegistrationRepository gcmRegistrationRepository, GroupRepository groupRepository, RestTemplate restTemplate, GroupChatSettingsRepository groupChatSettingsRepository) {
        this.userRepository = userRepository;
        this.gcmRegistrationRepository = gcmRegistrationRepository;
        this.groupRepository = groupRepository;
        this.restTemplate = restTemplate;
        this.groupChatSettingsRepository = groupChatSettingsRepository;
    }

    @Override
    @Transactional
    public GcmRegistration registerUser(User user, String registrationId) {
        // todo : periodic cleaning of duplicate gcm registrations
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findTopByUserOrderByCreationTimeDesc(user);
        if (gcmRegistration != null) {
            gcmRegistration.setRegistrationId(registrationId);
        } else {
            gcmRegistration = new GcmRegistration(user, registrationId);
        }

        return gcmRegistrationRepository.save(gcmRegistration);
    }

    @Async
    @Override
    @Transactional
    public void refreshAllGroupTopicSubscriptions(String userUid, final String registrationId) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(registrationId);

        User user = userRepository.findOneByUid(userUid);
        List<Group> groupsPartOf = groupRepository.findByMembershipsUserAndActiveTrue(user);
        for (Group group : groupsPartOf) {
            try {
                GroupChatSettings settings = groupChatSettingsRepository.findByUserAndGroup(user, group);
                changeTopicSubscription(user.getUid(), group.getUid(), true);
                if (settings == null || !settings.isCanReceive()) {
                    GroupChatSettings thisGroupSettings = new GroupChatSettings(user, group, true, true, true, true);
                    groupChatSettingsRepository.saveAndFlush(thisGroupSettings);
                }
            } catch (DataIntegrityViolationException e) {
                log.error("Error storing group chat settings, possibly due to async loop");
            } catch (IOException e) {
                log.info("IO exception in loop ... GCM connection must be down");
                e.printStackTrace();
            }
        }
        log.info("Finished doing the registration");
    }

    @Override
    @Transactional
    public void unregisterUser(User user) {
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findTopByUserOrderByCreationTimeDesc(user);
        List<Group> groupsPartOf = groupRepository.findByMembershipsUserAndActiveTrue(user);
        for (Group group : groupsPartOf) {
            try {
                unsubscribeFromTopic(gcmRegistration.getRegistrationId(), group.getUid());
            } catch (Exception ignored) {
            }
        }
        gcmRegistrationRepository.delete(gcmRegistration);
    }

    @Async
    @Override
    public void changeTopicSubscription(String userUid, String topicId, boolean subscribe) throws IOException {
        User user = userRepository.findOneByUid(userUid);
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findTopByUserOrderByCreationTimeDesc(user);
        String registrationId = gcmRegistration != null ? gcmRegistration.getRegistrationId() : null;
        if (registrationId != null) {
            if (subscribe) {
                subscribeToTopic(registrationId, topicId);
            } else {
                unsubscribeFromTopic(registrationId, topicId);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasGcmKey(User user){
        return gcmRegistrationRepository.findTopByUserOrderByCreationTimeDesc(user) != null;
    }

    private void subscribeToTopic(String registrationId, String topicId) throws IOException {
        UriComponentsBuilder gatewayURI = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(INSTANCE_ID_SERVICE_GATEWAY)
                .path(INSTANCE_ID_FIXED_PATH)
                .path(registrationId)
                .path("/rel" + TOPICS)
                .path(topicId);

        int noAttempts = 0;
        int backoff = BACKOFF_INITIAL_DELAY;
        boolean retry;

        ResponseEntity<String> response = null;
        do {
            noAttempts++;
            // todo : work out why this is so slow (~ 3 secs ... seems like it's not pooling, which is strange)
            try {
                response = restTemplate.exchange(gatewayURI.build().toUri(), POST,
                        new HttpEntity<String>(getHttpHeaders()), String.class);
            } catch (HttpClientErrorException e) {
                log.error("Error calling group subscribe, with message: {}, and path: {}", e.toString(), gatewayURI.build().toString());
                if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                    removeStaleRegistration(registrationId);
                    break;
                }
                if (e.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
                    break;
                }
            }
            retry = (response == null || !response.getStatusCode().is2xxSuccessful()) && noAttempts <= MAX_RETRIES;
            if (retry) {
                backoff = exponentialBackoffSleep(backoff);
            }
        } while (retry);

        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            log.error("Could not send subscribe user after " + noAttempts + " attempts");
        }
    }

    @Transactional
    private void removeStaleRegistration(final String registrationId) {
        GcmRegistration registration = gcmRegistrationRepository.findByRegistrationId(registrationId);
        if (registration != null) {
            gcmRegistrationRepository.delete(registration);
        }
    }

    private void unsubscribeFromTopic(String registrationId, String topicId) throws IOException {
        UriComponentsBuilder gatewayURI = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(INSTANCE_ID_SERVICE_GATEWAY)
                .path(INSTANCE_ID_FIXED_PATH.concat(BATCH_REMOVE));

        int noAttempts = 0;
        int backoff = BACKOFF_INITIAL_DELAY;
        boolean retry;

        ResponseEntity<String> response;
        String topicName = TOPICS.concat(topicId);
        Map<String, Object> body = new HashMap<>();
        List<String> registrationTokens = Collections.singletonList(registrationId);
        body.put(DESTINATION, topicName);
        body.put(REGISTRATION_TOKENS, registrationTokens);
        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(body), getHttpHeaders());

        do {
            noAttempts++;
            response = restTemplate.exchange(gatewayURI.build().toUri(), POST, entity, String.class);
            retry = (!response.getStatusCode().is2xxSuccessful() && noAttempts <= MAX_RETRIES);
            if (retry) {
                backoff = exponentialBackoffSleep(backoff);
            }
        } while (retry);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IOException("Could not unsubscribe user after " + noAttempts + " attempts");
        }
    }

    private HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_AUTH, "key=".concat(AUTH_KEY));
        return headers;
    }

    private int exponentialBackoffSleep(int backoff) {
        try {
            int sleepTime = backoff / 2 + random.nextInt(backoff);
            Thread.sleep(sleepTime);
            if (2 * backoff < MAX_BACKOFF_DELAY) {
                backoff *= 2;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return backoff;
    }
}