package za.org.grassroot.integration.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GcmRegistrationRepository;
import za.org.grassroot.core.repository.GroupRepository;

import java.io.IOException;
import java.util.*;

import static org.springframework.http.HttpMethod.POST;

/**
 * Created by paballo on 2016/04/05.
 */
@Service
public class GcmManager implements GcmService {

    private static final Logger log = LoggerFactory.getLogger(GcmManager.class);

    @Autowired
    private GcmRegistrationRepository gcmRegistrationRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private RestTemplate restTemplate;

    private final static String INSTANCE_ID_SERVICE_GATEWAY = "iid.googleapis.com";
    private final static String AUTH_KEY = System.getenv("GCM_KEY");
    private final static String HEADER_AUTH = "Authorization";
    private final static int MAX_RETRIES = 3;
    private final static int BACKOFF_INITIAL_DELAY = 1000;
    private final static int MAX_BACKOFF_DELAY = 60 * 1000;
    private final static Random random = new Random();
    private final static String DESTINATION = "to";
    private final static String REGISTRATION_TOKENS = "registration_tokens";
    private final static String BATCH_REMOVE= ":batchRemove";



    private static final ObjectMapper mapper = new ObjectMapper();


    @Override
    @Transactional(readOnly = true)
    public GcmRegistration load(String uid) {
        return gcmRegistrationRepository.findByUid(uid);
    }

    @Override
    @Transactional(readOnly = true)
    public GcmRegistration loadByRegistrationId(String registrationId) {
        return gcmRegistrationRepository.findByRegistrationId(registrationId);
    }

    @Override
    @Transactional(readOnly = true)
    public String getGcmKey(User user) {
        return gcmRegistrationRepository.findByUser(user).getRegistrationId();
    }

    @Override
    @Transactional
    public GcmRegistration registerUser(User user, String registrationId) {
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findByUser(user);
        if (gcmRegistration != null) {
            gcmRegistration.setRegistrationId(registrationId);
        } else {
            gcmRegistration = new GcmRegistration(user, registrationId);
        }
        List<Group> groupsPartOf = groupRepository.findByMembershipsUserAndActiveTrue(user);
        for (Group group : groupsPartOf) {
            try {
                subscribeToTopic(registrationId, group.getUid());
            } catch (IOException ignored) {
            }
        }
        return gcmRegistrationRepository.save(gcmRegistration);
    }

    @Override
    @Transactional
    public void unregisterUser(User user) {
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findByUser(user);

        List<Group> groupsPartOf = groupRepository.findByMembershipsUserAndActiveTrue(user);
        for (Group group : groupsPartOf) {
            try {
                unsubScribeFromTopic(gcmRegistration.getRegistrationId(), group.getUid());
            } catch (Exception ignored) {
            }
        }

        gcmRegistrationRepository.delete(gcmRegistration);
    }

    @Override
    @Transactional
    @Async
    public void subscribeToTopic(String registrationId, String topicId) throws IOException {
       UriComponentsBuilder gatewayURI = UriComponentsBuilder.newInstance().scheme("https").host(INSTANCE_ID_SERVICE_GATEWAY);
       gatewayURI.path("/iid/v1").path(registrationId).path("/rel/topics/").path(topicId);

        int noAttempts = 0;
        int backoff = BACKOFF_INITIAL_DELAY;
        boolean retry;

        ResponseEntity<String> response;

        do {
            noAttempts++;
            response = restTemplate.exchange(gatewayURI.build().toUri(), POST,
                    new HttpEntity<String>(getHttpHeaders()),
                    String.class);


            log.info("uri" + gatewayURI.build().toString());
            log.info("response" + response);
            retry = (!response.getStatusCode().is2xxSuccessful() && noAttempts <= MAX_RETRIES);
            if (retry) {
                backoff = exponentialBackoffSleep(backoff);
            }
        }
        while (retry);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.info("error" + response.getBody());
            throw new IOException("Could not send message after " + noAttempts + " attempts");
        }

    }

    @Override
    @Transactional
    @Async
    public void unsubScribeFromTopic(String registrationId, String topicId) throws Exception {
        UriComponentsBuilder gatewayURI = UriComponentsBuilder.newInstance().scheme("https").host(INSTANCE_ID_SERVICE_GATEWAY
        ).path("/iid/v1".concat(BATCH_REMOVE));
        int noAttempts = 0;
        int backoff = BACKOFF_INITIAL_DELAY;
        boolean retry;

        ResponseEntity<String> response;

        String topicName = "/topics/".concat(topicId);
        Map<String, Object> body = new HashMap<>();
        List<String> registrationTokens = Collections.singletonList(registrationId);
        body.put(DESTINATION, topicName);
        body.put(REGISTRATION_TOKENS, registrationTokens);
        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(body), getHttpHeaders());
        do {
            noAttempts++;
            response = restTemplate.exchange(gatewayURI.build().toUri(), POST,
                    entity,
                    String.class);
            retry = (!response.getStatusCode().is2xxSuccessful() && noAttempts <= MAX_RETRIES);
            if (retry) {
                backoff = exponentialBackoffSleep(backoff);
            }
        }
        while (retry);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IOException("Could not send message after " + noAttempts + " attempts");
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
