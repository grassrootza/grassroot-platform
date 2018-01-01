package za.org.grassroot.webapp.controller.webapp.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.integration.messaging.GrassrootEmail;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.integration.payments.PaymentBroker;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by luke on 2016/12/01.
 */
@Controller
@RequestMapping("/admin/accounts/")
@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
public class AccountAdminController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AccountAdminController.class);

    private final AccountBroker accountBroker;
    private final AccountBillingBroker billingBroker;
    private final Environment environment;

    private PaymentBroker paymentBroker;
    private MessagingServiceBroker messagingBroker;

    @Autowired
    public AccountAdminController(AccountBroker accountBroker, AccountBillingBroker billingBroker, Environment environment, MessagingServiceBroker messagingBroker) {
        this.accountBroker = accountBroker;
        this.billingBroker = billingBroker;
        this.environment = environment;
        this.messagingBroker = messagingBroker;
    }

    @Autowired(required = false)
    public void setPaymentBroker(PaymentBroker paymentBroker) {
        this.paymentBroker = paymentBroker;
    }

    /**
     * Methods to create Grassroot Extra accounts and designate their administrators
     */
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/home", method = RequestMethod.GET)
    public String listAccounts(Model model, @RequestParam(required = false) Boolean showClosed) {
        model.addAttribute("accounts", new ArrayList<>(accountBroker.loadAllAccounts(showClosed == null ? true : showClosed, null, null)));
        return "admin/accounts/home";
    }

    @RequestMapping(value = "/modify", method = RequestMethod.GET)
    public String modifyAccountGeneral(Model model, @RequestParam String accountUid) {
        model.addAttribute("account", accountBroker.loadAccount(accountUid));
        Map<Boolean, String> onOffOptions = new HashMap<>(); // slightly kludgy, but least bad option
        onOffOptions.put(true, "Yes");
        onOffOptions.put(false, "No");
        model.addAttribute("onOffOptions", onOffOptions);
        return "admin/accounts/modify";
    }

    @RequestMapping(value = "/modify/do", method = RequestMethod.POST)
    public String modifyAccountDo(@RequestParam String accountUid,
                                  @RequestParam AccountType accountType,
                                  @RequestParam long subscriptionFee,
                                  @RequestParam boolean visible,
                                  @RequestParam boolean chargePerMessage,
                                  @RequestParam long costPerMessage,
                                  RedirectAttributes attributes, HttpServletRequest request) {
        attributes.addAttribute("accountUid", accountUid);

        Account account = accountBroker.loadAccount(accountUid);
        if (!account.isVisible() == visible) {
            accountBroker.setAccountVisibility(getUserProfile().getUid(), accountUid, visible);
        }

        accountBroker.modifyAccount(getUserProfile().getUid(), accountUid, accountType, subscriptionFee,
                chargePerMessage, costPerMessage);

        addMessage(attributes, MessageType.SUCCESS, "admin.accounts.modified", request);
        return "redirect:/admin/accounts/modify";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/deposit", method = RequestMethod.GET)
    public String listDepositAccounts(Model model) {
        model.addAttribute("accounts", new ArrayList<>(accountBroker.loadAllAccounts(true, AccountPaymentType.DIRECT_DEPOSIT, null)));
        return "admin/accounts/home";
    }





    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/disable", method = RequestMethod.POST)
    public String disableAccount(@RequestParam("accountUid") String accountUid, RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.disableAccount(getUserProfile().getUid(), accountUid, "disabled by admin user", false, false); // todo : have a form to input this
        addMessage(attributes, MessageType.INFO, "admin.accounts.disabled", request);
        return "redirect:home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/enable", method = RequestMethod.POST)
    public String enableAccount(@RequestParam("accountUid") String accountUid, @RequestParam(required = false) Boolean sendEmail,
                                RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.enableAccount(getUserProfile().getUid(), accountUid, null, AccountPaymentType.DIRECT_DEPOSIT,
                false, false);
        Account account = accountBroker.loadAccount(accountUid);
        if (sendEmail != null && sendEmail) {
            GrassrootEmail.EmailBuilder builder = new GrassrootEmail.EmailBuilder("Grassroot Extra Account Enabled")
                    .address(account.getBillingUser().getEmailAddress())
                    .content("Hello!\nYour account is enabled. Great!\nGrassroot");
            messagingBroker.sendEmail(Collections.singletonList(account.getBillingUser().getEmailAddress()), builder.build());
        }
        addMessage(attributes, MessageType.INFO, "admin.accounts.enabled", request);
        return "redirect:home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/close", method = RequestMethod.GET)
    public String closeAccount(@RequestParam("accountUid") String accountUid, RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.closeAccount(getUserProfile().getUid(), accountUid, false);
        addMessage(attributes, MessageType.INFO, "admin.accounts.invisible", request);
        return "redirect:home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/change/subscription", method = RequestMethod.POST)
    public String changeSubcription(@RequestParam String accountUid, @RequestParam Integer newSubscriptionFee,
                                    RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.updateAccountFee(getUserProfile().getUid(), accountUid, newSubscriptionFee);
        addMessage(attributes, MessageType.INFO, "admin.accounts.fee.changed", request);
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/change/balance", method = RequestMethod.POST)
    public String changeBalance(@RequestParam String accountUid, @RequestParam Integer newBalance,
                                RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.updateAccountBalance(getUserProfile().getUid(), accountUid, newBalance);
        addMessage(attributes, MessageType.INFO, "admin.accounts.balance.changed", request);
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/bill", method = RequestMethod.POST)
    public String generateBill(@RequestParam String accountUid, @RequestParam(required = false) Long billAmount,
                               @RequestParam(required = false) boolean generateStatement, @RequestParam(required = false) boolean triggerPayment,
                               RedirectAttributes attributes, HttpServletRequest request) {
        billingBroker.generateBillOutOfCycle(accountUid, generateStatement, triggerPayment, billAmount, true);
        addMessage(attributes, MessageType.INFO, "admin.accounts.bill.generated", request);
        return "redirect:home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/change/payment/method", method = RequestMethod.POST)
    public String changePaymentMethod(@RequestParam String accountUid, @RequestParam AccountPaymentType paymentType,
                                      RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.updateAccountPaymentType(getUserProfile().getUid(), accountUid, paymentType);
        addMessage(attributes, MessageType.SUCCESS, "admin.accounts.payment.type.changed", request);
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/change/billingdate", method = RequestMethod.POST)
    public String toggleBilling(@RequestParam String accountUid, @RequestParam(required = false) boolean stopBilling,
                                @RequestParam(required = false) LocalDateTime billingDateTime,
                                RedirectAttributes attributes, HttpServletRequest request) {
        log.info("stopBilling: {}", stopBilling);
        billingBroker.forceUpdateBillingDate(getUserProfile().getUid(), accountUid, stopBilling ? null : billingDateTime);
        addMessage(attributes, MessageType.INFO, "admin.accounts.billingdate.changed", request);
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/payments/stop", method = RequestMethod.GET)
    public String togglePayments(@RequestParam String accountUid, RedirectAttributes attributes, HttpServletRequest request) {
        billingBroker.haltAccountPayments(getUserProfile().getUid(), accountUid);
        addMessage(attributes, MessageType.INFO, "admin.accounts.payment.stopped", request);
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/records/list", method = RequestMethod.GET)
    public String seeUnpaidBills(@RequestParam(required = false) String accountUid,
                                 @RequestParam(required = false) boolean unpaidOnly,
                                 Model model) {
        model.addAttribute("records", billingBroker.loadBillingRecords(accountUid, unpaidOnly,
                new Sort(Sort.Direction.DESC, "createdDateTime")));
        if (!StringUtils.isEmpty(accountUid)) {
            model.addAttribute("account", accountBroker.loadAccount(accountUid));
        }
        return "admin/accounts/records";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/bill/paydate/change", method = RequestMethod.POST)
    public String changeBillPaymentDate(@RequestParam String recordUid, LocalDateTime newDate,
                                        RedirectAttributes attributes, HttpServletRequest request) {
        billingBroker.changeBillPaymentDate(getUserProfile().getUid(), recordUid, newDate);
        addMessage(attributes, MessageType.INFO, "admin.accounts.billpaydate.changed", request);
        return "redirect:/admin/accounts/bill/list/unpaid";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/bill/pay/toggle", method = RequestMethod.GET)
    public String toggleBillPayment(@RequestParam String recordUid, @RequestParam(required = false) String accountUid,
                                    RedirectAttributes attributes, HttpServletRequest request) {
        billingBroker.togglePaymentStatus(recordUid);
        addMessage(attributes, MessageType.INFO, "admin.accounts.billpaid.toggled", request);
        if (!StringUtils.isEmpty(accountUid)) {
            attributes.addAttribute("accountUid", accountUid);
        }
        return "redirect:/admin/accounts/records/list";
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
    @RequestMapping(value = "/reset/billing", method = RequestMethod.POST)
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
    public String triggerPayments(RedirectAttributes attributes, HttpServletRequest request) {
        if (!environment.acceptsProfiles("production") && paymentBroker != null) {
            billingBroker.processBillsDueForPayment();
            addMessage(attributes, MessageType.INFO, "admin.accounts.payments.done", request);
        } else {
            addMessage(attributes, MessageType.ERROR, "admin.accounts.production", request);
        }
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/limits", method = RequestMethod.GET)
    public String adjustGroupLimits(Model model, @RequestParam String accountUid) {
        model.addAttribute("account", accountBroker.loadAccount(accountUid));
        return "admin/accounts/limits";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/limits", method = RequestMethod.POST)
    public String resetGroupLimits(RedirectAttributes attributes, HttpServletRequest request, @RequestParam String accountUid,
                                   @RequestParam int numberGroups, @RequestParam int sizePerGroup,
                                   @RequestParam int subGroupDepth, @RequestParam int messagesPerMonth,
                                   @RequestParam int todosPerMonth, @RequestParam int eventsPerMonth) {
        try {
            log.info("events per month in controller: {}", eventsPerMonth);
            accountBroker.updateAccountGroupLimits(getUserProfile().getUid(), accountUid, numberGroups, sizePerGroup,
                    subGroupDepth, messagesPerMonth, todosPerMonth, eventsPerMonth);
            addMessage(attributes, MessageType.SUCCESS, "admin.accounts.limits.changed", request);
        } catch (Exception e) {
            log.error("Error! Exception thrown updating group limits: {}", e.toString());
            addMessage(attributes, MessageType.ERROR, "admin.accounts.limits.error", request);
        }
        return "redirect:/admin/accounts/home";
    }

}
