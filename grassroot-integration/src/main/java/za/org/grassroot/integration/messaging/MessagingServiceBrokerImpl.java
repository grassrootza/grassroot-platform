package za.org.grassroot.integration.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Set;

/**
 * Created by luke on 2017/05/23.
 */
@Service
public class MessagingServiceBrokerImpl implements MessagingServiceBroker, CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MessagingServiceBrokerImpl.class);

    @Value("${grassroot.messaging.service.url:http://localhost}")
    private String messagingServiceUrl;

    @Value("${grassroot.messaging.service.port:8081}")
    private Integer messagingServicePort;

    private final RestTemplate restTemplate;
    private final AsyncRestTemplate asyncRestTemplate;
    private final JwtService jwtService;

    @Autowired
    public MessagingServiceBrokerImpl(RestTemplate restTemplate, AsyncRestTemplate asyncRestTemplate, JwtService jwtService) {
        this.restTemplate = restTemplate;
        this.asyncRestTemplate = asyncRestTemplate;
        this.jwtService = jwtService;
    }

    // using this instead of @PostConstruct because that runs too early
    @Override
    public void run(String... args) throws Exception {
        logger.info("HTTP servlet booted, telling messaging server to refresh keys");
        asyncRestTemplate.getForEntity(baseUri().path("/jwt/public/refresh/trusted").toUriString(), Boolean.class);
    }

    @Override
    public void sendSMS(String message, String destinationNumber, boolean userRequested) {
        String serviceCallUri = baseUri()
                .path("/notification/push/system/{destinationNumber}")
                .queryParam("message", message)
                .queryParam("userRequested", userRequested)
                .buildAndExpand(destinationNumber)
                .toUriString();
        asyncRestTemplate
                .exchange(
                        serviceCallUri,
                        HttpMethod.POST,
                        new HttpEntity<String>(jwtHeaders()),
                        String.class)
                .addCallback(
                        result -> logger.info("Success! Sent SMS async, via messaging services"),
                        ex -> logger.info("Error! Could not send SMS, failure: {}", ex.getMessage()));
    }

    @Override
    public MessageServicePushResponse sendPrioritySMS(String message, String phoneNumber) {
        URI serviceCallUri = baseUri()
                .path("/notification/push/priority/{phoneNumber}")
                .queryParam("message", message)
                .buildAndExpand(phoneNumber)
                .toUri();
        ResponseEntity<MessageServicePushResponse> responseEntity =
                restTemplate.exchange(
                        serviceCallUri,
                        HttpMethod.POST,
                        new HttpEntity<MessageServicePushResponse>(jwtHeaders()),
                        MessageServicePushResponse.class
                );
        return responseEntity.getBody();
    }

    @Override
    public void markMessagesAsRead(String groupUid, Set<String> messageUids) {
        URI serviceCallUri = baseUri()
                .path("/groupchat/mark_read/{groupUid}")
                .buildAndExpand(groupUid)
                .toUri();
        asyncRestTemplate.exchange(
                serviceCallUri,
                HttpMethod.POST,
                new HttpEntity<>(messageUids, jwtHeaders()),
                String.class
        );
    }

    @Override
    public void updateActivityStatus(String userUid, String groupUid, boolean active, boolean userInitiated) {
        URI serviceCallUri = baseUri()
                .pathSegment("/groupchat/update_activity/{userUid}")
                .queryParam("groupUid", groupUid)
                .queryParam("setActive", active)
                .queryParam("selfInitiated", userInitiated)
                .buildAndExpand(userUid)
                .toUri();
        asyncRestTemplate.exchange(
                serviceCallUri,
                HttpMethod.POST,
                new HttpEntity<String>(jwtHeaders()),
                String.class
        );
    }

    @Override
    public void subscribeServerToGroupChatTopic(String groupUid) {
        URI serviceCallUri = baseUri()
                .pathSegment("/groupchat/server_subscribe/{groupUid}")
                .buildAndExpand(groupUid)
                .toUri();
        asyncRestTemplate.exchange(
                serviceCallUri,
                HttpMethod.POST,
                new HttpEntity<String>(jwtHeaders()),
                String.class
        );
    }


    private UriComponentsBuilder baseUri() {
        return UriComponentsBuilder.fromUriString(messagingServiceUrl)
                .port(messagingServicePort);
    }

    // this means duplication but getting extreme weirdness on doing generic but
    private HttpHeaders jwtHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + jwtService.createJwt(new CreateJwtTokenRequest(JwtType.GRASSROOT_MICROSERVICE)));
        return headers;
    }

}
