package za.org.grassroot.integration.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

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
    public void sendEmail(List<String> addresses, GrassrootEmail email) {

        UriComponentsBuilder builder = baseUri()
                .path("/email/send")
                .queryParam("addresses", addresses.toArray())
                .queryParam("subject", email.getSubject());

        if (email.hasAttachment()) {
            logger.info("we have an attachment, setting it here");
            builder = builder.queryParam("attachmentName", email.getAttachmentName());
        }

        if (email.hasHtmlContent()) {
            builder = builder.queryParam("content", email.getHtmlContent())
                    .queryParam("textContent", email.getContent());
        } else {
            builder = builder.queryParam("content", email.getContent());
        }

        if (!StringUtils.isEmpty(email.getFrom())) {
            builder = builder.queryParam("fromName", email.getFrom());
        }

        if (!StringUtils.isEmpty(email.getFromAddress())) {
            builder = builder.queryParam("fromAddress", email.getFromAddress());
        }

        HttpHeaders headers = jwtHeaders();
        LinkedMultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
        if (email.hasAttachment()) {
            params.add("attachment", new FileSystemResource(email.getAttachment()));
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        }
        HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> responseEntity =
                    restTemplate.exchange(builder.build().toUri(), HttpMethod.POST,
                            requestEntity, String.class);
            logger.info("what happened ? {}", responseEntity);
        } catch (RestClientException e) {
            logger.error("Error pushing out emails! {}", e);
        }

    }

    private UriComponentsBuilder baseUri() {
        return UriComponentsBuilder.fromUriString(messagingServiceUrl)
                .port(messagingServicePort);
    }

    // this means duplication but getting extreme weirdness on doing generic but
    private HttpHeaders jwtHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + jwtService.createJwt(new CreateJwtTokenRequest(JwtType.GRASSROOT_MICROSERVICE, null)));
        return headers;
    }

}
