package za.org.grassroot.webapp.controller.rest.account;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.billing.BillingServiceBroker;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController @Grassroot2RestController
@RequestMapping("/v2/api/account") @Slf4j
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class AccountSignUpController extends BaseRestController {

    private final AccountBroker accountBroker;
    private final BillingServiceBroker billingServiceBroker;
    private final UserManagementService userService;

    public AccountSignUpController(JwtService jwtService, UserManagementService userManagementService, AccountBroker accountBroker, BillingServiceBroker billingServiceBroker) {
        super(jwtService, userManagementService);
        this.accountBroker = accountBroker;
        this.billingServiceBroker = billingServiceBroker;
        this.userService= userManagementService;
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public CompletableFuture<List<String>> signUpForAccount(HttpServletRequest request,
                                                                     @RequestParam String accountName,
                                                                     @RequestParam String billingEmail,
                                                                     @RequestParam boolean addAllGroupsToAccount,
                                                                     @RequestParam String otherAdmins,
                                                                     @RequestParam(required = false) String paymentRef) {
        final String userId = getUserIdFromRequest(request);
        String accountUid = accountBroker.createAccount(userId, accountName, userId, paymentRef);

        if (!StringUtils.isEmpty(paymentRef) && addAllGroupsToAccount) {
            accountBroker.addAllUserCreatedGroupsToAccount(accountUid, userId);
        }

        final List<String> errorAdmins = StringUtils.isEmpty(otherAdmins) ? new ArrayList<>()
                : handleAddingAccountAdmins(userId, accountUid, otherAdmins);

        return billingServiceBroker.createSubscription(accountName, billingEmail, getJwtTokenFromRequest(request))
                .map(record -> {
                    log.info("Got a record: {}", record.getAccountName());
                    accountBroker.setAccountSubscriptionRef(userId, accountUid, record.getId());
                    log.info("Account enabled, done, returning with error admins: {}", errorAdmins);
                    return errorAdmins;
                }).toFuture();
    }

    // this is quite heavy, but also error prone, hence doing it one by one to avoid failed sign up
    private List<String> handleAddingAccountAdmins(String addingUserUid, String accountUid, String adminEmailsPhones) {
        List<String> adminsPhoneOrEmail = Arrays.stream(adminEmailsPhones.split(",")).map(String::trim).map(String::toLowerCase).collect(Collectors.toList());
        List<String> errorAdmins = new ArrayList<>();
        adminsPhoneOrEmail.forEach(admin -> {
            try {
                User user = userService.loadOrCreateUser(admin); // todo : make user service work with this & email
                accountBroker.addAdministrator(addingUserUid, accountUid, user.getUid());
            } catch (IllegalArgumentException e) {
                log.error("Could not add admin: {}");
                errorAdmins.add(admin);
            }
        });
        return errorAdmins;
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/add/admin/{accountId}", method = RequestMethod.POST)
    public ResponseEntity addAdminToAccount(@PathVariable String accountId, HttpServletRequest request,
                                            String userToAddPhoneOrEmail) {
        List<String> failuresIfAny = handleAddingAccountAdmins(getUserIdFromRequest(request), accountId, userToAddPhoneOrEmail);
        return ResponseEntity.ok(failuresIfAny);
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/remove/admin/{accountId}", method = RequestMethod.POST)
    public ResponseEntity removeAdminFromAccount(@PathVariable String accountId, HttpServletRequest request,
                                                 String adminUid) {
        accountBroker.removeAdministrator(getUserIdFromRequest(request), accountId, adminUid, true);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/add/group/{accountId}", method = RequestMethod.POST)
    public ResponseEntity addGroupsToAccount(@PathVariable String accountId, HttpServletRequest request,
                                             List<String> groupUids) {
        accountBroker.addGroupsToAccount(accountId, new HashSet<>(groupUids), getUserIdFromRequest(request));
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/add/group/all/{accountId}", method = RequestMethod.POST)
    public ResponseEntity addAllGroupsToAccount(@PathVariable String accountId, HttpServletRequest request) {
        accountBroker.addAllUserCreatedGroupsToAccount(accountId, getUserIdFromRequest(request));
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/remove/group/{accountId}", method = RequestMethod.POST)
    public ResponseEntity removeGroupFromAccount(@PathVariable String accountId, HttpServletRequest request) {
//        accountBroker.removeAdministrator();
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/change/payment/{accountId}", method = RequestMethod.POST)
    public ResponseEntity alterAccountPaymentRef(@PathVariable String accountId, @RequestParam String paymentRef,
                                                 HttpServletRequest request) {
        accountBroker.setAccountPaymentRef(getUserIdFromRequest(request), paymentRef, accountId);
        return ResponseEntity.ok().build();
    }

}
