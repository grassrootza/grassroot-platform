package za.org.grassroot.webapp.controller.rest.integration;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.socialmedia.FacebookAccount;
import za.org.grassroot.integration.socialmedia.SocialMediaBroker;
import za.org.grassroot.integration.socialmedia.TwitterAccount;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController @Grassroot2RestController
@Api("/v2/api/integration/settings") @Slf4j
@RequestMapping(value = "/v2/api/integration/settings")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class IntegrationSettingsController extends BaseRestController {

    private final SocialMediaBroker socialMediaBroker;

    public IntegrationSettingsController(JwtService jwtService, UserManagementService userManagementService, SocialMediaBroker socialMediaBroker) {
        super(jwtService, userManagementService);
        this.socialMediaBroker = socialMediaBroker;
    }

    @RequestMapping(value = "/status/facebook", method = RequestMethod.GET)
    public List<FacebookAccount> getFacebookAccounts(HttpServletRequest request) {
        return socialMediaBroker.getFacebookPages(getUserIdFromRequest(request));
    }

    @RequestMapping(value = "/status/twitter", method = RequestMethod.GET)
    public TwitterAccount getTwitterAccount(HttpServletRequest request) {
        return socialMediaBroker.isTwitterAccountConnected(getUserIdFromRequest(request));
    }

    @RequestMapping(value = "/connect/facebook/initiate", method = RequestMethod.GET)
    public ResponseEntity<String> initiateFbConnect(HttpServletRequest request) {
        String location = socialMediaBroker.initiateFacebookConnection(getUserIdFromRequest(request));
        log.info("also extracted host: {}", location);
        return ResponseEntity.ok(location);
    }

    @RequestMapping(value = "/connect/facebook/complete", method = RequestMethod.GET)
    public ResponseEntity<List<FacebookAccount>> completeFbConnect(HttpServletRequest request,
                                                                   @RequestParam String code) {
        return ResponseEntity.ok(socialMediaBroker.completeFbConnection(getUserIdFromRequest(request), code));
    }

    @RequestMapping(value = "/connect/twitter/initiate", method = RequestMethod.GET)
    public ResponseEntity<String> initiateTwitterConnect(HttpServletRequest request) {
        String location = socialMediaBroker.initiateTwitterConnection(getUserIdFromRequest(request));
        log.info("extracted returned view for Twitter: {}", location);
        return ResponseEntity.ok(location);
    }

    @RequestMapping(value = "/connect/twitter/complete", method = RequestMethod.GET)
    public ResponseEntity<TwitterAccount> completeTwitterAccount(HttpServletRequest request,
                                                                 @RequestParam String oauth_token,
                                                                 @RequestParam String oauth_verifier) {
        return ResponseEntity.ok(socialMediaBroker.completeTwitterConnection(getUserIdFromRequest(request), oauth_token, oauth_verifier));
    }

    @RequestMapping(value = "/remove/{providerId}", method = RequestMethod.POST)
    public ResponseEntity<Boolean> removeAccount(HttpServletRequest request, @PathVariable String providerId) {
        boolean removed = socialMediaBroker.removeIntegration(getUserIdFromRequest(request), providerId);
        log.info("after removal, integrations: {}", removed);
        return ResponseEntity.ok(removed);
    }

}
