package za.org.grassroot.webapp.controller.rest.integration;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.socialmedia.GenericPostRequest;
import za.org.grassroot.integration.socialmedia.IntegrationListResponse;
import za.org.grassroot.integration.socialmedia.ManagedPagesResponse;
import za.org.grassroot.integration.socialmedia.SocialMediaBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController @Grassroot2RestController
@Api("/api/integration/settings") @Slf4j
@RequestMapping(value = "/api/integration/settings")
public class IntegrationSettingsController extends BaseRestController {

    private final SocialMediaBroker socialMediaBroker;

    public IntegrationSettingsController(JwtService jwtService, UserManagementService userManagementService, SocialMediaBroker socialMediaBroker) {
        super(jwtService, userManagementService);
        this.socialMediaBroker = socialMediaBroker;
    }

    @RequestMapping(value = "/status/all", method = RequestMethod.GET)
    public IntegrationListResponse getAllCurrentConnections(HttpServletRequest request) {
        return socialMediaBroker.getCurrentIntegrations(getUserIdFromRequest(request));
    }

    @RequestMapping(value = "/connect/facebook/initiate", method = RequestMethod.GET)
    public ResponseEntity<String> initiateFbConnect(HttpServletRequest request) {
        String location = socialMediaBroker.initiateFacebookConnection(getUserIdFromRequest(request));
        log.info("also extracted host: {}", location);
        return ResponseEntity.ok(location);
    }

    @RequestMapping(value = "/connect/{providerId}/complete", method = RequestMethod.GET)
    public ResponseEntity<ManagedPagesResponse> completeFbConnect(HttpServletRequest request,
                                                                  @PathVariable String providerId) {
        // this gets much cleaner when we up to spring 5
        log.info("here is our parameter list: {}", request.getParameterMap());
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        Map<String, String[]> params = request.getParameterMap();
        params.forEach((key, value) -> {
            List<String> subValues = Arrays.asList(value);
            subValues.forEach(sv -> map.add(key, sv));
        });
        log.info("composed map: {}", map);
        return ResponseEntity.ok(socialMediaBroker.completeIntegrationConnect(getUserIdFromRequest(request), providerId, map));
    }

    @RequestMapping(value = "/connect/twitter/initiate", method = RequestMethod.GET)
    public ResponseEntity<String> initiateTwitterConnect(HttpServletRequest request) {
        String location = socialMediaBroker.initiateTwitterConnection(getUserIdFromRequest(request));
        log.info("extracted returned view for Twitter: {}", location);
        return ResponseEntity.ok(location);
    }

    @RequestMapping(value = "/remove/{providerId}", method = RequestMethod.POST)
    public IntegrationListResponse removeAccount(HttpServletRequest request, @PathVariable String providerId) {
        IntegrationListResponse response = socialMediaBroker.removeIntegration(getUserIdFromRequest(request), providerId);
        log.info("after removal, integrations: {}", response);
        return response;
    }

}
