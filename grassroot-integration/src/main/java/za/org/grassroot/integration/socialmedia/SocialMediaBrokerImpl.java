package za.org.grassroot.integration.socialmedia;

import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    @Value("${grassroot.broadcast.mocksm.enabled:false}")
    private boolean mockSocialMediaBroadcasts;

    // note: to replace with single WebClient when up to Spring 5 (and in meantime fine tune this rest template given
    // heavy use!)
    private final RestTemplate restTemplate;
    private final JwtService jwtService;
    private final CacheManager cacheManager;

    public SocialMediaBrokerImpl(RestTemplate restTemplate, JwtService jwtService, CacheManager cacheManager) {
        this.restTemplate = restTemplate;
        this.jwtService = jwtService;
        this.cacheManager = cacheManager;
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

    @Override
    public ManagedPagesResponse getManagedPages(String userUid, String providerId) {
        ManagedPagesResponse response = searchCache(userUid, providerId);
        return response == null ? askService(userUid, providerId) : response;
    }

    private ManagedPagesResponse searchCache(String userUid, String providerId) {
        Cache cache = cacheManager.getCache("social_media_managed_pages");
        String cacheKey = providerId + "-" + userUid;
        Element element = cache.get(cacheKey);
        ManagedPagesResponse resultFromCache = element != null ? (ManagedPagesResponse) element.getObjectValue() : null;
        log.info("got anything from cache? key = {}, result = {}", cacheKey, resultFromCache);
        return resultFromCache;
    }

    private ManagedPagesResponse askService(String userUid, String providerId) {
        if (mockSocialMediaBroadcasts) {
            switch (providerId) {
                case "facebook": return mockFbPages();
                case "twitter": return mockTwitterAccount();
                default: return null;
            }
        } else {
            try {
                final URI uri = baseUri(userUid).path("/connect/status/pages/" + providerId).build().toUri();
                log.debug("getting user's managed pages, URI = {}", uri.toString());
                ResponseEntity<ManagedPagesResponse> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(jwtHeaders()), ManagedPagesResponse.class);
                return handleResponse(response, providerId);
            } catch (RestClientException e) {
                log.error("Error calling social media service! Exception header: {}", e.getMessage());
                return new ManagedPagesResponse();
            }
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
    public List<GenericPostResponse> postToFacebook(List<FBPostBuilder> posts) {
        log.info("posting to facebook, here is the post: {}", posts);
        List<GenericPostResponse> responses = new ArrayList<>();
        for (FBPostBuilder post : posts) {
            final URI uri = baseUri(post.getPostingUserUid()).path("/grassroot/post/facebook").build().toUri();
            HttpEntity<GenericPostRequest> entity = new HttpEntity<>(new GenericPostRequest(post, imageBaseUri), jwtHeaders());
            try {
                ResponseEntity<GenericPostResponse> response = restTemplate.exchange(uri, HttpMethod.POST, entity, GenericPostResponse.class);
                responses.add(handleResponse(response, "FB"));
            } catch (RestClientException e) {
                log.error("Error calling FB post!", e);
            }
        }
        return responses;
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

    // using this while we are still in alpha - as else a time drag to boot integration service locally etc - remove when done
    private ManagedPagesResponse mockFbPages() {
        ManagedPage mockPage = new ManagedPage();
        mockPage.setDisplayName("User FB page");
        mockPage.setProviderUserId("user");
        ManagedPage mockPage2 = new ManagedPage();
        mockPage2.setDisplayName("Org FB page");
        mockPage2.setProviderUserId("org");
        return new ManagedPagesResponse(true, Arrays.asList(mockPage, mockPage2));
    }

    private ManagedPagesResponse mockTwitterAccount() {
        ManagedPage mockAccount = new ManagedPage();
        mockAccount.setDisplayName("@testing");
        mockAccount.setProviderUserId("testing");
        return new ManagedPagesResponse(true, Collections.singletonList(mockAccount));
    }

}
