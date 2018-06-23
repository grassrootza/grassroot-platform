package za.org.grassroot.webapp.controller.rest.admin;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.integration.billing.BillingServiceBroker;
import za.org.grassroot.integration.billing.SubscriptionRecordDTO;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController @Grassroot2RestController @Slf4j
@Api("/v2/api/admin/accounts") @RequestMapping(value = "/v2/api/admin/accounts")
@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
public class AdminAccountsController extends BaseRestController {

    private final BillingServiceBroker billingServiceBroker;

    public AdminAccountsController(JwtService jwtService, UserManagementService userManagementService, BillingServiceBroker billingServiceBroker) {
        super(jwtService, userManagementService);
        this.billingServiceBroker = billingServiceBroker;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public CompletableFuture<List<SubscriptionRecordDTO>> listSubscriptions(HttpServletRequest request) {
        return billingServiceBroker.fetchListOfSubscriptions(false, getJwtTokenFromRequest(request))
                .collectList().toFuture();
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public CompletableFuture<SubscriptionRecordDTO> createAccount(HttpServletRequest request,
                                                                  @RequestParam String accountName,
                                                                  @RequestParam String billingEmail) {
        return billingServiceBroker.createSubscription(accountName, billingEmail, getJwtTokenFromRequest(request)).toFuture();
    }

    @RequestMapping(value = "/enable/{subscriptionId}", method = RequestMethod.POST)
    public CompletableFuture<SubscriptionRecordDTO> enableSubscription(HttpServletRequest request, @PathVariable String subscriptionId) {
        return billingServiceBroker.enableSubscription(subscriptionId, getJwtTokenFromRequest(request)).toFuture();
    }

    @RequestMapping(value = "/cancel/{subscriptionId}", method = RequestMethod.POST)
    public CompletableFuture<SubscriptionRecordDTO> disableAccount(HttpServletRequest request, @PathVariable String subscriptionId) {
        return billingServiceBroker.cancelSubscription(subscriptionId, getJwtTokenFromRequest(request)).toFuture();
    }

}
