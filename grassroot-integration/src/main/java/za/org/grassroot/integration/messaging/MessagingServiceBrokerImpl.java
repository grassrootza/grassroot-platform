package za.org.grassroot.integration.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.dto.GrassrootEmail;
import za.org.grassroot.integration.authentication.CreateJwtTokenRequest;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.authentication.JwtType;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by luke on 2017/05/23.
 */
@Service
public class MessagingServiceBrokerImpl implements MessagingServiceBroker {

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

    @Override
    public void sendSMS(String message, String userUid, boolean userRequested) {
        String serviceCallUri = baseUri()
                .path("/notification/push/system/{destinationNumber}")
                .queryParam("message", message)
                .queryParam("userRequested", userRequested)
                .buildAndExpand(userUid)
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
        try {
            ResponseEntity<MessageServicePushResponse> responseEntity =
                    restTemplate.exchange(
                            serviceCallUri,
                            HttpMethod.POST,
                            new HttpEntity<MessageServicePushResponse>(jwtHeaders()),
                            MessageServicePushResponse.class
                    );
            return responseEntity.getBody();
        } catch (Exception e) {
            logger.error("Error connecting to: {}", serviceCallUri);
            throw e;
        }
    }

    @Override
    public void sendEmail(Map<String, String> recipients, GrassrootEmail email) {
        UriComponentsBuilder builder = baseUri().path("/email/send");
        Set<GrassrootEmail> emails = new HashSet<>();
        recipients.forEach((address, name) -> emails.add(email.copyIntoNew(address, name)));
        HttpEntity<Set<GrassrootEmail>> requestEntity = new HttpEntity<>(emails, jwtHeaders());

        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(builder.build().toUri(), HttpMethod.POST,
                            requestEntity, String.class);
            logger.info("what happened ? {}", responseEntity);
        } catch (RestClientException e) {
            logger.error("Error pushing out emails! {}", e);
        }

    }

    @Override
    public boolean sendEmail(GrassrootEmail mail) {
        UriComponentsBuilder builder = baseUri().path("/email/send");
        HttpEntity<Set<GrassrootEmail>> requestEntity = new HttpEntity<>(Collections.singleton(mail), jwtHeaders());

        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(builder.build().toUri(), HttpMethod.POST,
                    requestEntity, String.class);
            logger.info("send email: ? {}", responseEntity);
            return true;
        } catch (RestClientException e) {
            logger.error("Error pushing out emails! {}", e);
            return false;
        }
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
