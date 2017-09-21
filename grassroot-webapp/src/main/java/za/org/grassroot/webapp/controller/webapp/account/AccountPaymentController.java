package za.org.grassroot.webapp.controller.webapp.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.integration.exception.PaymentMethodFailedException;
import za.org.grassroot.integration.payments.PaymentBroker;
import za.org.grassroot.integration.payments.PaymentMethod;
import za.org.grassroot.integration.payments.PaymentResponse;
import za.org.grassroot.integration.payments.PaymentResultType;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountSponsorshipBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.enums.CombinedPaymentOption;

import javax.servlet.http.HttpServletRequest;
import java.text.DecimalFormat;
import java.util.Map;

import static za.org.grassroot.core.enums.AccountBillingCycle.ANNUAL;
import static za.org.grassroot.core.enums.AccountBillingCycle.MONTHLY;
import static za.org.grassroot.core.enums.AccountPaymentType.CARD_PAYMENT;
import static za.org.grassroot.core.enums.AccountPaymentType.DIRECT_DEPOSIT;
import static za.org.grassroot.webapp.enums.CombinedPaymentOption.ANNUAL_DEPOSIT;
import static za.org.grassroot.webapp.enums.CombinedPaymentOption.MONTHLY_CARD;

/**
 * Created by luke on 2016/11/25.
 */
@Controller
@RequestMapping("/account/payment")
@SessionAttributes("user")
@PropertySource(value = "${grassroot.payments.properties}", ignoreResourceNotFound = true)
public class AccountPaymentController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AccountPaymentController.class);

    private static long PAYMENT_VERIFICATION_AMT = 1000; // todo : make proportional to account size
    private static final int ENABLE = 100;
    private static final int UPDATE = 200;

    @Value("${grassroot.payments.auth.path:/auth/incoming}")
    private String authorizationPath;
    @Value("${grassroot.payments.deposit.details:DepositDetails}")
    private String depositDetails;
    @Value("${grassroot.payments.email.address:payments@grassroot.org.za}")
    private String paymentsEmail;

    private final AccountBroker accountBroker;
    private final AccountBillingBroker accountBillingBroker;
    private final PaymentBroker paymentBroker;
    private final AccountSponsorshipBroker sponsorshipBroker;

    @Autowired
    public AccountPaymentController(AccountBroker accountBroker, AccountBillingBroker accountBillingBroker,
                                    PaymentBroker paymentBroker, AccountSponsorshipBroker sponsorshipBroker) {
        this.accountBroker = accountBroker;
        this.accountBillingBroker = accountBillingBroker;
        this.paymentBroker = paymentBroker;
        this.sponsorshipBroker = sponsorshipBroker;
    }

    @RequestMapping(value = "start", method = RequestMethod.GET)
    public String initialAccountPayment(Model model, @RequestParam String accountUid,
                                        @RequestParam(required = false) AccountPaymentType paymentType) {
        Account account = accountBroker.loadAccount(accountUid);
        User user = userManagementService.load(getUserProfile().getUid());

        if (!account.getAdministrators().contains(user)) {
            throw new AccessDeniedException("Error! Only an administrator of the account can pay for it, except via sponsorship request");
        }

        return DIRECT_DEPOSIT.equals(paymentType)
                ? loadDebitInstruction(model, account)
                : loadCreditCardForm(model, account, true);
    }

    @RequestMapping(value = "deposit", method = RequestMethod.GET)
    public String displayDepositDetails(Model model, @RequestParam String accountUid, @RequestParam(required = false) String requestUid) {
        // todo : create a reference number ...
        return loadDebitInstruction(model, accountBroker.loadAccount(accountUid));
    }

    @RequestMapping(value = "process", method = RequestMethod.POST)
    public String initiatePayment(Model model, RedirectAttributes attributes, @RequestParam String accountUid,
                                  @ModelAttribute("method") PaymentMethod paymentMethod, HttpServletRequest request) {
        AccountBillingRecord record = accountBillingBroker.generateSignUpBill(accountUid);
        return handleInitiatingPayment(accountUid, paymentMethod, record, ENABLE, model, attributes, request);
    }

    @RequestMapping(value = "redirect", method = RequestMethod.GET)
    public String asyncPaymentTrafficControl(@RequestParam String paymentId, @RequestParam(required = false) String paymentRef,
                                             @RequestParam boolean succeeded, @RequestParam(required = false) String failureDescription,
                                             Model model) {
        model.addAttribute("paymentId", paymentId);
        model.addAttribute("paymentRef", paymentRef);
        model.addAttribute("succeeded", succeeded);
        model.addAttribute("failureDescription", failureDescription);
        return "account/done_redirect";
    }

    @RequestMapping(value = "done", method = RequestMethod.POST)
    public String asyncPaymentDone(@RequestParam String paymentId, @RequestParam(required = false) String paymentRef,
                                   @RequestParam boolean succeeded, @RequestParam(required = false) String failureDescription,
                                   RedirectAttributes attributes, HttpServletRequest request) {
        AccountBillingRecord record = accountBillingBroker.fetchRecordByPayment(paymentId);
        Account account = record.getAccount();
        int typeOfCall = account.isEnabled() ? UPDATE : ENABLE;
        if (succeeded) {
            return handleSuccess(paymentId, paymentRef, typeOfCall, attributes, request);
        } else {
            return handleError(typeOfCall, attributes, request, failureDescription);
        }
    }

    @RequestMapping(value = "trial", method = RequestMethod.GET)
    public String payForTrialAccountBeforeExpiry(Model model, @RequestParam String accountUid,
                                                 @RequestParam CombinedPaymentOption combinedPaymentOption,
                                                 @RequestParam(required = false) String errorDescription) {
        User user = userManagementService.load(getUserProfile().getUid());
        Account account = accountBroker.loadAccount(accountUid);

        if (!account.getAdministrators().contains(user)) {
            throw new AccessDeniedException("Only users administering account can pay prior to trial expiry");
        }

        AccountBillingCycle billingCycle = combinedPaymentOption == null ? account.getBillingCycle() :
                MONTHLY_CARD.equals(combinedPaymentOption) ? MONTHLY : ANNUAL;

        // make sure to pass null to payment type otherwise this will end the free trial even if something goes wrong
        accountBroker.updateAccountPaymentCycleAndMethod(user.getUid(), accountUid, null, billingCycle, false);

        return  loadAppropriatePaymentForm(model, combinedPaymentOption, account, errorDescription);
    }

    // for disabled accounts
    @RequestMapping(value = "retry", method = RequestMethod.GET)
    public String retryAccountPayment(Model model,
                                      @RequestParam(required = false) String errorDescription,
                                      @RequestParam(required = false) CombinedPaymentOption combinedPaymentOption) {
        User user = userManagementService.load(getUserProfile().getUid());
        Account account = user.getPrimaryAccount();

        if (account == null) {
            throw new AccessDeniedException("Must have an account before trying to retry payment");
        }

        AccountPaymentType paymentType = combinedPaymentOption == null ? account.getDefaultPaymentType() :
                ANNUAL_DEPOSIT.equals(combinedPaymentOption) ? DIRECT_DEPOSIT : CARD_PAYMENT;
        AccountBillingCycle billingCycle = combinedPaymentOption == null ? account.getBillingCycle() :
                MONTHLY_CARD.equals(combinedPaymentOption) ? MONTHLY : ANNUAL;

        if (!paymentType.equals(account.getDefaultPaymentType()) || !billingCycle.equals(account.getBillingCycle())) {
            accountBroker.updateAccountPaymentCycleAndMethod(user.getUid(), account.getUid(), paymentType, billingCycle, false);
        }

        return  loadAppropriatePaymentForm(model, combinedPaymentOption, account, errorDescription);
    }

    private String loadAppropriatePaymentForm(Model model, CombinedPaymentOption combinedPaymentOption, Account account, String errorDescription) {
        if (ANNUAL_DEPOSIT.equals(combinedPaymentOption)) {
            return loadDebitInstruction(model, account);
        } else {
            if (!StringUtils.isEmpty(errorDescription)) {
                model.addAttribute("errorDescription", errorDescription);
            }
            return loadCreditCardForm(model, account, true);
        }
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "change", method = RequestMethod.GET)
    public String changePaymentCard(Model model, @RequestParam(required = false) String accountUid,
                                    @RequestParam(required = false) String errorDescription) {
        Account account = StringUtils.isEmpty(accountUid) ? accountBroker.loadPrimaryAccountForUser(getUserProfile().getUid(), false) :
                accountBroker.loadAccount(accountUid);

        if (!StringUtils.isEmpty(errorDescription)) {
            model.addAttribute("errorDescription", errorDescription);
        }

        return loadCreditCardForm(model, account, false);
    }

    private String loadCreditCardForm(Model model, Account account, boolean newAccount) {
        model.addAttribute("account", account);
        model.addAttribute("newAccount", newAccount);
        model.addAttribute("method", PaymentMethod.makeEmpty());
        model.addAttribute("billingAmount", "R" + (new DecimalFormat("#,###.##").format(calculateAmountToPay(account))));
        return "account/payment";
    }

    private String loadDebitInstruction(Model model, Account account) {
        model.addAttribute("reference", account.getAccountName());
        model.addAttribute("details", depositDetails);
        model.addAttribute("paymentsEmail", paymentsEmail);
        model.addAttribute("billingAmount", "R" + (new DecimalFormat("#,###.##").format(calculateAmountToPay(account))));
        return "account/deposit";
    }

    private double calculateAmountToPay(Account account) {
        return Math.round((double) account.calculatePeriodCost() / 100);
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "change", method = RequestMethod.POST)
    public String changePaymentDo(Model model, RedirectAttributes attributes, @RequestParam String accountUid,
                                  @ModelAttribute("method") PaymentMethod paymentMethod, HttpServletRequest request) {
        AccountBillingRecord record = accountBillingBroker.generatePaymentChangeBill(accountUid, PAYMENT_VERIFICATION_AMT);
        return handleInitiatingPayment(accountUid, paymentMethod, record, UPDATE, model, attributes, request);
    }

    private String handleInitiatingPayment(String accountUid, PaymentMethod method, AccountBillingRecord record,
                                           int enableOrUpdate, Model model, RedirectAttributes attributes, HttpServletRequest request) {
        final String returnUrl = "https://" + request.getServerName() + ":" + request.getServerPort() + authorizationPath;
        logger.info("sending payment request with this URL: {}", returnUrl);

        try {
            PaymentResponse response = paymentBroker.asyncPaymentInitiate(accountUid, method, record, returnUrl);
            if (!StringUtils.isEmpty(response.getRedirectUrl())) {
                for (Map<String, String> parameter : response.getRedirectParams()) {
                    attributes.addAttribute(parameter.get("name"), parameter.get("value"));
                }
                logger.info("Redirect Params: {}", response.getRedirectParams());
                model.addAttribute("redirectUrl", response.getRedirectUrl());
                model.addAttribute("redirectParams", response.getRedirectParams());
                return "account/payment_confirm";
            } else if (response.isSuccessful()) {
                return handleSuccess(response.getThisPaymentId(), response.getReference(), enableOrUpdate, attributes, request);
            } else {
                return handleError(enableOrUpdate, attributes, request, response.getDescription());
            }
        } catch (PaymentMethodFailedException e) {
            String description;
            if (e.getPaymentError() != null) {
                if (PaymentResultType.NOT_IN_3D.equals(e.getPaymentError().getResult().getType())) {
                    description = getMessage("account.payment.not3d");
                } else {
                    description = e.getPaymentError().getResult().getDescription();
                }
            } else {
                description = null;
            }
            return handleError(enableOrUpdate, attributes, request, description);
        }
    }

    private String handleSuccess(String paymentId, String paymentRef, int enableOrUpdateAccount,
                                 RedirectAttributes attributes, HttpServletRequest request) {
        logger.info("handling successful payment, enable set to true = {}", enableOrUpdateAccount == ENABLE);
        AccountBillingRecord record = accountBillingBroker.fetchRecordByPayment(paymentId);
        Account account = record.getAccount();
        if (enableOrUpdateAccount == ENABLE) {
            accountBroker.enableAccount(getUserProfile().getUid(), account.getUid(), paymentRef, CARD_PAYMENT, true, true);
            sponsorshipBroker.closeRequestsAndMarkApproved(getUserProfile().getUid(), account.getUid());
            addMessage(attributes, MessageType.SUCCESS, "account.signup.payment.done", request);
        } else if (enableOrUpdateAccount == UPDATE) {
            accountBroker.updateAccountCardPaymentReference(getUserProfile().getUid(), account.getUid(), paymentRef);
            addMessage(attributes, MessageType.SUCCESS, "account.payment.changed", request);
        }

        attributes.addAttribute("accountUid", account.getUid());
        return "redirect:/account/view";
    }

    private String handleError(int enableOrUpdate, RedirectAttributes attributes, HttpServletRequest request, String description) {
        logger.info("Error in payment, handling with description: {}", description);
        addMessage(attributes, MessageType.ERROR, "account.payment.failed", request);
        attributes.addFlashAttribute("errorDescription", description == null ? "" : description);
        return enableOrUpdate == ENABLE ? "redirect:/account/payment/retry" : "redirect:/account/payment/change";
    }

}
