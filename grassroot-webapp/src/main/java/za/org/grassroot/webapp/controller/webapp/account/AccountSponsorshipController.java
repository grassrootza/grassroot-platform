package za.org.grassroot.webapp.controller.webapp.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.payments.PaymentMethod;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountSponsorshipBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by luke on 2017/02/07.
 */
@Controller
@RequestMapping("/account/sponsor")
public class AccountSponsorshipController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AccountSponsorshipController.class);

    private final AccountBroker accountBroker;
    private final AccountSponsorshipBroker sponsorshipBroker;

    public AccountSponsorshipController(AccountBroker accountBroker, AccountSponsorshipBroker sponsorshipBroker) {
        this.accountBroker = accountBroker;
        this.sponsorshipBroker = sponsorshipBroker;
    }

    @RequestMapping(value = "/request", method = RequestMethod.GET)
    public String initiateSponsorRequest(Model model, @RequestParam(required = false) String accountUid) {
        Account account = accountBroker.loadAccount(accountUid);
        model.addAttribute("account", account);
        return "account/sponsor_request";
    }

    // how to handle users to create?
    @RequestMapping(value = "/request", method = RequestMethod.POST)
    public String completeSponsorRequest(Model model, @RequestParam String accountUid, @RequestParam String displayName,
                                         @RequestParam String phoneNumber, @RequestParam String emailAddress,
                                         @RequestParam String messageToSponsor, RedirectAttributes attributes, HttpServletRequest request) {
        String destinationUid;

        if (userManagementService.userExist(PhoneNumberUtil.convertPhoneNumber(phoneNumber))) {
            User destination = userManagementService.findByInputNumber(phoneNumber);
            if (!StringUtils.isEmpty(destination.getEmailAddress()) && !emailAddress.equalsIgnoreCase(destination.getEmailAddress())) {
                return addMessageAndReturnToForm(model, request, "account.sponsorship.email.mismatch", MessageType.ERROR,
                        accountUid, displayName, phoneNumber, emailAddress, messageToSponsor);
            } else {
                destinationUid = destination.getUid();
                userManagementService.updateEmailAddress(destinationUid, emailAddress);
            }
        } else {
            destinationUid = userManagementService.create(phoneNumber, displayName, emailAddress);
        }

        sponsorshipBroker.openSponsorshipRequest(getUserProfile().getUid(), accountUid, destinationUid, messageToSponsor);
        addMessage(attributes, MessageType.SUCCESS, "account.sponsorship.open.done", request);
        attributes.addAttribute("accountUid", accountUid);
        return "redirect:/account/view";
    }

    private String addMessageAndReturnToForm(Model model, HttpServletRequest request, String messageKey, MessageType messageType,
                                             String accountUid, String displayName, String phoneNumber, String emailAddress, String messageToSponsor) {
        Account account = accountBroker.loadAccount(accountUid);
        model.addAttribute("account", account);
        model.addAttribute("displayName", displayName);
        model.addAttribute("phoneNumber", phoneNumber);
        model.addAttribute("emailAddress", emailAddress);
        model.addAttribute("messageToSponsor", messageToSponsor);
        addMessage(model, messageType, messageKey, request);
        return "account/sponsor_request";
    }

    // confirm approval, select payment method, hand this to payments, and return via a parameter
    @RequestMapping(value = "/respond", method = RequestMethod.GET)
    public String respondSponsorRequest(Model model, @RequestParam String requestUid) {
        AccountSponsorshipRequest request = sponsorshipBroker.load(requestUid);
        model.addAttribute("request", request);
        model.addAttribute("method", PaymentMethod.makeEmpty());
        return "account/sponsor_respond";
    }

    @RequestMapping(value = "/respond/approve/start", method = RequestMethod.GET)
    public String approveSponsorRequestInitiate(Model model, @RequestParam String requestUid) {
        sponsorshipBroker.approveRequestPendingPayment(requestUid);
        return "account/sponsor_payment";
    }

    @RequestMapping(value = "/respond/approve/done", method = RequestMethod.GET)
    public String approveSponsorRequestDone(Model model, @RequestParam String requestUid, @RequestParam String paymentRef) {
        AccountSponsorshipRequest request = sponsorshipBroker.load(requestUid);
        sponsorshipBroker.approveRequestPaymentComplete(requestUid);
        accountBroker.enableAccount(request.getDestination().getUid(), request.getRequestor().getUid(), paymentRef);
        return "accont/sponsor_approved";
    }

    @RequestMapping(value = "/respond/deny", method = RequestMethod.GET)
    public String denySponsorRequest(@RequestParam String requestUid, RedirectAttributes attributes, HttpServletRequest request) {
        sponsorshipBroker.denySponsorshipRequest(requestUid);
        addMessage(attributes, MessageType.INFO, "account.sponsorship.denied.done", request);
        return "redirect:/home";
    }

}
