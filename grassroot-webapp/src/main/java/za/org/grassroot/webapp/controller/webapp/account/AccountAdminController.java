package za.org.grassroot.webapp.controller.webapp.account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.integration.payments.PaymentServiceBroker;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;

/**
 * Created by luke on 2016/12/01.
 */
@Controller
@RequestMapping("/admin/accounts/")
@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
public class AccountAdminController extends BaseController {

    private final AccountBroker accountBroker;
    private final AccountBillingBroker billingBroker;
    private final Environment environment;

    private PaymentServiceBroker paymentServiceBroker;

    @Autowired
    public AccountAdminController(AccountBroker accountBroker, AccountBillingBroker billingBroker, Environment environment) {
        this.accountBroker = accountBroker;
        this.billingBroker = billingBroker;
        this.environment = environment;
    }

    @Autowired(required = false)
    public void setPaymentServiceBroker(PaymentServiceBroker paymentServiceBroker) {
        this.paymentServiceBroker = paymentServiceBroker;
    }

    /**
     * Methods to create institutional accounts and designate their administrators
     */
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/home")
    public String listAccounts(Model model) {
        model.addAttribute("accounts", new ArrayList<>(accountBroker.loadAllAccounts(true)));
        return "admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/disable")
    public String disableAccount(@RequestParam("accountUid") String accountUid, RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.disableAccount(getUserProfile().getUid(), accountUid, "disabled by admin user", true, false); // todo : have a form to input this
        addMessage(attributes, MessageType.INFO, "admin.accounts.disabled", request);
        return "redirect:home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/close", method = RequestMethod.GET)
    public String makeAccountInvisible(@RequestParam("accountUid") String accountUid, RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.closeAccount(getUserProfile().getUid(), accountUid, false);
        addMessage(attributes, MessageType.INFO, "admin.accounts.invisible", request);
        return "redirect:home";
    }

    // todo : have options to force set the amount to bill
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/bill", method = RequestMethod.POST)
    public String generateBill(@RequestParam String accountUid, @RequestParam(required = false) boolean generateStatement,
                                @RequestParam(required = false) boolean triggerPayment, RedirectAttributes attributes, HttpServletRequest request) {
        billingBroker.generateBillOutOfCycle(accountUid, generateStatement, triggerPayment);
        addMessage(attributes, MessageType.INFO, "admin.accounts.bill.generated", request);
        return "redirect:home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/balance", method = RequestMethod.POST)
    public String changeBalance(@RequestParam String accountUid, @RequestParam Integer newBalance,
                                RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.updateAccountBalance(getUserProfile().getUid(), accountUid, newBalance);
        addMessage(attributes, MessageType.INFO, "admin.accounts.balance.changed", request);
        return "redirect:home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/reset/dates", method = RequestMethod.GET)
    public String resetAccountBillingDates(RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (!environment.acceptsProfiles("production")) {
            accountBroker.resetAccountBillingDates(Instant.now());
            addMessage(redirectAttributes, MessageType.INFO, "admin.accounts.reset", request);
        } else {
            addMessage(redirectAttributes, MessageType.ERROR, "admin.accounts.production", request);
        }
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/reset/billing", method = RequestMethod.GET)
    public String triggerBilling(@RequestParam(required = false) boolean sendEmails, @RequestParam(required = false) boolean sendNotifications,
                                 RedirectAttributes attributes, HttpServletRequest request) {
        if (!environment.acceptsProfiles("production")) {
            billingBroker.calculateStatementsForDueAccounts(sendEmails, sendNotifications);
            addMessage(attributes, MessageType.INFO, "admin.accounts.billing.done", request);
        } else {
            addMessage(attributes, MessageType.ERROR, "admin.accounts.production", request);
        }
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/reset/payments", method = RequestMethod.GET)
    public String triggerPayments(@RequestParam RedirectAttributes attributes, @RequestParam HttpServletRequest request) {
        if (!environment.acceptsProfiles("production") && paymentServiceBroker != null) {
            paymentServiceBroker.processAccountPaymentsOutstanding();
            addMessage(attributes, MessageType.INFO, "admin.accounts.payments.done", request);
        } else {
            addMessage(attributes, MessageType.ERROR, "admin.accounts.production", request);
        }
        return "redirect:/admin/accounts/home";
    }

    // todo: wire this up properly
    public void changeAccountSettings(Account account) {
        accountBroker.updateBillingEmail(getUserProfile().getUid(), account.getUid(), account.getBillingUser().getEmailAddress());
        accountBroker.updateAccountGroupLimits(getUserProfile().getUid(), account.getUid(), account.getMaxNumberGroups(),
                account.getMaxSizePerGroup(), account.getMaxSubGroupDepth());
        accountBroker.updateAccountMessageSettings(getUserProfile().getUid(), account.getUid(), account.getFreeFormMessages(),
                account.getFreeFormCost());
    }

}
