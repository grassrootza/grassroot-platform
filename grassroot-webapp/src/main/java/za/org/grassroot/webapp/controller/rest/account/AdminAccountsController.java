package za.org.grassroot.webapp.controller.rest.account;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.integration.billing.BillingServiceBroker;
import za.org.grassroot.integration.billing.SubscriptionRecordDTO;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.model.rest.wrappers.AccountWrapper;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController @Grassroot2RestController @Slf4j
@Api("/v2/api/admin/accounts") @RequestMapping(value = "/v2/api/admin/accounts")
@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
public class AdminAccountsController extends BaseRestController {

    private final AccountBroker accountBroker;
    private final BillingServiceBroker billingServiceBroker;

    public AdminAccountsController(JwtService jwtService, UserManagementService userManagementService, AccountBroker accountBroker, BillingServiceBroker billingServiceBroker) {
        super(jwtService, userManagementService);
        this.accountBroker = accountBroker;
        this.billingServiceBroker = billingServiceBroker;
    }

    private AccountWrapper wrapAccount(HttpServletRequest request, String accountUid) {
        Account account = accountBroker.loadAccount(accountUid);
        AccountWrapper wrapper = new AccountWrapper(account, getUserFromRequest(request));
        wrapper.setNotificationsSinceLastBill(accountBroker.countAccountNotifications(accountUid, account.getLastBillingDate(), Instant.now()));
        return wrapper;
    }

    // this is heavy, but will be rarely used
    @RequestMapping(value = "/list/enabled", method = RequestMethod.GET)
    public ResponseEntity listAccounts(HttpServletRequest request) {
        return ResponseEntity.ok(accountBroker.loadAllAccounts(true).stream()
                .map(account -> wrapAccount(request, account.getUid())));
    }

    @RequestMapping(value = "/list/disabled", method = RequestMethod.GET)
    public ResponseEntity listDisabledAccounts(HttpServletRequest request) {
        return ResponseEntity.ok(accountBroker.loadDisabledAccountMap());
    }

    @RequestMapping(value = "/update/billing", method = RequestMethod.POST)
    public ResponseEntity updateLastBillingDate(HttpServletRequest request, @RequestParam String accountUid,
                                                @RequestParam long newLastBillingDateTimeMillis) {
        accountBroker.setLastBillingDate(getUserIdFromRequest(request), accountUid, Instant.ofEpochMilli(newLastBillingDateTimeMillis));
        return ResponseEntity.ok(wrapAccount(request, accountUid));
    }

    @RequestMapping(value = "/update/subscription", method = RequestMethod.POST)
    public ResponseEntity updateAccountSubscriptionRef(HttpServletRequest request, @RequestParam String accountUid,
                                                       @RequestParam String newSubscriptionRef) {
        accountBroker.setAccountSubscriptionRef(getUserIdFromRequest(request), accountUid, newSubscriptionRef);
        return ResponseEntity.ok(wrapAccount(request, accountUid));
    }

    @RequestMapping(value = "/enable/account", method = RequestMethod.POST)
    public ResponseEntity enableAccount(HttpServletRequest request, @RequestParam String accountUid,
                                        @RequestParam String logMessage) {
        accountBroker.enableAccount(getUserIdFromRequest(request), accountUid, logMessage);
        return ResponseEntity.ok(wrapAccount(request, accountUid));
    }

    @RequestMapping(value = "/disable/account", method = RequestMethod.POST)
    public ResponseEntity disableAccount(HttpServletRequest request, @RequestParam String accountUid,
                                         @RequestParam String logMessage) {
        accountBroker.disableAccount(getUserIdFromRequest(request), accountUid, logMessage);
        return ResponseEntity.ok(wrapAccount(request, accountUid));
    }

    /// these are for interacting with the Chargebee subscriptions - necessary in time, not right now, yet
    @RequestMapping(value = "/list/billing", method = RequestMethod.GET)
    public CompletableFuture<List<SubscriptionRecordDTO>> listSubscriptions(HttpServletRequest request) {
        return billingServiceBroker.fetchListOfSubscriptions(false, getJwtTokenFromRequest(request))
                .collectList().toFuture();
    }

    @RequestMapping(value = "/create/subscription", method = RequestMethod.POST)
    public CompletableFuture<SubscriptionRecordDTO> createAccount(HttpServletRequest request,
                                                                  @RequestParam String accountName,
                                                                  @RequestParam String billingEmail) {
        return billingServiceBroker.createSubscription(accountName, billingEmail, getJwtTokenFromRequest(request), false).toFuture();
    }

    @RequestMapping(value = "/enable/billing/{accountUid}", method = RequestMethod.POST)
    public CompletableFuture<SubscriptionRecordDTO> enableSubscription(HttpServletRequest request,
                                                                       @PathVariable String accountUid) {
        accountBroker.enableAccount(getUserIdFromRequest(request), accountUid, null);
        Account account = accountBroker.loadAccount(accountUid);
        return billingServiceBroker.enableSubscription(account.getSubscriptionRef(), getJwtTokenFromRequest(request)).toFuture();
    }

    @RequestMapping(value = "/cancel/billing/{accountUid}", method = RequestMethod.POST)
    public CompletableFuture<SubscriptionRecordDTO> disableAccount(HttpServletRequest request,
                                                                   @PathVariable String accountUid) {
        accountBroker.disableAccount(getUserIdFromRequest(request), accountUid, null);
        Account account = accountBroker.loadAccount(accountUid);
        return billingServiceBroker.cancelSubscription(account.getSubscriptionRef(), getJwtTokenFromRequest(request)).toFuture();
    }




}
