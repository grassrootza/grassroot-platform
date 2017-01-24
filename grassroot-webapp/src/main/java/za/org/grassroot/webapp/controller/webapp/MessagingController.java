package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.GroupAccountMismatchException;
import za.org.grassroot.services.exception.GroupNotPaidForException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by luke on 2016/11/07.
 */
@Controller
@RequestMapping("/messaging")
@PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
public class MessagingController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(MessagingController.class);

    private GroupBroker groupBroker;
    private AccountBroker accountBroker;
    private AccountGroupBroker accountGroupBroker;

    @Autowired
    public MessagingController(GroupBroker groupBroker, AccountBroker accountBroker, AccountGroupBroker accountGroupBroker) {
        this.groupBroker = groupBroker;
        this.accountBroker = accountBroker;
        this.accountGroupBroker = accountGroupBroker;
    }

    /**
     * Free text entry, for authorized accounts
     * todo : throw an error if account not enabled or no messages left
     */

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "freeform")
    public String sendFreeForm(Model model, @RequestParam(required=false) String groupUid,
                               RedirectAttributes attributes, HttpServletRequest request) {
        Account userAccount = accountBroker.loadUsersAccount(getUserProfile().getUid());

        if (userAccount == null) {
            addMessage(attributes, MessageType.ERROR, "messaging.error.account.none", request);
            return "redirect:/home";
        } else if (!userAccount.isEnabled()) {
            addMessage(attributes, MessageType.ERROR, "messaging.error.account.disabled", request);
            return "redirect:/account/view";
        } else {
            if (groupUid != null) {
                model.addAttribute("group", groupBroker.load(groupUid));
            } else {
                model.addAttribute("userGroups", accountGroupBroker.fetchGroupsSponsoredByAccount(userAccount.getUid()));
            }
            model.addAttribute("account", userAccount);
            model.addAttribute("messagesLeft", accountGroupBroker.calculateMessagesLeftThisMonth(userAccount.getUid()));
            return "messaging/freeform";
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "confirm", method = RequestMethod.GET)
    public String confirmFreeMsg(Model model, @RequestParam String groupUid, @RequestParam String message) {
        Account account = accountBroker.loadUsersAccount(getUserProfile().getUid());
        Group group = groupBroker.load(groupUid);
        model.addAttribute("account", account);
        model.addAttribute("group", group);

        int messagesLeft = accountGroupBroker.calculateMessagesLeftThisMonth(account.getUid());

        if (messagesLeft < group.getMembers().size()) {
            model.addAttribute("leftThisMonth", messagesLeft);
            return "messaging/error";
        } else {
            model.addAttribute("remainder", messagesLeft - group.getMembers().size());
            model.addAttribute("message", message);
            return "messaging/confirm";
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "send", method = RequestMethod.POST)
    public String sendFreeMsg(Model model, @RequestParam(value="groupUid") String groupUid,
                              @RequestParam(value="message") String message,
                              RedirectAttributes redirectAttributes, HttpServletRequest request) {

        // service layer will check group is paid for
        log.info("Sending free form message: {}, to this group: {}", message, groupUid);

        try {
            accountGroupBroker.sendFreeFormMessage(getUserProfile().getUid(), groupUid, message);
            addMessage(redirectAttributes, BaseController.MessageType.SUCCESS, "sms.message.sent", request);
            log.info("Sent message, redirecting to home");
        } catch (AccessDeniedException|GroupNotPaidForException|GroupAccountMismatchException e) {
            // todo : use different messages for the different exceptions
            addMessage(redirectAttributes, BaseController.MessageType.ERROR, "sms.message.error", request);
            e.printStackTrace();
        }

        return "redirect:/home";
    }


}
