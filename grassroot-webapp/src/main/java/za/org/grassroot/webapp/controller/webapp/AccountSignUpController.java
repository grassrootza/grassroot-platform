package za.org.grassroot.webapp.controller.webapp;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.integration.payments.PaymentMethod;
import za.org.grassroot.integration.payments.PaymentServiceBroker;
import za.org.grassroot.integration.payments.peachp.PaymentRedirectPP;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

/**
 * Created by luke on 2016/10/26.
 */
@Controller
@RequestMapping("/account/")
public class AccountSignUpController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AccountSignUpController.class);

    private AccountBroker accountBroker;
    private AccountBillingBroker billingBroker;
    private PaymentServiceBroker paymentBroker;

    @Autowired
    public AccountSignUpController(AccountBroker accountBroker, AccountBillingBroker billingBroker, PaymentServiceBroker paymentBroker) {
        this.accountBroker = accountBroker;
        this.billingBroker = billingBroker;
        this.paymentBroker = paymentBroker;
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

    @RequestMapping(value = "enable", method = RequestMethod.POST)
    public String attemptPayment(Model model, RedirectAttributes attributes, @RequestParam String accountUid,
                                 @ModelAttribute("method") PaymentMethod paymentMethod, HttpServletRequest request) {

        // todo : work out how to do a progress bar type thing
        // todo : better description of error if no 3d secure

        AccountBillingRecord record = billingBroker.generateSignUpBill(accountUid);
        final String returnUrl = "https://" + request.getServerName() + ":" + request.getServerPort()
                + "/cardauth/3dsecure/response/new";

        PaymentRedirectPP paymentResponse = paymentBroker.asyncPaymentInitiate(accountUid, paymentMethod,
                record.getTotalAmountToPay(), returnUrl);

        if (paymentResponse != null) {
            for (Map<String, String> parameter: paymentResponse.getParameters()) {
                attributes.addAttribute(parameter.get("name"), parameter.get("value"));
            }
            return "redirect:" + paymentResponse.getUrl();
        } else {
            addMessage(model, MessageType.ERROR, "account.signup.payment.error", request);
            return "account/payment";
        }
    }

    @RequestMapping(value = "payment/done", method = RequestMethod.GET)
    public String paymentDone(@RequestParam String paymentId, @RequestParam String paymentRef, @RequestParam boolean succeeded,
                              RedirectAttributes attributes, HttpServletRequest request) {

        if (succeeded) {
            Account account = accountBroker.loadByPaymentRef(paymentId);
            logger.info("Found account: {}", account);
            accountBroker.enableAccount(getUserProfile().getUid(), account.getUid(), LocalDate.now().plusMonths(1L), paymentRef);
            addMessage(attributes, MessageType.SUCCESS, "account.signup.payment.done", request);
            attributes.addAttribute("accountUid", accountBroker.loadUsersAccount(getUserProfile().getUid()));
            return "redirect:/account/view";
        } else {
            return "account/payment/error";
        }

    }

}
