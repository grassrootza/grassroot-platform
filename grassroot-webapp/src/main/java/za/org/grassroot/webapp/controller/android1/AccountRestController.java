package za.org.grassroot.webapp.controller.android1;

import org.apache.commons.validator.routines.EmailValidator;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;
import za.org.grassroot.core.domain.account.PaidGroup;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.integration.payments.PaymentBroker;
import za.org.grassroot.integration.payments.PaymentResponse;
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

import javax.servlet.http.HttpServletRequest;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by luke on 2017/01/11.
 */
@Controller
@RequestMapping(value = "/api/account", produces = MediaType.APPLICATION_JSON_VALUE)
@PropertySource(value = "${grassroot.payments.properties}", ignoreResourceNotFound = true)
public class AccountRestController {

    @Value("${grassroot.payments.auth.path.mobile:/auth/incoming/mobile}")
    private String mobilePaymentNotifyPath;

    private static final Logger logger = LoggerFactory.getLogger(AccountRestController.class);

    private final UserManagementService userService;
    private final AccountBroker accountBroker;
    private final AccountGroupBroker accountGroupBroker;

    private final AccountBillingBroker billingBroker;
    private final PaymentBroker paymentBroker;

    @Autowired
    public AccountRestController(UserManagementService userService, AccountBroker accountBroker, AccountGroupBroker accountGroupBroker,
                                 AccountBillingBroker billingBroker, PaymentBroker paymentBroker) {
        this.userService = userService;
        this.accountBroker = accountBroker;
        this.billingBroker = billingBroker;
        this.paymentBroker = paymentBroker;
        this.accountGroupBroker = accountGroupBroker;
    }

    @GetMapping("settings/fetch/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> getAccountSettings(@PathVariable String phoneNumber, @RequestParam(required = false) String accountUid) {
        User user = userService.findByInputNumber(phoneNumber);
        Account account = StringUtils.isEmpty(accountUid) ? user.getPrimaryAccount() : accountBroker.loadAccount(accountUid);

        if (account == null) {
           return RestUtil.messageOkayResponse(RestMessage.USER_NO_ACCOUNT);
        } else {
            final int groupsLeft = account.isEnabled() ? accountGroupBroker.numberGroupsLeft(account.getUid()) : 0;
            final int messagesLeft = account.isEnabled() ? accountGroupBroker.numberMessagesLeft(account.getUid()) : 0;
            return RestUtil.okayResponseWithData(account.isEnabled() ? RestMessage.ACCOUNT_ENABLED : RestMessage.ACCOUNT_DISABLED,
                    new AccountWrapper(account, user, groupsLeft, messagesLeft));
        }
    }

    @GetMapping("payment/signup/initiate/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> initiateAccountSignup(@PathVariable String phoneNumber,
                                                                 @RequestParam String accountName,
                                                                 @RequestParam String billingEmail,
                                                                 @RequestParam AccountType accountType,
                                                                 HttpServletRequest request) {
        User user = userService.findByInputNumber(phoneNumber);

        final String accountUid = accountBroker.createAccount(user.getUid(), accountName, user.getUid(), accountType,
                AccountPaymentType.CARD_PAYMENT, AccountBillingCycle.MONTHLY, false);

        if (!StringUtils.isEmpty(billingEmail) && EmailValidator.getInstance(false).isValid(billingEmail)) {
            userService.updateEmailAddress(user.getUid(), user.getUid(), billingEmail);
        }

        try {
            final String notifyUrl = generateNotifyUrl(request);
            logger.info("initiating mobile payment with return URL : {}", notifyUrl);

            AccountBillingRecord record = billingBroker.generateSignUpBill(accountUid);
            PaymentResponse response = paymentBroker.initiateMobilePayment(record, notifyUrl);

            record.setPaymentId(response.getThisPaymentId()); // also done in method but saving a return trip to DB (and/or possible overwrite issues)

            return RestUtil.okayResponseWithData(RestMessage.ACCOUNT_CREATED, new BillingWrapper(record));
        } catch (URISyntaxException e) {
            return RestUtil.errorResponse(RestMessage.MISC_PAYMENT_ERROR);
        }
    }

    @GetMapping("payment/enable/initiate/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> initiateAccountEnablePayment(@RequestParam String accountUid,
                                                                        HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        if (account.isEnabled()) {
            return RestUtil.errorResponse(RestMessage.ACCOUNT_ENABLED);
        } else {
            // todo : double check if really want to generate new bill (might have separate method here)
            try {
                AccountBillingRecord record = billingBroker.generateSignUpBill(accountUid);
                final String notifyUrl = generateNotifyUrl(request);
                logger.info("mobile payment URL: {}", notifyUrl);
                PaymentResponse response = paymentBroker.initiateMobilePayment(record, notifyUrl);
                return RestUtil.okayResponseWithData(RestMessage.PAYMENT_STARTED, response.getThisPaymentId());
            } catch (URISyntaxException e) {
                return RestUtil.errorResponse(RestMessage.MISC_PAYMENT_ERROR);
            }
        }
    }

    private String generateNotifyUrl(HttpServletRequest request) throws URISyntaxException {
        return new URIBuilder().setScheme("https")
                .setHost(request.getServerName())
                .setPort(request.getServerPort())
                .setPath(mobilePaymentNotifyPath)
                .build().toString();

    }

    @GetMapping("payment/result/{phoneNumber}/{code}")
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
            accountBroker.enableAccount(user.getUid(), accountUid, response.getReference(), AccountPaymentType.CARD_PAYMENT, true, true);
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

        if (!accountGroupBroker.canAddGroupToAccount(user.getUid(), null)) {
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
