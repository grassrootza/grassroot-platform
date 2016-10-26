package za.org.grassroot.webapp.controller.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.integration.PaymentServiceBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

/**
 * Created by luke on 2016/10/26.
 * major todo : harmoinize use of plural (accounts/account)
 */
@Controller
@RequestMapping("/account/")
public class AccountSignUpController extends BaseController {

    private AccountBroker accountBroker;
    private PaymentServiceBroker paymentBroker;

    @Autowired
    public AccountSignUpController(AccountBroker accountBroker, PaymentServiceBroker paymentBroker) {
        this.accountBroker = accountBroker;
        this.paymentBroker = paymentBroker;
    }

    @GetMapping("signup")
    public String startAccountSignup(Model model) {
        model.addAttribute("user", getUserProfile());
        model.addAttribute("accountTypes", Arrays.asList(AccountType.LIGHT, AccountType.STANDARD, AccountType.HEAVY));
        return "accounts/signup";
    }

    @PostMapping("create")
    public String createAccountEntity(Model model, @RequestParam String accountName, @RequestParam AccountType accountType) {
        final String accountUid = accountBroker.createAccount(getUserProfile().getUid(), accountName, getUserProfile().getUid(), null, accountType);
        model.addAttribute("accountUid", accountUid);
        model.addAttribute("accountName", accountName);
        return "accounts/payment";
    }

    @PostMapping("payment")
    public String attemptPayment(Model model, RedirectAttributes attributes, @RequestParam String accountUid,
                                 HttpServletRequest request) {
        if (paymentBroker.linkPaymentMethodToAccount(accountUid)) {
            addMessage(attributes, MessageType.SUCCESS, "account.signup.payment.done", request);
            attributes.addAttribute("accountUid", accountUid);
            return "redirect:/accounts/view";
        } else {
            addMessage(model, MessageType.ERROR, "account.signup.payment.error", request);
            return "accounts/payment";
        }
    }

}
