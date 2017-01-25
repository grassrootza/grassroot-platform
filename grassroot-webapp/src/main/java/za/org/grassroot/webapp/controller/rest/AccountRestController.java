package za.org.grassroot.webapp.controller.rest;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.domain.PaidGroup;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.integration.payments.PaymentResponse;
import za.org.grassroot.integration.payments.PaymentServiceBroker;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.GroupAccountMismatchException;
import za.org.grassroot.services.exception.GroupAlreadyPaidForException;
import za.org.grassroot.services.exception.GroupNotPaidForException;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.AccountWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.BillingWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.PaidGroupWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by luke on 2017/01/11.
 */
@Controller
@RequestMapping(value = "/api/account", produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountRestController {

    private static final Logger logger = LoggerFactory.getLogger(AccountRestController.class);

    private final UserManagementService userService;
    private final AccountBroker accountBroker;
    private final AccountGroupBroker accountGroupBroker;

    private final AccountBillingBroker billingBroker;
    private final PaymentServiceBroker paymentBroker;


    @Autowired
    public AccountRestController(UserManagementService userService, AccountBroker accountBroker, AccountGroupBroker accountGroupBroker,
                                 AccountBillingBroker billingBroker, PaymentServiceBroker paymentBroker) {
        this.userService = userService;
        this.accountBroker = accountBroker;
        this.billingBroker = billingBroker;
        this.paymentBroker = paymentBroker;
        this.accountGroupBroker = accountGroupBroker;
    }

    @GetMapping("settings/fetch/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> getAccountSettings(@PathVariable String phoneNumber, @RequestParam(required = false) String accountUid) {
        User user = userService.findByInputNumber(phoneNumber);
        Account account = StringUtils.isEmpty(accountUid) ? accountBroker.loadUsersAccount(user.getUid()) : accountBroker.loadAccount(accountUid);

        if (account == null || !account.isEnabled()) { // once client wired for disabled account, remove or condition
           return RestUtil.messageOkayResponse(RestMessage.MESSAGE_SETTING_NOT_FOUND);
        } else {
            final int groupsLeft = accountGroupBroker.numberGroupsLeft(account.getUid());
            final int messagesLeft = accountGroupBroker.numberMessagesLeft(account.getUid());
            return RestUtil.okayResponseWithData(account.isEnabled() ? RestMessage.ACCOUNT_ENABLED : RestMessage.ACCOUNT_DISABLED,
                    new AccountWrapper(account, user, groupsLeft, messagesLeft));
        }
    }

    @GetMapping("payment/signup/initiate/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> initiateAccountSignup(@PathVariable String phoneNumber,
                                                                @RequestParam String accountName,
                                                                @RequestParam String billingEmail,
                                                                @RequestParam AccountType accountType) {
        User user = userService.findByInputNumber(phoneNumber);

        final String accountUid = accountBroker.createAccount(user.getUid(), accountName, user.getUid(), accountType);
        if (!StringUtils.isEmpty(billingEmail) && EmailValidator.getInstance(false).isValid(billingEmail)) {
            userService.updateEmailAddress(user.getUid(), billingEmail);
        }

        AccountBillingRecord record = billingBroker.generateSignUpBill(accountUid);
        PaymentResponse response = paymentBroker.initiateMobilePayment(record);

        record.setPaymentId(response.getThisPaymentId()); // also done in method but saving a return trip to DB (and/or possible overwrite issues)

        return RestUtil.okayResponseWithData(RestMessage.ACCOUNT_CREATED, new BillingWrapper(record));
    }

    @GetMapping("payment/status/check/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> checkPaymentStatus(@PathVariable String phoneNumber,
                                                              @RequestParam String accountUid,
                                                              @RequestParam String paymentId) {
        User user = userService.findByInputNumber(phoneNumber);
        Account account = accountBroker.loadAccount(accountUid);

        if (account == null || !account.getAdministrators().contains(user)) {
            return RestUtil.accessDeniedResponse();
        }

        PaymentResponse response = paymentBroker.checkMobilePaymentResult(paymentId);
        if (response != null && response.isSuccessful()) {
            accountBroker.enableAccount(user.getUid(), accountUid, LocalDate.now().plusMonths(1L), response.getReference());
            return RestUtil.okayResponseWithData(RestMessage.ACCOUNT_ENABLED, new AccountWrapper(account, user,
                    account.getMaxNumberGroups(), account.getFreeFormMessages())); // by definition a newly enabled account has full quota
        } else {
            logger.info("error! payment response: {}", response);
            return RestUtil.errorResponse(RestMessage.PAYMENT_ERROR);
        }
    }

    @GetMapping("groups/add/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> addGroupToAccount(@PathVariable String phoneNumber,
                                                             @RequestParam String accountUid,
                                                             @RequestParam String groupUid) {
        User user = userService.findByInputNumber(phoneNumber);
        Account account = accountBroker.loadAccount(accountUid);

        if (account == null || !account.getAdministrators().contains(user)) {
            return RestUtil.accessDeniedResponse();
        }

        if (!accountGroupBroker.canAddGroupToAccount(user.getUid())) {
            return RestUtil.errorResponse(RestMessage.ACCOUNT_GROUPS_EXHAUSTED);
        }

        try {
            accountGroupBroker.addGroupToAccount(accountUid, groupUid, user.getUid());
            return RestUtil.messageOkayResponse(RestMessage.GROUP_ADDED_TO_ACCOUNT);
        } catch (GroupAlreadyPaidForException e) {
            return RestUtil.errorResponse(RestMessage.GROUP_ALREADY_PAID_FOR);
        }
    }

    @GetMapping("groups/list/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> removeGroupsFromAccount(@PathVariable String phoneNumber,
                                                                   @RequestParam String accountUid) {
        User user = userService.findByInputNumber(phoneNumber);
        Account account = accountBroker.loadAccount(accountUid);

        if (account == null || !account.getAdministrators().contains(user)) {
            return RestUtil.accessDeniedResponse();
        }

        List<PaidGroupWrapper> groups = accountGroupBroker.fetchGroupsSponsoredByAccount(accountUid)
                .stream().map(PaidGroupWrapper::new).collect(Collectors.toList());
        return RestUtil.okayResponseWithData(RestMessage.ACCOUNT_GROUPS, groups);
    }

    @GetMapping("groups/remove/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> removeGroupDo(@PathVariable String phoneNumber,
                                                         @RequestParam String accountUid,
                                                         @RequestParam String groupUid) {
        User user = userService.findByInputNumber(phoneNumber);
        Account account = accountBroker.loadAccount(accountUid);

        if (account == null || !account.getAdministrators().contains(user)) {
            return RestUtil.accessDeniedResponse();
        }

        try {
            accountGroupBroker.removeGroupsFromAccount(accountUid, Collections.singleton(groupUid), user.getUid());
            return RestUtil.messageOkayResponse(RestMessage.GROUP_REMOVED_ACCOUNT);
        } catch (Exception e) {
            return RestUtil.errorResponse(RestMessage.ERROR_REMOVING_GROUP);
        }
    }

    @GetMapping("message/send/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> sendFreeFormMessage(@PathVariable String phoneNumber,
                                                               @RequestParam String accountUid,
                                                               @RequestParam String groupUid,
                                                               @RequestParam String message) {
        User user = userService.findByInputNumber(phoneNumber);
        Account account = accountBroker.loadAccount(accountUid);

        if (account == null || !account.getAdministrators().contains(user)) {
            return RestUtil.accessDeniedResponse();
        }

        try {
            accountGroupBroker.sendFreeFormMessage(user.getUid(), groupUid, message);
            return RestUtil.messageOkayResponse(RestMessage.FFORM_MESSAGE_SENT);
        } catch (GroupNotPaidForException e) {
            return RestUtil.errorResponse(RestMessage.GROUP_NOT_PAID_FOR);
        } catch (GroupAccountMismatchException e) {
            return RestUtil.errorResponse(RestMessage.GROUP_ACCOUNT_WRONG);
        } catch (AccountLimitExceededException e) {
            return RestUtil.errorResponse(RestMessage.FREE_FORM_EXHAUSTED);
        }
    }

    @GetMapping("type/change/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> changeAccountType(@PathVariable String phoneNumber,
                                                             @RequestParam String accountUid,
                                                             @RequestParam AccountType accountType) {
        User user = userService.findByInputNumber(phoneNumber);
        Account account = accountBroker.loadAccount(accountUid);

        if (account == null || !account.getAdministrators().contains(user)) {
            return RestUtil.accessDeniedResponse();
        }

        List<PaidGroup> currentlyPaidGroups = account.getPaidGroups().stream()
                .filter(PaidGroup::isActive)
                .collect(Collectors.toList());

        int numberToRemove = currentlyPaidGroups.size() - accountBroker.getNumberGroupsPerType().getOrDefault(accountType, 0);
        if (numberToRemove > 0) {
            return RestUtil.errorResponse(RestMessage.MUST_REMOVE_PAID_GROUPS);
        } else {
            accountBroker.changeAccountType(user.getUid(), accountUid, accountType, null);
            Account alteredAccount = accountBroker.loadAccount(accountUid);
            return RestUtil.okayResponseWithData(null, new AccountWrapper(alteredAccount, user,
                    accountGroupBroker.numberGroupsLeft(accountUid),accountGroupBroker.numberMessagesLeft(accountUid)));
        }
    }
}
