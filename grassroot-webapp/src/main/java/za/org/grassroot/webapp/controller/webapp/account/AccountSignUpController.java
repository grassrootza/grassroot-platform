package za.org.grassroot.webapp.controller.webapp.account;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.PaidGroup;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.integration.messaging.GrassrootEmail;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/10/26.
 */
@Controller
@RequestMapping("/account/")
@PropertySource(value = "${grassroot.payments.properties}", ignoreResourceNotFound = true)
public class AccountSignUpController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AccountSignUpController.class);

    private final AccountBroker accountBroker;
    private final AccountGroupBroker accountGroupBroker;
    private final MessagingServiceBroker messageBroker;

    @Autowired
    public AccountSignUpController(AccountBroker accountBroker, AccountBillingBroker billingBroker,
                                   AccountGroupBroker accountGroupBroker, MessagingServiceBroker messageBroker) {
        this.accountBroker = accountBroker;
        this.messageBroker = messageBroker;
        this.accountGroupBroker = accountGroupBroker;
    }

    @RequestMapping(value = "signup", method = RequestMethod.GET)
    public String startAccountSignup(Model model, @RequestParam(required = false) String accountType,
                                     @RequestParam(required = false) Boolean forceNew) {
        User user = userManagementService.load(getUserProfile().getUid());
        logger.debug("accountType in parameter: {}", accountType);
        if (user.getPrimaryAccount() != null && (forceNew == null || !forceNew)) {
            return user.getPrimaryAccount().isEnabled() ? "redirect:/account/type" : "redirect:/account";
        } else {
            model.addAttribute("user", user); // may be cached (and not reflect email) if use just getuserprofile
            model.addAttribute("accountTypes", Arrays.asList(AccountType.LIGHT, AccountType.STANDARD, AccountType.LARGE));

            if (!StringUtils.isEmpty(accountType) && AccountType.contains(accountType)) {
                model.addAttribute("defaultType", AccountType.valueOf(accountType));
            } else {
                model.addAttribute("defaultType", AccountType.STANDARD);
            }

            model.addAttribute("showBillingOptions", user.isHasUsedFreeTrial()); // in future, also use some promotional logic here
            model.addAttribute("showFreeTrialText", !user.isHasUsedFreeTrial());

            return "account/signup";
        }
    }

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createAccountEntity(RedirectAttributes attributes, HttpServletRequest request,
                                      @RequestParam(required = false) String accountName, @RequestParam AccountType accountType,
                                      @RequestParam(required = false) String emailAddress,
                                      @RequestParam(required = false) AccountBillingCycle billingCycle,
                                      @RequestParam(required = false) AccountPaymentType paymentType) {

        final boolean isFreeTrial = !userManagementService.load(getUserProfile().getUid()).isHasUsedFreeTrial();

        final String nameToUse = StringUtils.isEmpty(accountName) ? getUserProfile().nameToDisplay() : accountName;
        final String accountUid = accountBroker.createAccount(getUserProfile().getUid(), nameToUse, getUserProfile().getUid(), accountType,
                paymentType == null ? AccountPaymentType.CARD_PAYMENT : paymentType,
                billingCycle == null ? AccountBillingCycle.MONTHLY : billingCycle, isFreeTrial);

        if (!StringUtils.isEmpty(emailAddress) && EmailValidator.getInstance(false).isValid(emailAddress)) {
            userManagementService.updateEmailAddress(getUserProfile().getUid(), getUserProfile().getUid(), emailAddress);
        }

        refreshAuthorities();

        attributes.addAttribute("accountUid", accountUid);
        if (isFreeTrial) {
            addMessage(attributes, MessageType.SUCCESS, "account.trial.started", request);
            return "redirect:/account/trial";
        } else {
            addMessage(attributes, MessageType.INFO, "account.trial.exhausted", request);
            return "redirect:/account/payment/start";
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/type", method = RequestMethod.GET)
    public String changeAccountTypeOptions(Model model, @RequestParam(required = false) String accountUid) {
        Account account = !StringUtils.isEmpty(accountUid) ? accountBroker.loadAccount(accountUid)
                : accountBroker.loadPrimaryAccountForUser(getUserProfile().getUid(), false);
        User user = userManagementService.load(getUserProfile().getUid());

        if (!user.getPrimaryAccount().equals(account)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        model.addAttribute("account", account);

        int messagesLeftNow = accountGroupBroker.numberMessagesLeft(account.getUid());
        int numberGroupsNow = (int) account.getPaidGroups().stream()
                .filter(PaidGroup::isActive)
                .count();
        Map<AccountType, Integer> numberGroups = accountBroker.getNumberGroupsPerType();
        Map<AccountType, Integer> messageSize = accountBroker.getNumberMessagesPerType();
        Map<AccountType, Integer> groupSizes = accountBroker.getGroupSizeLimits();
        Map<AccountType, Integer> accountFees = accountBroker.getAccountTypeFees();
        Map<AccountType, Integer> eventLimits = accountBroker.getEventMonthlyLimits();

        Map<String, Object> changeMap = new HashMap<>();

        for (AccountType type : AccountType.values()) {
            changeMap.put(type.name() + "-GROUPS-DIFFERENCE", numberGroups.getOrDefault(type, 0)
                    - numberGroups.getOrDefault(account.getType(), 0));
            changeMap.put(type.name() + "-GROUPS-EXCEED", numberGroupsNow > numberGroups.get(type));
            changeMap.put(type.name() + "-GROUPS-NUMBER", numberGroups.get(type));
            changeMap.put(type.name() + "-GROUPS-SIZE", groupSizes.get(type));
            changeMap.put(type.name() + "-MESSAGES-LIMIT", messageSize.get(type));
            changeMap.put(type.name() + "-EVENTS-LIMIT", eventLimits.get(type));

            int newFee = accountFees.getOrDefault(type, 0);
            changeMap.put(type.name() + "-MONTHLY-FEE", newFee);
            changeMap.put(type.name() + "-FEES-DIFFERENCE", newFee - account.getSubscriptionFee());

            int messagesLeftAfter = messageSize.containsKey(type) ? 0 :
                    Math.max(0, messagesLeftNow + (messageSize.get(type) - messageSize.get(account.getType())));
            changeMap.put(type.name() + "-MESSAGES-LEFT-AFTER", messagesLeftAfter);
        }

        model.addAttribute("changeMap", changeMap);

        return "account/type";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @GetMapping(value = "/type/change")
    public String changeAccountTypeDo(@RequestParam String accountUid, @RequestParam AccountType newType,
                                      Model model, RedirectAttributes attributes, HttpServletRequest request) {

        Account account = accountBroker.loadAccount(accountUid);
        User user = userManagementService.load(getUserProfile().getUid());

        if (!user.getPrimaryAccount().equals(account)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        List<PaidGroup> currentlyPaidGroups = account.getPaidGroups().stream()
                .filter(PaidGroup::isActive)
                .collect(Collectors.toList());

        int numberToRemove = currentlyPaidGroups.size() - accountBroker.getNumberGroupsPerType().getOrDefault(newType, 0);
        if (numberToRemove > 0) {
            model.addAttribute("numberToRemove", numberToRemove);
            model.addAttribute("paidGroups", currentlyPaidGroups);
            model.addAttribute("account", account);
            model.addAttribute("newType", newType);
            return "/account/type_confirm";
        } else {
            accountBroker.changeAccountType(getUserProfile().getUid(), accountUid, newType, null);
            addMessage(attributes, MessageType.SUCCESS, "account.changetype.success", request);
            attributes.addAttribute("accountUid", accountUid);
            return "redirect:/account/view";
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @PostMapping(value = "/type/change/remove")
    public String changeAccountTypeRemoveGroups(@RequestParam String accountUid, @RequestParam(required = false) String groupUids,
                                                @RequestParam AccountType newType, RedirectAttributes attributes, HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        User user = userManagementService.load(getUserProfile().getUid());

        logger.info("Okay, removing this list of UIDs: {}", groupUids);

        if (!user.getPrimaryAccount().equals(account)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        attributes.addAttribute("accountUid", accountUid);

        try {
            List<String> splitUids = Arrays.asList(groupUids.split(","));
            accountBroker.changeAccountType(getUserProfile().getUid(), accountUid, newType, new HashSet<>(splitUids));
            addMessage(attributes, MessageType.SUCCESS, "account.changetype.success", request);
            return "redirect:/account/view";
        } catch (NullPointerException|AccountLimitExceededException e) {
            addMessage(attributes, MessageType.ERROR, "account.changetype.error", request);
            return "redirect:/account/type";
        }
    }

    @RequestMapping(value = "contact", method = RequestMethod.POST)
    public String sendContactMail(@RequestParam(required = false) String emailAddress, @RequestParam String message,
                                  RedirectAttributes attributes, HttpServletRequest request) {
        User user = userManagementService.load(getUserProfile().getUid());
        Account account = accountBroker.loadPrimaryAccountForUser(user.getUid(), false);

        if (!StringUtils.isEmpty(emailAddress)) {
            userManagementService.updateEmailAddress(user.getUid(), user.getUid(), emailAddress);
            user.setEmailAddress(emailAddress); // so it's set when inserted into email body
        }

        StringBuilder mailBody = new StringBuilder("User message:\n\n");
        mailBody.append(message);
        mailBody.append("\n\n User details: \n");
        mailBody.append(user.toString());
        mailBody.append("\n\n Account details: \n");
        mailBody.append(account == null ? "Null account" : account.toString());

        messageBroker.sendEmail(Collections.singletonList("contact@grassroot.org.za"),
                new GrassrootEmail.EmailBuilder("Account 'Contact Us' Query").content(mailBody.toString()).build());

        addMessage(attributes, MessageType.SUCCESS, "account.contact.done", request);
        return "redirect:/account/";
    }

}
