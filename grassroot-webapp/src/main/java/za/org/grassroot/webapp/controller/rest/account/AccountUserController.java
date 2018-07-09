package za.org.grassroot.webapp.controller.rest.account;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.integration.billing.BillingServiceBroker;
import za.org.grassroot.integration.billing.SubscriptionRecordDTO;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.model.rest.wrappers.AccountWrapper;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController @Grassroot2RestController @Slf4j
@RequestMapping("/v2/api/account") @Api("/v2/api/account")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class AccountUserController extends BaseRestController {

    private final AccountBroker accountBroker;
    private final BillingServiceBroker billingServiceBroker;
    private final UserManagementService userService;

    public AccountUserController(JwtService jwtService, UserManagementService userManagementService, AccountBroker accountBroker, BillingServiceBroker billingServiceBroker) {
        super(jwtService, userManagementService);
        this.accountBroker = accountBroker;
        this.billingServiceBroker = billingServiceBroker;
        this.userService= userManagementService;
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public CompletableFuture<AccountCreationResponse> signUpForAccount(HttpServletRequest request,
                                                                     @RequestParam String accountName,
                                                                     @RequestParam String billingEmail,
                                                                     @RequestParam(required = false) String otherAdmins) {
        final String userId = getUserIdFromRequest(request);
        return billingServiceBroker.createSubscription(accountName, billingEmail, getJwtTokenFromRequest(request), false)
                .map(record -> {
                    log.info("Got a record back, as created: {}", record.getAccountName());
                    final String accountUid = accountBroker.createAccount(userId, accountName, userId, null);
                    final List<String> errorAdmins = StringUtils.isEmpty(otherAdmins) ? new ArrayList<>()
                            : handleAddingAccountAdmins(userId, accountUid, otherAdmins);
                    accountBroker.setAccountSubscriptionRef(userId, accountUid, record.getId());
                    log.info("Account enabled, done, returning with error admins: {}", errorAdmins);
                    return new AccountCreationResponse(accountUid, errorAdmins);
                }).toFuture();
    }

    @RequestMapping(value = "/change/payment/{accountId}", method = RequestMethod.POST)
    public CompletableFuture<SubscriptionRecordDTO> alterAccountPaymentRef(@PathVariable String accountId,
                                                                           @RequestParam String paymentRef,
                                                                           @RequestParam(required = false) Boolean addAllGroups,
                                                                           HttpServletRequest request) {
        accountBroker.setAccountPaymentRef(getUserIdFromRequest(request), accountId, paymentRef);
        if (addAllGroups != null && addAllGroups) {
            accountBroker.addAllUserCreatedGroupsToAccount(accountId, getUserIdFromRequest(request));
        }
        Account account = accountBroker.loadAccount(accountId);
        return billingServiceBroker.enableSubscription(account.getSubscriptionRef(), getJwtTokenFromRequest(request)).toFuture();
    }

    // this is quite heavy, but also error prone, hence doing it one by one to avoid failed sign up
    private List<String> handleAddingAccountAdmins(String addingUserUid, String accountUid, String adminEmailsPhones) {
        List<String> adminsPhoneOrEmail = Arrays.stream(adminEmailsPhones.split(",")).map(String::trim).map(String::toLowerCase).collect(Collectors.toList());
        List<String> errorAdmins = new ArrayList<>();
        adminsPhoneOrEmail.forEach(admin -> {
            try {
                User user = userService.loadOrCreate(admin);
                accountBroker.addAdministrator(addingUserUid, accountUid, user.getUid());
            } catch (IllegalArgumentException|NullPointerException e) {
                log.error("Could not add admin: {}", admin);
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
        return wrappedAccount(accountId, request);
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/add/group/{accountId}", method = RequestMethod.POST)
    public ResponseEntity addGroupsToAccount(@PathVariable String accountId, HttpServletRequest request,
                                             @RequestParam Set<String> groupUids) {
        log.info("adding groups: {} to account: {}", groupUids, accountId);
        accountBroker.addGroupsToAccount(accountId, groupUids, getUserIdFromRequest(request));
        return wrappedAccount(accountId, request);
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/add/group/{accountId}/all", method = RequestMethod.POST)
    public ResponseEntity addAllGroupsToAccount(@PathVariable String accountId, HttpServletRequest request) {
        accountBroker.addAllUserCreatedGroupsToAccount(accountId, getUserIdFromRequest(request));
        return wrappedAccount(accountId, request);
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/remove/group/{accountId}", method = RequestMethod.POST)
    public ResponseEntity removeGroupFromAccount(@PathVariable String accountId,
                                                 @RequestParam Set<String> groupIds,
                                                 HttpServletRequest request) {
        accountBroker.removeGroupsFromAccount(accountId, groupIds, getUserIdFromRequest(request));
        return wrappedAccount(accountId, request);
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/close", method = RequestMethod.POST)
    public String closeAccount(@RequestParam String accountUid,
                               HttpServletRequest request) {
        User user = getUserFromRequest(request);
        try {
            accountBroker.closeAccount(user.getUid(), accountUid, null);
            return "closed";
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.PERMISSION_VIEW_ACCOUNT_DETAILS);
        }
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/fetch", method = RequestMethod.GET)
    public ResponseEntity<AccountWrapper> getAccountSettings(@RequestParam(required = false) String accountUid,
                                                             HttpServletRequest request) {
        User user = getUserFromRequest(request);
        Account account = StringUtils.isEmpty(accountUid) ? user.getPrimaryAccount() : accountBroker.loadAccount(accountUid);

        if (account == null) {
            return ResponseEntity.ok().build();
        } else {
            long startTime = System.currentTimeMillis();
            AccountWrapper accountWrapper = new AccountWrapper(account, user);
            log.info("Assembled user account wrapper, time : {} msecs", System.currentTimeMillis() - startTime);
            long accountNotifications = accountBroker.countAccountNotifications(account.getUid(),
                    account.getLastBillingDate(), Instant.now());
            log.info("Counted {} notifications for account", accountNotifications);
            accountWrapper.setNotificationsSinceLastBill(accountNotifications);
            return ResponseEntity.ok(accountWrapper);
        }
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/fetch/groups/candidates", method = RequestMethod.GET)
    public ResponseEntity<Map<String, String>> getGroupsUserCanAdd(@RequestParam String accountUid,
                                                                   HttpServletRequest request) {
        List<Group> groups = accountBroker.fetchGroupsUserCanAddToAccount(accountUid, getUserIdFromRequest(request))
                .stream().sorted(Comparator.comparing(Group::getName)).collect(Collectors.toList());
        Map<String, String> groupUidsAndNames = new LinkedHashMap<>();
        groups.forEach(group -> groupUidsAndNames.put(group.getUid(), group.getName()));
        return ResponseEntity.ok(groupUidsAndNames);
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/fetch/group/notification_count", method = RequestMethod.GET)
    public ResponseEntity<Long> getGroupNotificationCount(@RequestParam String accountUid,
                                                          @RequestParam String groupUid,
                                                          HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        long startTime = System.currentTimeMillis();
        long groupCount = accountBroker.countChargedNotificationsForGroup(accountUid, groupUid,
                account.getLastBillingDate(), Instant.now());
        log.info("Counted {} messages, took {} msecs", groupCount, System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(groupCount);
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/primary/set/{accountUid}", method = RequestMethod.POST)
    public ResponseEntity<AccountWrapper> setAccountPrimary(@PathVariable String accountUid, HttpServletRequest request) {
        accountBroker.setAccountPrimary(getUserIdFromRequest(request), accountUid);
        return wrappedAccount(accountUid, request);
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/settings/update", method = RequestMethod.POST)
    public ResponseEntity<AccountWrapper> updateAccountName(@RequestParam String accountUid,
                                                            @RequestParam String accountName,
                                                            HttpServletRequest request) {
        User user = getUserFromRequest(request);
        try {
            accountBroker.renameAccount(user.getUid(), accountUid, accountName);
            Account account = accountBroker.loadAccount(accountUid);
            return ResponseEntity.ok(new AccountWrapper(account, user));
        }catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.PERMISSION_VIEW_ACCOUNT_DETAILS);
        }
    }

    private ResponseEntity<AccountWrapper> wrappedAccount(String accountUid, HttpServletRequest request) {
        return ResponseEntity.ok(new AccountWrapper(accountBroker.loadAccount(accountUid), getUserFromRequest(request)));
    }

}
