package za.org.grassroot.webapp.controller.webapp.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.payments.PaymentMethod;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountSponsorshipBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.text.DecimalFormat;

/**
 * Created by luke on 2017/02/07.
 */
@Controller
@RequestMapping("/account/sponsor")
public class AccountSponsorshipController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AccountSponsorshipController.class);

    private final AccountBroker accountBroker;
    private final AccountSponsorshipBroker sponsorshipBroker;
    private final AccountBillingBroker billingBroker;

    public AccountSponsorshipController(AccountBroker accountBroker, AccountSponsorshipBroker sponsorshipBroker,
                                        AccountBillingBroker billingBroker) {
        this.accountBroker = accountBroker;
        this.sponsorshipBroker = sponsorshipBroker;
        this.billingBroker = billingBroker;
    }

    @RequestMapping(value = "/request", method = RequestMethod.GET)
    public String initiateSponsorRequest(Model model, @RequestParam(required = false) String accountUid,
                                         @RequestParam(required = false) String requestUid) {
        Account account = !StringUtils.isEmpty(accountUid) ? accountBroker.loadAccount(accountUid) :
                accountBroker.loadPrimaryAccountForUser(getUserProfile().getUid(), true);
        model.addAttribute("account", account);
        model.addAttribute("resendExisting", !StringUtils.isEmpty(requestUid));
        if (!StringUtils.isEmpty(requestUid)) {
            model.addAttribute("request", sponsorshipBroker.load(requestUid));
        }
        return "account/sponsor_request";
    }

    // how to handle users to create?
    @RequestMapping(value = "/request", method = RequestMethod.POST)
    public String completeSponsorRequest(Model model, @RequestParam String accountUid, @RequestParam String displayName,
                                         @RequestParam String phoneNumber, @RequestParam String emailAddress,
                                         @RequestParam String messageToSponsor, RedirectAttributes attributes, HttpServletRequest request) {
        String destinationUid;

        try {
            if (userManagementService.userExist(PhoneNumberUtil.convertPhoneNumber(phoneNumber))) {
                User destination = userManagementService.findByInputNumber(phoneNumber);
                if (!StringUtils.isEmpty(destination.getEmailAddress()) && !emailAddress.equalsIgnoreCase(destination.getEmailAddress())) {
                    return addMessageAndReturnToForm(model, request, "account.sponsorship.email.mismatch", MessageType.ERROR,
                            accountUid, displayName, phoneNumber, emailAddress, messageToSponsor);
                } else {
                    destinationUid = destination.getUid();
                    // todo : find other way to do this
                    userManagementService.updateEmailAddress(null, destinationUid, emailAddress);
                }
            } else {
                destinationUid = userManagementService.create(phoneNumber, displayName, emailAddress);
            }

            sponsorshipBroker.openSponsorshipRequest(getUserProfile().getUid(), accountUid, destinationUid, messageToSponsor);
            addMessage(attributes, MessageType.SUCCESS, "account.sponsorship.open.done", request);
            attributes.addAttribute("accountUid", accountUid);
            return "redirect:/account/view";
        } catch (InvalidPhoneNumberException e) {
            return addMessageAndReturnToForm(model, request, "account.sponsorship.phone.exception", MessageType.ERROR,
                    accountUid, displayName, phoneNumber, emailAddress, messageToSponsor);
        } catch (IllegalArgumentException e) {
            return addMessageAndReturnToForm(model, request, "account.sponsorship.arg.exception", MessageType.ERROR,
                    accountUid, displayName, phoneNumber, emailAddress, messageToSponsor);
        }
    }

    private String addMessageAndReturnToForm(Model model, HttpServletRequest request, String messageKey, MessageType messageType,
                                             String accountUid, String displayName, String phoneNumber, String emailAddress, String messageToSponsor) {
        Account account = accountBroker.loadAccount(accountUid);
        model.addAttribute("account", account);
        model.addAttribute("resendExisting", false);
        model.addAttribute("displayName", displayName);
        model.addAttribute("phoneNumber", phoneNumber);
        model.addAttribute("emailAddress", emailAddress);
        model.addAttribute("messageToSponsor", messageToSponsor);
        addMessage(model, messageType, messageKey, request);
        return "account/sponsor_request";
    }

    // confirm approval, select payment method, hand this to payments, which then handles account enabling & closing sponsorships
    @RequestMapping(value = "/respond", method = RequestMethod.GET)
    public String respondSponsorRequest(Model model, @RequestParam String requestUid) {
        AccountSponsorshipRequest request = sponsorshipBroker.load(requestUid);
        User user = userManagementService.load(getUserProfile().getUid());

        if (!request.getDestination().equals(user)) {
            throw new AccessDeniedException("Error! Only the user asked to sponsor can respond to the request");
        }

        model.addAttribute("request", request);
        model.addAttribute("amount", "R" + new DecimalFormat("#,###.##")
                .format(Math.round((double) accountBroker.getAccountTypeFees().get(request.getRequestor().getType()) / 100)));

        sponsorshipBroker.markRequestAsViewed(requestUid);
        return "account/sponsor_respond";
    }

    @RequestMapping(value = "/respond/payment", method = RequestMethod.GET)
    public String payForSponsorRequest(Model model, @RequestParam String requestUid, @RequestParam AccountPaymentType type,
                                       @RequestParam AccountBillingCycle cycle, RedirectAttributes attributes) {

        AccountSponsorshipRequest request = sponsorshipBroker.load(requestUid);
        User user = userManagementService.load(getUserProfile().getUid());

        if (!request.getDestination().equals(user)) {
            throw new AccessDeniedException("Error! Only the user asked to sponsor can respond to the request");
        }

        // note : this is updated even if it doesn't succeed, but just gets reset by a future sponsor (or user paying themselves)
        Account requestAccount = request.getRequestor(); // for Hibernate loading
        accountBroker.updateAccountPaymentCycleAndMethod(user.getUid(), requestAccount.getUid(), type, cycle, true);
        Account updatedAccount = accountBroker.loadAccount(requestAccount.getUid());

        if (type.equals(AccountPaymentType.DIRECT_DEPOSIT)) {
            // todo : probably a better way is to turn this into a fragment and load from here, with a back button, but for moment ...
            attributes.addAttribute("accountUid", updatedAccount.getUid());
            attributes.addAttribute("requestUid", request.getUid());
            return "redirect:/account/payment/deposit";
        } else {
            model.addAttribute("account", updatedAccount);
            model.addAttribute("method", PaymentMethod.makeEmpty());
            model.addAttribute("billingAmount", "R" + (new DecimalFormat("#,###.##")
                    .format(Math.round((double) updatedAccount.calculatePeriodCost() / 100))));

            return "account/sponsor_payment";
        }
    }

    @RequestMapping(value = "/respond/deny", method = RequestMethod.GET)
    public String denySponsorRequest(@RequestParam String requestUid, RedirectAttributes attributes, HttpServletRequest request) {
        AccountSponsorshipRequest sponsorshipRequest = sponsorshipBroker.load(requestUid);
        User user = userManagementService.load(getUserProfile().getUid());

        if (!sponsorshipRequest.getDestination().equals(user)) {
            throw new AccessDeniedException("Error! Only the user asked to sponsor can respond to the request");
        }

        sponsorshipBroker.denySponsorshipRequest(requestUid);
        addMessage(attributes, MessageType.INFO, "account.sponsorship.denied.done", request);
        return "redirect:/home";
    }

}
