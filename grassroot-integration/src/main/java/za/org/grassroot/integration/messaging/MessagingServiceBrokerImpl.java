package za.org.grassroot.integration.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import za.org.grassroot.core.dto.GrassrootEmail;
import za.org.grassroot.integration.authentication.CreateJwtTokenRequest;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.authentication.JwtType;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by luke on 2017/05/23.
 */
@Service @Slf4j
public class MessagingServiceBrokerImpl implements MessagingServiceBroker {

    private static final String AUTH_HEADER = "Authorization";

    @Value("${grassroot.messaging.service.url:http://localhost}")
    private String messagingServiceUrl;

    @Value("${grassroot.messaging.service.port:8081}")
    private Integer messagingServicePort;

    private final RestTemplate restTemplate;
    private final JwtService jwtService;

    private WebClient asyncWebClient;

    @Autowired
    public MessagingServiceBrokerImpl(RestTemplate restTemplate, JwtService jwtService) {
        this.restTemplate = restTemplate;
        this.jwtService = jwtService;
    }

    @PostConstruct
    public void init() {
        this.asyncWebClient = WebClient.builder()
                .baseUrl(baseUri().toUriString())
                .build();
    }

    @Override
    public void sendSMS(String message, String userUid, boolean userRequested) {
        asyncWebClient.post()
                .uri("/notification/push/system/{userUid}?message={message}&userRequested={userRequested}",
                        userUid, message, userRequested)
                .header(AUTH_HEADER, jwtHeader())
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, error -> {
                    log.error("Error! Client error, could not send SMS, failure: {}", error);
                    return Mono.empty();
                })
                .onStatus(HttpStatus::is5xxServerError, error -> {
                    log.error("Error, Server error, could not send SMS, failure: {}", error);
                    return Mono.empty();
                })
                .bodyToMono(String.class)
                .log()
                .subscribe();
    }

    @Override
    public MessageServicePushResponse sendPrioritySMS(String message, String phoneNumber) {
        log.info("Sending a priority SMS ...");
        URI serviceCallUri = baseUri()
                .path("/notification/push/priority/{phoneNumber}")
                .queryParam("message", message)
                .buildAndExpand(phoneNumber)
                .toUri();
        try {
            log.info("Calling: {}", serviceCallUri);
            ResponseEntity<MessageServicePushResponse> responseEntity =
                    restTemplate.exchange(
                            serviceCallUri,
                            HttpMethod.POST,
                            new HttpEntity<MessageServicePushResponse>(jwtHeaders()),
                            MessageServicePushResponse.class
                    );
            return responseEntity.getBody();
        } catch (Exception e) {
            log.error("Error connecting to: {}, error: {}", serviceCallUri, e.getMessage());
            return null;
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
            log.info("what happened ? {}", responseEntity);
        } catch (RestClientException e) {
            log.error("Error pushing out emails! {}", e);
        }

    }

    @Override
    public boolean sendEmail(GrassrootEmail mail) {
        UriComponentsBuilder builder = baseUri().path("/email/send");
        HttpEntity<Set<GrassrootEmail>> requestEntity = new HttpEntity<>(Collections.singleton(mail), jwtHeaders());

        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(builder.build().toUri(), HttpMethod.POST,
                    requestEntity, String.class);
            log.info("send email: ? {}", responseEntity);
            return true;
        } catch (RestClientException e) {
            log.error("Error pushing out emails! {}", e);
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

    private String jwtHeader() {
        return "Bearer " + jwtService.createJwt(new CreateJwtTokenRequest(JwtType.GRASSROOT_MICROSERVICE));
    }

}
