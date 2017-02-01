package za.org.grassroot.webapp.controller.webapp.account;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.PaidGroup;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.integration.email.EmailSendingBroker;
import za.org.grassroot.integration.email.GrassrootEmail;
import za.org.grassroot.integration.payments.PaymentMethod;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/10/26.
 */
@Controller
@RequestMapping("/account/")
public class AccountSignUpController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AccountSignUpController.class);

    private final AccountBroker accountBroker;
    private final AccountBillingBroker billingBroker;
    private final AccountGroupBroker accountGroupBroker;
    private final EmailSendingBroker emailSendingBroker;

    @Autowired
    public AccountSignUpController(AccountBroker accountBroker, AccountBillingBroker billingBroker,
                                   AccountGroupBroker accountGroupBroker, EmailSendingBroker emailSendingBroker) {
        this.accountBroker = accountBroker;
        this.billingBroker = billingBroker;
        this.accountGroupBroker = accountGroupBroker;
        this.emailSendingBroker = emailSendingBroker;
    }

    @GetMapping("signup")
    public String startAccountSignup(Model model, @RequestParam(required = false) String accountType) {
        User user = userManagementService.load(getUserProfile().getUid());
        logger.info("accountType in parameter: {}", accountType);
        if (user.getAccountAdministered() != null) {
            return user.getAccountAdministered().isEnabled() ? "redirect:/account/type" : "redirect:/account";
        } else {
            model.addAttribute("user", user); // may be cached (and not reflect email) if use just getuserprofile
            model.addAttribute("accountTypes", Arrays.asList(AccountType.LIGHT, AccountType.STANDARD, AccountType.HEAVY));
            if (!StringUtils.isEmpty(accountType) && AccountType.contains(accountType)) {
                model.addAttribute("defaultType", AccountType.valueOf(accountType));
            } else {
                model.addAttribute("defaultType", AccountType.STANDARD);
            }
            return "account/signup";
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @GetMapping(value = "/type")
    public String changeAccountTypeOptions(Model model, @RequestParam(required = false) String accountUid) {
        Account account = !StringUtils.isEmpty(accountUid) ? accountBroker.loadAccount(accountUid)
                : accountBroker.loadUsersAccount(getUserProfile().getUid());
        User user = userManagementService.load(getUserProfile().getUid());

        if (!user.getAccountAdministered().equals(account)) {
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

        Map<String, Object> changeMap = new HashMap<>();

        for (AccountType type : AccountType.values()) {
            changeMap.put(type.name() + "-GROUPS-DIFFERENCE", numberGroups.getOrDefault(type, 0)
                    - numberGroups.getOrDefault(account.getType(), 0));
            changeMap.put(type.name() + "-GROUPS-EXCEED", numberGroupsNow > numberGroups.get(type));
            changeMap.put(type.name() + "-GROUPS-NUMBER", numberGroups.get(type));
            changeMap.put(type.name() + "-GROUPS-SIZE", groupSizes.get(type));
            changeMap.put(type.name() + "-MESSAGES-LIMIT", messageSize.get(type));

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

        if (!user.getAccountAdministered().equals(account)) {
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

        if (!user.getAccountAdministered().equals(account)) {
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

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createAccountEntity(Model model, @RequestParam(required = false) String accountName, @RequestParam AccountType accountType,
                                      @RequestParam(value = "emailAddress", required = false) String emailAddress) {

        final String nameToUse = StringUtils.isEmpty(accountName) ? getUserProfile().nameToDisplay() : accountName;
        final String accountUid = accountBroker.createAccount(getUserProfile().getUid(), nameToUse, getUserProfile().getUid(), accountType);

        if (!StringUtils.isEmpty(emailAddress) && EmailValidator.getInstance(false).isValid(emailAddress)) {
            userManagementService.updateEmailAddress(getUserProfile().getUid(), emailAddress);
        }

        refreshAuthorities();

        return "account/payment_options";
    }

    // todo : move this to payment controller (and/or just reuse disabled/enabled payment
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "payment_now", method = RequestMethod.GET)
    public String creditCardNow(Model model, @RequestParam String accountUid) {
        Account createdAccount = accountBroker.loadAccount(accountUid);

        model.addAttribute("account", createdAccount);
        model.addAttribute("newAccount", true);

        model.addAttribute("method", PaymentMethod.makeEmpty());
        model.addAttribute("billingAmount", "R" + (new DecimalFormat("#.##"))
                .format((double) createdAccount.getSubscriptionFee() / 100));


        logger.info("account created! here is the name: {}, and uid: {}", createdAccount.getAccountName(), accountUid);
        return "account/payment";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "close", method = RequestMethod.POST)
    public String closeAccount(@RequestParam String accountUid, @RequestParam String confirmText,
                               RedirectAttributes attributes, HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        User loadedUser = userManagementService.load(getUserProfile().getUid());

        if (!account.getAdministrators().contains(loadedUser)) {
            permissionBroker.validateSystemRole(loadedUser, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        if ("confirmed".equalsIgnoreCase(confirmText.trim())) {
            billingBroker.generateClosingBill(getUserProfile().getUid(), accountUid);
            accountBroker.closeAccount(getUserProfile().getUid(), accountUid, false);
            addMessage(attributes, MessageType.INFO, "account.closed.done", request);
            refreshAuthorities();
            return "redirect:/home";
        } else {
            addMessage(attributes, MessageType.ERROR, "account.closed.error", request);
            attributes.addAttribute("accountUid", account.getUid());
            if (account.isEnabled()) {
                return "redirect:/account/view";
            } else {
                return "redirect:/account/disabled";
            }
        }
    }

    @RequestMapping(value = "contact", method = RequestMethod.POST)
    public String sendContactMail(@RequestParam(required = false) String emailAddress, @RequestParam String message,
                                  RedirectAttributes attributes, HttpServletRequest request) {
        User user = userManagementService.load(getUserProfile().getUid());
        Account account = accountBroker.loadUsersAccount(user.getUid());

        if (!StringUtils.isEmpty(emailAddress)) {
            userManagementService.updateEmailAddress(user.getUid(), emailAddress);
            user.setEmailAddress(emailAddress); // so it's set when inserted into email body
        }

        StringBuilder mailBody = new StringBuilder("User message:\n\n");
        mailBody.append(message);
        mailBody.append("\n\n User details: \n");
        mailBody.append(user.toString());
        mailBody.append("\n\n Account details: \n");
        mailBody.append(account == null ? "Null account" : account.toString());

        emailSendingBroker.sendMail(new GrassrootEmail.EmailBuilder("Account 'Contact Us' Query")
                .address("contact@grassroot.org.za")
                .content(mailBody.toString()).build());

        addMessage(attributes, MessageType.SUCCESS, "account.contact.done", request);
        return "redirect:/account/";
    }

}
