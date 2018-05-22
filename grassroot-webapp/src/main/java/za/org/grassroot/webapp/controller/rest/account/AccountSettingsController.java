package za.org.grassroot.webapp.controller.rest.account;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.model.rest.wrappers.AccountWrapper;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller @Grassroot2RestController
@RequestMapping(value = "/v2/api/account") @Api("/v2/api/account")
@PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
public class AccountSettingsController extends BaseRestController {

    private final AccountBroker accountBroker;
    private final AccountGroupBroker accountGroupBroker;
    private final PermissionBroker permissionBroker;


    @Autowired
    public AccountSettingsController(UserManagementService userService, AccountBroker accountBroker, AccountGroupBroker accountGroupBroker,
                                     PermissionBroker permissionBroker, JwtService jwtService) {
        super(jwtService, userService);
        this.accountBroker = accountBroker;
        this.accountGroupBroker = accountGroupBroker;
        this.permissionBroker = permissionBroker;
    }

    @RequestMapping(value = "/settings/fetch", method = RequestMethod.GET)
    public ResponseEntity<AccountWrapper> getAccountSettings(@RequestParam(required = false) String accountUid,
                                                             HttpServletRequest request) {
        User user = getUserFromRequest(request);
        Account account = StringUtils.isEmpty(accountUid) ? user.getPrimaryAccount() : accountBroker.loadAccount(accountUid);

        if (account == null) {
            return ResponseEntity.ok(null);
        } else {
            final int groupsLeft = account.isEnabled() ? accountGroupBroker.numberGroupsLeft(account.getUid()) : 0;
            final int messagesLeft = account.isEnabled() ? accountGroupBroker.numberMessagesLeft(account.getUid()) : 0;
            return ResponseEntity.ok(new AccountWrapper(account, user, groupsLeft, messagesLeft));
        }
    }

    @RequestMapping(value = "/settings/update", method = RequestMethod.POST)
    public ResponseEntity<AccountWrapper> updateAccountSettings(@RequestParam String accountUid,
                                                                @RequestParam String accountName,
                                                                @RequestParam String billingEmail,
                                                                @RequestParam AccountType accountType,
                                                                @RequestParam AccountBillingCycle billingCycle,
                                                                HttpServletRequest request) {
        User user = getUserFromRequest(request);
        Account account = accountBroker.loadAccount(accountUid);
        try {
            if (!account.getAdministrators().contains(user)) {
                permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
            }
            accountBroker.modifyAccount(user.getUid(), accountUid, accountType, accountName, billingEmail, billingCycle);

            account = accountBroker.loadAccount(accountUid);
            final int groupsLeft = account.isEnabled() ? accountGroupBroker.numberGroupsLeft(account.getUid()) : 0;
            final int messagesLeft = account.isEnabled() ? accountGroupBroker.numberMessagesLeft(account.getUid()) : 0;
            return ResponseEntity.ok(new AccountWrapper(account, user, groupsLeft, messagesLeft));
        }catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.PERMISSION_VIEW_ACCOUNT_DETAILS);
        }
    }

    @RequestMapping(value = "/last-cost", method = RequestMethod.GET)
    public ResponseEntity<Long> getCostSinceLastBill(@RequestParam String accountUid,
                                                     HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        // long costSinceLastBill = billingBroker.calculateMessageCostsInPeriod(account, account.getLastPaymentDate(), Instant.now());
        // todo: as elsewhere, make this work
        return ResponseEntity.ok(0L);
    }

    @RequestMapping(value = "/account-fees", method = RequestMethod.GET)
    public ResponseEntity<Map<AccountType, Integer>> getAccountTypeFees(HttpServletRequest request) {
        return ResponseEntity.ok(accountBroker.getAccountTypeFees());
    }

    @RequestMapping(value = "/billing-records", method = RequestMethod.GET)
    public ResponseEntity<List<AccountBillingRecordResponse>> getAccountBillingRecords(@RequestParam String accountUid,
                                                                               HttpServletRequest request) {
        User user = getUserFromRequest(request);
        Account account = accountBroker.loadAccount(accountUid);
        try {
            if (!account.getAdministrators().contains(user)) {
                permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
            }
            List<AccountBillingRecordResponse> accountBillingRecordsResponse = new ArrayList<>();
            // todo: pull from chargebee or remove
//            if (account.getAdministrators().contains(user)) {
//                List<AccountBillingRecord> accountBillingRecords = billingBroker.findRecordsWithStatementDates(accountUid);
//                accountBillingRecords.forEach(abr -> accountBillingRecordsResponse.add(new AccountBillingRecordResponse(abr.getCreatedDateTime(), abr.getPaymentId())));
//            }
            return ResponseEntity.ok(accountBillingRecordsResponse);
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.PERMISSION_VIEW_ACCOUNT_DETAILS);
        }
    }

    // todo: restore from chargebee or similar
//    @RequestMapping(value = "/statement", method = RequestMethod.GET)
//    public ResponseEntity<byte[]> viewAccountBillingStatement(@RequestParam String paymentUid,
//                                                              @RequestParam String accountUid,
//                                                              HttpServletRequest request) throws IOException{
//        User user = getUserFromRequest(request);
//        Account account = accountBroker.loadAccount(accountUid);
//        try {
//            if (!account.getAdministrators().contains(user)) {
//                permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
//            }
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//            headers.add("Content-Disposition", "attachment; filename=\"statement.pdf\"");
//            headers.add("Cache-Control", "no-cache");
//            headers.add("Pragma", "no-cache");
//            headers.setDate("Expires", 0);
//            headers.setContentType(MediaType.APPLICATION_PDF);
//
//
//            AccountBillingRecord accountBillingRecord = billingBroker.fetchRecordByPayment(paymentUid);
//            File invoiceFile = pdfGeneratingService.generateInvoice(Collections.singletonList(accountBillingRecord.getUid()));
//            byte[] data = IOUtils.toByteArray(new FileInputStream(invoiceFile));
//            headers.setContentDispositionFormData("statement.pdf", "statement.pdf");
//            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
//            return new ResponseEntity<>(data, headers, HttpStatus.OK);
//        }catch (AccessDeniedException e) {
//            throw new MemberLacksPermissionException(Permission.PERMISSION_VIEW_ACCOUNT_DETAILS);
//        }
//    }


    @RequestMapping(value = "/close", method = RequestMethod.POST)
    public String closeAccount(@RequestParam String accountUid,
                               HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        User user = getUserFromRequest(request);

        try {
            if (!account.getAdministrators().contains(user)) {
                permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
            }

            accountBroker.closeAccountRest(user.getUid(), accountUid, true);
            return "closed";
        }catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.PERMISSION_VIEW_ACCOUNT_DETAILS);
        }


    }
}
