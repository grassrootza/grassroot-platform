package za.org.grassroot.integration.socialmedia;

import lombok.extern.slf4j.Slf4j;
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
import za.org.grassroot.integration.messaging.CreateJwtTokenRequest;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.messaging.JwtType;

import java.net.URI;

@Service @Slf4j
public class SocialMediaBrokerImpl implements SocialMediaBroker {

    @Value("${grassroot.integration.service.url:http://localhost}")
    private String integrationServiceUrl;

    @Value("${grassroot.integration.service.port:8085}")
    private Integer integrationServicePort;

    @Value("${grassroot.integration.useruid.param:authuser_uid}")
    private String userUidParam;

    @Value("${grassroot.integration.image.baseurl:http://localhost/images/")
    private String imageBaseUri;


    // note: to replace with single WebClient when up to Spring 5
    private final RestTemplate restTemplate;
    private final AsyncRestTemplate asyncRestTemplate;
    private final JwtService jwtService;

    public SocialMediaBrokerImpl(RestTemplate restTemplate, AsyncRestTemplate asyncRestTemplate, JwtService jwtService) {
        this.restTemplate = restTemplate;
        this.asyncRestTemplate = asyncRestTemplate;
        this.jwtService = jwtService;
    }

    private UriComponentsBuilder baseUri(String userUid) {
        return UriComponentsBuilder.fromUriString(integrationServiceUrl).port(integrationServicePort)
                .queryParam(userUidParam, userUid);
    }

    private HttpHeaders jwtHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + jwtService.createJwt(new CreateJwtTokenRequest(JwtType.GRASSROOT_MICROSERVICE, null)));
        return headers;
    }

    @Override
    public boolean isFacebookPageConnected(String userUid) {
        return false;
    }

    @Override
    public ManagedPagesResponse getManagedFacebookPages(String userUid) {
        final URI uri = baseUri(userUid).path("/grassroot/post/facebook/pages").build().toUri();
        log.info("getting user's facebook pages, URI = {}", uri.toString());
        try {
            ResponseEntity<ManagedPagesResponse> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(jwtHeaders()), ManagedPagesResponse.class);
            return handleResponse(response, "FB");
        } catch (RestClientException e) {
            log.error("Error calling social media service! Exception looks like: {}", e);
            return null;
        }
    }

    @Override
    public GenericPostResponse postToFacebook(FBPostBuilder post) {
        log.info("posting to facebook, here is the post: {}", post);
        final URI uri = baseUri(post.getPostingUserUid()).path("/grassroot/post/facebook").build().toUri();
        HttpEntity<GenericPostRequest> entity = new HttpEntity<>(new GenericPostRequest(post, imageBaseUri), jwtHeaders());
        try {
            ResponseEntity<GenericPostResponse> response = restTemplate.exchange(uri, HttpMethod.POST, entity, GenericPostResponse.class);
            return handleResponse(response, "FB");
        } catch (RestClientException e) {
            log.error("Error calling FB post!", e);
            return null;
        }
    }

    @Override
    public boolean isTwitterAccountConnected(String userUid) {
        // todo : work through Spring Social to decipher
        return true;
    }

    @Override
    public GenericPostResponse postToTwitter(TwitterPostBuilder post) {
        final URI uri = baseUri(post.getPostingUserUid()).path("/grassroot/post/twitter").build().toUri();
        HttpEntity<GenericPostRequest> entity = new HttpEntity<>(new GenericPostRequest(post, imageBaseUri), jwtHeaders());
        try {
            ResponseEntity<GenericPostResponse> response = restTemplate.exchange(uri, HttpMethod.POST, entity, GenericPostResponse.class);
            return handleResponse(response, "Twitter");
        } catch (RestClientException e) {
            log.error("Error calling Twitter post!", e);
            return null;
        }
    }

    private <T extends ServiceResponse> T handleResponse(ResponseEntity<T> response, String provider) {
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        } else {
            log.error("Error! Could not complete call to {} provider, response: {}", provider, response);
            return null;
        }
    }
}
