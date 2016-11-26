package za.org.grassroot.webapp.controller.webapp;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.integration.payments.PaymentMethod;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.text.DecimalFormat;
import java.util.Arrays;

/**
 * Created by luke on 2016/10/26.
 */
@Controller
@RequestMapping("/account/")
public class AccountSignUpController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AccountSignUpController.class);

    private AccountBroker accountBroker;
    private AccountBillingBroker billingBroker;

    @Autowired
    public AccountSignUpController(AccountBroker accountBroker, AccountBillingBroker billingBroker) {
        this.accountBroker = accountBroker;
        this.billingBroker = billingBroker;
    }

    @GetMapping("signup")
    public String startAccountSignup(Model model) {
        model.addAttribute("user", userManagementService.load(getUserProfile().getUid())); // may be cached (and not reflect email) if use just getuserprofile
        model.addAttribute("accountTypes", Arrays.asList(AccountType.LIGHT, AccountType.STANDARD, AccountType.HEAVY));
        return "account/signup";
    }

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createAccountEntity(Model model, @RequestParam(required = false) String accountName, @RequestParam AccountType accountType,
                                      @RequestParam(value = "emailAddress", required = false) String emailAddress) {

        final String nameToUse = StringUtils.isEmpty(accountName) ? getUserProfile().nameToDisplay() : accountName;
        final String accountUid = accountBroker.createAccount(getUserProfile().getUid(), nameToUse, getUserProfile().getUid(), accountType);

        if (!StringUtils.isEmpty(emailAddress) && EmailValidator.getInstance(false).isValid(emailAddress)) {
            userManagementService.updateEmailAddress(getUserProfile().getUid(), emailAddress);
        }

        Account createdAccount = accountBroker.loadAccount(accountUid);

        model.addAttribute("account", createdAccount);
        model.addAttribute("newAccount", true);

        model.addAttribute("method", PaymentMethod.makeEmpty());
        model.addAttribute("billingAmount", "R" + (new DecimalFormat("#.##"))
                .format((double) createdAccount.getSubscriptionFee() / 100));

        refreshAuthorities();

        logger.info("account created! here is the name: {}, and uid: {}", accountName, accountUid);
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
            accountBroker.closeAccount(getUserProfile().getUid(), accountUid);
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

}
