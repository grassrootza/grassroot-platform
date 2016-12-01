package za.org.grassroot.webapp.controller.webapp.account;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
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
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.exception.PaymentMethodFailedException;
import za.org.grassroot.integration.payments.PaymentMethod;
import za.org.grassroot.integration.payments.PaymentResponse;
import za.org.grassroot.integration.payments.PaymentResultType;
import za.org.grassroot.integration.payments.PaymentServiceBroker;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private AccountBroker accountBroker;
    private AccountBillingBroker accountBillingBroker;
    private PaymentServiceBroker paymentServiceBroker;

    @Autowired
    public AccountPaymentController(AccountBroker accountBroker, AccountBillingBroker accountBillingBroker,
                                    PaymentServiceBroker paymentServiceBroker) {
        this.accountBroker = accountBroker;
        this.accountBillingBroker = accountBillingBroker;
        this.paymentServiceBroker = paymentServiceBroker;
    }

    @RequestMapping(value = "signup", method = RequestMethod.POST)
    public String initiatePayment(Model model, RedirectAttributes attributes, @RequestParam String accountUid,
                                  @ModelAttribute("method") PaymentMethod paymentMethod, HttpServletRequest request) {
        AccountBillingRecord record = accountBillingBroker.generateSignUpBill(accountUid);
        return handleInitiatingPayment(accountUid, paymentMethod, record, ENABLE, model, attributes, request);
    }

    @RequestMapping(value = "done")
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

    @RequestMapping(value = "signup/retry", method = RequestMethod.GET)
    public String retryAccountPayment(Model model, @RequestParam(required = false) String error) {
        User user = userManagementService.load(getUserProfile().getUid());
        Account account = user.getAccountAdministered();

        if (account == null) {
            throw new AccessDeniedException("Must have an account before trying to retry payment");
        }

        model.addAttribute("account", account);
        model.addAttribute("newAccount", true); // by definition, this account hasn't paid before
        model.addAttribute("billingAmount", "R" + (new DecimalFormat("#.##")).format((double) account.getSubscriptionFee() / 100));
        model.addAttribute("method", PaymentMethod.makeEmpty());

        if (!StringUtils.isEmpty(error)) {
            model.addAttribute("errorDescription", error);
        }

        return "account/payment";
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "change", method = RequestMethod.GET)
    public String changePaymentMethod(Model model, @RequestParam(required = false) String accountUid) {
        Account account = StringUtils.isEmpty(accountUid) ? accountBroker.loadUsersAccount(getUserProfile().getUid()) :
                accountBroker.loadAccount(accountUid);
        model.addAttribute("account", account);
        model.addAttribute("newAccount", false);
        model.addAttribute("billingAmount", "R" + (new DecimalFormat("#.##").format(PAYMENT_VERIFICATION_AMT / 100)));
        model.addAttribute("method", PaymentMethod.makeEmpty());
        return "account/payment";
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
            PaymentResponse response = paymentServiceBroker.asyncPaymentInitiate(accountUid, method, record, returnUrl);
            if (!StringUtils.isEmpty(response.getRedirectUrl())) {
                for (Map<String, String> parameter : response.getRedirectParams()) {
                    attributes.addAttribute(parameter.get("name"), parameter.get("value"));
                }
                String body = postRedirect(response);
                model.addAttribute("responseBody", body);
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

    private String postRedirect(PaymentResponse response) {
        HttpClient httpClient = HttpClients.createDefault();
        List<NameValuePair> params = response.getRedirectParams().stream()
                .map(p -> new BasicNameValuePair(p.get("name"), p.get("value")))
                .collect(Collectors.toList());
        HttpPost httpPost = new HttpPost(response.getRedirectUrl());

        try {
            httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
            HttpResponse postResponse = httpClient.execute(httpPost);
            HttpEntity entity = postResponse.getEntity();
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()), 65728);
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            };
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String handleSuccess(String paymentId, String paymentRef, int enableOrUpdateAccount,
                                 RedirectAttributes attributes, HttpServletRequest request) {
        AccountBillingRecord record = accountBillingBroker.fetchRecordByPayment(paymentId);
        Account account = record.getAccount();
        if (enableOrUpdateAccount == ENABLE) {
            accountBroker.enableAccount(getUserProfile().getUid(), account.getUid(), LocalDate.now().plusMonths(1L), paymentRef);
            addMessage(attributes, MessageType.SUCCESS, "account.signup.payment.done", request);
        } else if (enableOrUpdateAccount == UPDATE) {
            accountBroker.updateAccountPaymentReference(getUserProfile().getUid(), account.getUid(), paymentRef);
            addMessage(attributes, MessageType.SUCCESS, "account.payment.changed", request);
        }
        attributes.addAttribute("accountUid", account.getUid());
        return "redirect:/account/view";
    }

    private String handleError(int enableOrUpdate, RedirectAttributes attributes, HttpServletRequest request, String description) {
        logger.info("Error in payment, handling with description: {}", description);
        addMessage(attributes, MessageType.ERROR, "account.payment.failed", request);
        attributes.addAttribute("errorDescription", description == null ? "" : description);
        return enableOrUpdate == ENABLE ? "redirect:/account/payment/signup/retry" : "redirect:/account/payment/change";
    }


}
