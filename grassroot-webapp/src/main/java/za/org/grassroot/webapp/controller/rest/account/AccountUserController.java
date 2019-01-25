package za.org.grassroot.webapp.controller.rest.account;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.dto.GrassrootEmail;
import za.org.grassroot.core.dto.group.GroupRefDTO;
import za.org.grassroot.integration.authentication.CreateJwtTokenRequest;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.authentication.JwtType;
import za.org.grassroot.integration.billing.BillingServiceBroker;
import za.org.grassroot.integration.billing.SubscriptionRecordDTO;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.DataSetInfo;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.CampaignStatsBroker;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.model.rest.AuthorizedUserDTO;
import za.org.grassroot.webapp.model.rest.CampaignViewDTO;
import za.org.grassroot.webapp.model.rest.wrappers.AccountWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController @Grassroot2RestController @Slf4j
@RequestMapping("/v2/api/account") @Api("/v2/api/account")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class AccountUserController extends BaseRestController {

    @Value("${grassroot.accounts.email:accounts@somewhere}")
    private String grassrootAccountsEmail;

    private final AccountBroker accountBroker;
    private final UserManagementService userService;
    private final JwtService jwtService;

    private BillingServiceBroker billingServiceBroker;
    private MessagingServiceBroker messagingServiceBroker;
    private MemberDataExportBroker memberDataExportBroker;

    private CampaignBroker campaignBroker;
    private CampaignStatsBroker campaignStatsBroker;

    @Autowired
    public AccountUserController(JwtService jwtService, UserManagementService userManagementService, AccountBroker accountBroker) {
        super(jwtService, userManagementService);
        this.accountBroker = accountBroker;
        this.userService= userManagementService;
        this.jwtService = jwtService;
    }

    @Autowired(required = false)
    public void setBillingServiceBroker(BillingServiceBroker billingServiceBroker) {
        this.billingServiceBroker = billingServiceBroker;
    }

    @Autowired(required = false)
    public void setMessagingServiceBroker(MessagingServiceBroker messagingServiceBroker) {
        this.messagingServiceBroker = messagingServiceBroker;
    }

    @Autowired
    public void setCampaignBroker(CampaignBroker campaignBroker) {
        this.campaignBroker = campaignBroker;
    }

    @Autowired
    public void setCampaignStatsBroker(CampaignStatsBroker campaignStatsBroker) {
        this.campaignStatsBroker = campaignStatsBroker;
    }

    @Autowired
    public void setMemberDataExportBroker(MemberDataExportBroker memberDataExportBroker) {
        this.memberDataExportBroker = memberDataExportBroker;
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public CompletableFuture<AccountCreationResponse> signUpForAccount(HttpServletRequest request,
                                                                     @RequestParam String accountName,
                                                                     @RequestParam String billingEmail,
                                                                     @RequestParam boolean addAllGroupsToAccount,
                                                                     @RequestParam(required = false) String otherAdmins) {
        final String userId = getUserIdFromRequest(request);
        final String accountUid = accountBroker.createAccount(userId, accountName, userId, billingEmail, null);
        if (addAllGroupsToAccount) {
            log.info("Add all groups selected, proceeding");
            accountBroker.addAllUserCreatedGroupsToAccount(accountUid, userId);
        }
        final List<String> errorAdmins = StringUtils.isEmpty(otherAdmins) ? new ArrayList<>()
                : handleAddingAccountAdmins(userId, accountUid, otherAdmins);
        final User refreshedUser = userService.load(userId);

        // will just return right away if no billing broker
        return addSubscriptionToAccount(accountUid, accountName, billingEmail, getJwtTokenFromRequest(request))
                .map(record -> {
                    log.info("Account enabled, done, returning with error admins: {}", errorAdmins);
                    triggerEmailIfEnabled(accountName, billingEmail, refreshedUser);
                    return assembleAccountCreated(accountUid, refreshedUser, errorAdmins);
                }).toFuture();
    }

    private Mono<SubscriptionRecordDTO> addSubscriptionToAccount(String accountUid, String accountName,
                                                                 String billingEmail, String jwtToken) {
        return billingServiceBroker == null ? Mono.empty() :
                billingServiceBroker.createSubscription(accountName, billingEmail, jwtToken, false).map(record -> {
                    log.info("Got a record back, as created: {}", record.getAccountName());
                    accountBroker.setAccountSubscriptionRef(getUserIdFromToken(jwtToken), accountUid, record.getId());
                    return record;
                });
    }

    private void triggerEmailIfEnabled(String accountName, String billingEmail, User user) {
        if (messagingServiceBroker == null)
            return;

        log.info("Notifying accounts admin of the new account");
        final String emailBody = "Greetings,\n\n A new account has been set up for Grassroot Extra. The account name is " +
                accountName + ", billing email given as " + billingEmail + ". The account was set up by the user " +
                user.getName() + ", with email " + user.getEmailAddress() + " and phone " + user.getPhoneNumber() + ".\n\n" +
                "Have a good day, \n\n Grassroot";
        GrassrootEmail email = new GrassrootEmail.EmailBuilder("New Grassroot Extra account")
                .toAddress(grassrootAccountsEmail).content(emailBody).build();
        messagingServiceBroker.sendEmail(email);
    }

    private AccountCreationResponse assembleAccountCreated(String accountUid, User refreshedUser, List<String> errorAdmins) {
        CreateJwtTokenRequest tokenRequest = new CreateJwtTokenRequest(JwtType.WEB_ANDROID_CLIENT, refreshedUser);
        String token = jwtService.createJwt(tokenRequest);
        return new AccountCreationResponse(accountUid, errorAdmins, new AuthorizedUserDTO(refreshedUser, token));
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
    @RequestMapping(value = { "/add/group/{accountId}", "/add/group" }, method = RequestMethod.POST)
    public ResponseEntity addGroupsToAccount(HttpServletRequest request, @PathVariable(required = false) String accountId,
                                             @RequestParam Set<String> groupUids) {
        log.info("adding groups: {} to account: {}", groupUids, accountId);
        final String accountUid = accountId == null ? getUserFromRequest(request).getPrimaryAccount().getUid() : accountId;
        accountBroker.addGroupsToAccount(accountUid, groupUids, getUserIdFromRequest(request));
        return wrappedAccount(accountUid, request);
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
            accountBroker.closeAccount(user.getUid(), accountUid, "Closed by user");
            return "closed";
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.PERMISSION_VIEW_ACCOUNT_DETAILS);
        }
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/fetch", method = RequestMethod.GET)
    public ResponseEntity<AccountWrapper> getAccountSettings(@RequestParam(required = false) String accountUid,
                                                             HttpServletRequest request) {
        final String userUid = getUserIdFromRequest(request);
        if (accountUid != null) {
            accountBroker.validateUserCanViewAccount(accountUid, userUid);
        }

        Account account = StringUtils.isEmpty(accountUid) ? accountBroker.loadDefaultAccountForUser(userUid)
                : accountBroker.loadAccount(accountUid);

        if (account == null) {
            return ResponseEntity.ok().build();
        } else {
            long startTime = System.currentTimeMillis();
            AccountWrapper accountWrapper = new AccountWrapper(account, userService.load(userUid));
            log.info("Assembled user account wrapper, time : {} msecs", System.currentTimeMillis() - startTime);
            long accountNotifications = accountBroker.countAccountNotifications(account.getUid(),
                    account.getLastBillingDate(), Instant.now());
            log.debug("Counted {} notifications for account", accountNotifications);
            accountWrapper.setNotificationsSinceLastBill(accountNotifications);
            accountWrapper.setChargedUssdSinceLastBill(accountBroker.countChargedUssdSessionsForAccount(account.getUid(),
                    account.getLastBillingDate(), Instant.now()));
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

    // note: necessary because std group fetch methods include checks for group membership, but account admin might not
    // be part of the group
    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/fetch/group/info", method = RequestMethod.GET)
    public ResponseEntity<GroupRefDTO> getAccountGroupInfo(@RequestParam String groupUid, HttpServletRequest request) {
        return ResponseEntity.ok(accountBroker.fetchGroupAccountInfo(getUserIdFromRequest(request), groupUid));
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/fetch/group/notification_count", method = RequestMethod.GET)
    public ResponseEntity<Long> getGroupNotificationCount(@RequestParam(required = false) String accountUid,
                                                          @RequestParam String groupUid,
                                                          HttpServletRequest request) {
        Account account = !StringUtils.isEmpty(accountUid) ? accountBroker.loadAccount(accountUid)
                : accountBroker.loadDefaultAccountForUser(getUserIdFromRequest(request));
        long startTime = System.currentTimeMillis();
        long groupCount = accountBroker.countChargedNotificationsForGroup(accountUid, groupUid,
                account.getLastBillingDate(), Instant.now());
        log.info("Counted {} messages, took {} msecs", groupCount, System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(groupCount);
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/fetch/campaign/list", method = RequestMethod.GET)
    public ResponseEntity<List<CampaignViewDTO>> fetchAccountCampaigns(@RequestParam String accountUid, HttpServletRequest request) {
        accountBroker.validateUserCanViewAccount(accountUid, getUserIdFromRequest(request));
        List<Campaign> campaigns = campaignBroker.getCampaignsOnAccount(accountUid, true);
        log.info("Retrieved {} campaigns for the account", campaigns.size());
        List<CampaignViewDTO> dtos = campaigns.stream().map(campaign -> new CampaignViewDTO(campaign, null, false))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/fetch/campaign/stats", method = RequestMethod.GET)
    public ResponseEntity<Map<String, String>> fetchCampaignBillingDetails(@RequestParam String accountUid,
                                                                           @RequestParam String campaignUid,
                                                                           HttpServletRequest request) {
        accountBroker.validateUserCanViewAccount(accountUid, getUserIdFromRequest(request));
        return ResponseEntity.ok(campaignStatsBroker.getCampaignBillingStatsInPeriod(campaignUid, null, null));
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/fetch/group/download", method = RequestMethod.GET)
    public ResponseEntity<byte[]> downloadAccountRecord(@RequestParam(required = false) String accountUid,
                                                        HttpServletRequest request) {
        Account account = !StringUtils.isEmpty(accountUid) ? accountBroker.loadAccount(accountUid)
                : accountBroker.loadDefaultAccountForUser(getUserIdFromRequest(request));
        log.info("Downloading details for groups on account {}", account.getName());
        return RestUtil.convertWorkbookToDownload(account.getName() + ".xlsx",
                memberDataExportBroker.exportAccountActivityReport(accountUid, account.getLastBillingDate(), Instant.now()));
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/fetch/campaign/billing/download",method = RequestMethod.GET)
    public ResponseEntity<byte[]> downloadCampaignBillingData(@RequestParam(required = false) String accountUid,
                                                              @RequestParam long startDateMillis,
                                                              @RequestParam long endDateMillis,
                                                              HttpServletRequest request){

        Account account = !StringUtils.isEmpty(accountUid) ? accountBroker.loadAccount(accountUid)
                : accountBroker.loadDefaultAccountForUser(getUserIdFromRequest(request));

        List<Campaign> campaigns = campaignBroker.getCampaignsOnAccount(accountUid,true);

        log.info("Total campaigns on account = {}",campaigns.size());

        return RestUtil.convertWorkbookToDownload(account.getName() + " billing data.xlsx",
                memberDataExportBroker.exportAccountBillingData(campaigns,Instant.ofEpochMilli(startDateMillis),Instant.ofEpochMilli(endDateMillis)));
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

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/fetch/dataset/{datasetLabel}", method = RequestMethod.GET)
    public ResponseEntity<DataSetInfo> fetchDatasetInfo(@PathVariable String dataSetLabel,
                                                        @RequestParam(required = false) Long startTimeMillis,
                                                        @RequestParam(required = false) Long endTimeMillis,
                                                        @RequestParam(required = false) String accountUid,
                                                        HttpServletRequest request) {
        User user = getUserFromRequest(request);
        Account account = StringUtils.isEmpty(accountUid) ? user.getPrimaryAccount() : accountBroker.loadAccount(accountUid);
        Instant start = startTimeMillis != null ? Instant.ofEpochMilli(startTimeMillis) : account.getLastBillingDate();
        Instant end = endTimeMillis != null ? Instant.ofEpochMilli(endTimeMillis) : Instant.now();

        DataSetInfo counts = accountBroker.fetchDataSetInfo(user.getUid(), dataSetLabel, start, end);
        log.info("completed dataset counts: {}", counts);
        return ResponseEntity.ok(counts);
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/fetch/all/dataset", method = RequestMethod.GET)
    public ResponseEntity<List<DataSetInfo>> fetchDataSets(@RequestParam(required = false) String accountUid,
                                                           @RequestParam(required = false) Long startTimeMillis,
                                                           @RequestParam(required = false) Long endTimeMillis,
                                                           HttpServletRequest request) {
        User user = getUserFromRequest(request);
        log.info("Counting dataset results for account: {}", accountUid);
        Account account = StringUtils.isEmpty(accountUid) ? user.getPrimaryAccount() : accountBroker.loadAccount(accountUid);
        Instant start = startTimeMillis != null ? Instant.ofEpochMilli(startTimeMillis) : account.getLastBillingDate();
        Instant end = endTimeMillis != null ? Instant.ofEpochMilli(endTimeMillis) : Instant.now();
        Set<String> dataSets = StringUtils.commaDelimitedListToSet(account.getGeoDataSets());
        log.info("Datasets extracted: {}; from account: {}", dataSets, account);
        List<DataSetInfo> dataCounts = dataSets.stream().map(label -> {
            DataSetInfo counts = accountBroker.fetchDataSetInfo(user.getUid(), label, start, end);
            log.info("for {} got counts {}", label, counts);
            return counts;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(dataCounts);
    }

    private ResponseEntity<AccountWrapper> wrappedAccount(String accountUid, HttpServletRequest request) {
        return ResponseEntity.ok(new AccountWrapper(accountBroker.loadAccount(accountUid), getUserFromRequest(request)));
    }

}
