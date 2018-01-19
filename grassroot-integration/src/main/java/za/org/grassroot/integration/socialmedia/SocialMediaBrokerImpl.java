package za.org.grassroot.integration.socialmedia;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;
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


    // note: to replace with single WebClient when up to Spring 5 (and in meantime fine tune this rest template given
    // heavy use!)
    private final RestTemplate restTemplate;
    private final JwtService jwtService;

    public SocialMediaBrokerImpl(RestTemplate restTemplate, JwtService jwtService) {
        this.restTemplate = restTemplate;
        this.jwtService = jwtService;
    }

    private UriComponentsBuilder baseUri(String userUid) {
        // todo : use headers with JWT in production for all calls
        return UriComponentsBuilder.fromUriString(integrationServiceUrl).port(integrationServicePort)
                .queryParam(userUidParam, userUid);
    }

    private HttpHeaders jwtHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + jwtService.createJwt(new CreateJwtTokenRequest(JwtType.GRASSROOT_MICROSERVICE, null)));
        return headers;
    }

    @Override
    public IntegrationListResponse getCurrentIntegrations(String userUid) {
        IntegrationListResponse response = new IntegrationListResponse();
        response.addIntegration("facebook", getManagedPages(userUid, "facebook"));
        response.addIntegration("twitter", getManagedPages(userUid, "twitter"));
        response.addIntegration("google", getManagedPages(userUid, "google"));
        return response;
    }

    private ManagedPagesResponse getManagedPages(String userUid, String providerId) {
        final URI uri = baseUri(userUid).path("/connect/status/pages/" + providerId).build().toUri();
        log.debug("getting user's managed pages, URI = {}", uri.toString());
        try {
            ResponseEntity<ManagedPagesResponse> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(jwtHeaders()), ManagedPagesResponse.class);
            return handleResponse(response, providerId);
        } catch (RestClientException e) {
            log.error("Error calling social media service! Exception header: {}", e.getMessage());
            return new ManagedPagesResponse();
        }
    }

    @Override
    public ManagedPagesResponse getManagedFacebookPages(String userUid) {
        return getManagedPages(userUid, "facebook");
    }

    @Override
    public String initiateFacebookConnection(String userUid) {
        final URI uri = baseUri(userUid).path("/connect/facebook")
                .queryParam("scope", "user_friends,user_posts,manage_pages,publish_pages,publish_actions")
                .build().toUri();
        log.info("okay trying this out, URI = {}", uri.toString());
        return getRedirectUrl(uri);
    }

    @Override
    public String initiateTwitterConnection(String userUid) {
        final URI uri = baseUri(userUid).path("/connect/twitter")
                .build().toUri();
        log.info("okay trying to connect to twitter, URI = {}", uri.toString());
        return getRedirectUrl(uri);
    }

    @Override
    public IntegrationListResponse removeIntegration(String userUid, String providerId) {
        final URI uri = baseUri(userUid).path("/connect/" + providerId).build().toUri();
        log.info("okay, removing account: {}", uri.toString());
        try {
            restTemplate.exchange(uri, HttpMethod.DELETE, new HttpEntity<>(jwtHeaders()), String.class);
            log.info("successfully called delete connection, now getting latest set of connections");
            return getCurrentIntegrations(userUid);
        } catch (RestClientException e) {
            log.error("error calling integration broker", e);
            return null;
        }
    }


    @Override
    public ManagedPagesResponse completeIntegrationConnect(String userUid, String providerId, MultiValueMap<String, String> paramsToPass) {
        final URI uri = baseUri(userUid).path("/connect/" + providerId)
                .queryParams(paramsToPass)
                .build().toUri();
        try {
            log.info("calling URI: {}", uri);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(jwtHeaders()), String.class);
            log.info("response: {}", response);
            return getManagedFacebookPages(userUid);
        } catch(RestClientException e) {
            log.error("error calling integration broker", e);
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
    public ManagedPage isTwitterAccountConnected(String userUid) {
        final URI uri = baseUri(userUid).path("/connect/status/pages/twitter").build().toUri();
        try {
            ResponseEntity<ManagedPagesResponse> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(jwtHeaders()), ManagedPagesResponse.class);
            ManagedPagesResponse body = handleResponse(response, "Twitter");
            return body == null || body.managedPages.isEmpty() ? null : body.managedPages.get(0);
        } catch (RestClientException e) {
            log.error("Error trying to check Twitter account", e);
            return null;
        }
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

    private String getRedirectUrl(URI uri) {
        try {
            ResponseEntity<RedirectView> view = restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(jwtHeaders()),
                    RedirectView.class);
            log.info("and returnd view with headers = {} and url = {}", view.getHeaders(), view.getHeaders().get("Location"));
            return view.getHeaders().get("Location").get(0);
        } catch (RestClientException e) {
            log.error("error calling integration broker", e);
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
