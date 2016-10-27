package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.integration.PaymentServiceBroker;
import za.org.grassroot.integration.domain.PaymentMethod;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Arrays;

/**
 * Created by luke on 2016/10/26.
 * major todo : harmoinize use of plural (accounts/account)
 */
@Controller
@RequestMapping("/account/")
public class AccountSignUpController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AccountSignUpController.class);

    private AccountBroker accountBroker;
    private PaymentServiceBroker paymentBroker;

    @Autowired
    public AccountSignUpController(AccountBroker accountBroker, PaymentServiceBroker paymentBroker) {
        this.accountBroker = accountBroker;
        this.paymentBroker = paymentBroker;
    }

    @GetMapping("signup")
    public String startAccountSignup(Model model) {
        model.addAttribute("user", userManagementService.load(getUserProfile().getUid())); // may be cached (and not reflect email) if use just getuserprofile
        model.addAttribute("accountTypes", Arrays.asList(AccountType.LIGHT, AccountType.STANDARD, AccountType.HEAVY));
        return "accounts/signup";
    }

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createAccountEntity(Model model, @RequestParam String accountName, @RequestParam AccountType accountType,
                                      @RequestParam(value = "emailAddress", required = false) String emailAddress) {
        // todo : add validation to client side
        final String accountUid = accountBroker.createAccount(getUserProfile().getUid(), accountName, getUserProfile().getUid(), accountType);
        if (!StringUtils.isEmpty(emailAddress)) {
            logger.info("Setting user email address");
            userManagementService.updateEmailAddress(getUserProfile().getUid(), emailAddress);
        }

        model.addAttribute("accountUid", accountUid);
        model.addAttribute("accountName", accountName);

        refreshAuthorities();

        logger.info("account created! here is the name: {}, and uid: {}", accountName, accountUid);
        return "accounts/payment";
    }

    @RequestMapping(value = "payment", method = RequestMethod.POST)
    public String attemptPayment(Model model, RedirectAttributes attributes, @RequestParam String accountUid,
                                 @RequestParam String holderName, @RequestParam String cardNumber, HttpServletRequest request) {

        // todo : client side validation, of course
        PaymentMethod paymentMethod = new PaymentMethod.Builder(holderName)
                .cardNumber(Long.valueOf(cardNumber))
                .build();

        // todo : switch this to initially ajax, with a progress bar
        if (paymentBroker.linkPaymentMethodToAccount(paymentMethod, accountUid)) {
            accountBroker.enableAccount(getUserProfile().getUid(), accountUid, LocalDate.now().plusMonths(1L));
            addMessage(attributes, MessageType.SUCCESS, "account.signup.payment.done", request);
            attributes.addAttribute("accountUid", accountUid);
            return "redirect:/account/view";
        } else {
            addMessage(model, MessageType.ERROR, "account.signup.payment.error", request);
            return "accounts/payment";
        }
    }

}
