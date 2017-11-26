package za.org.grassroot.webapp.controller.webapp.group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.GroupNotPaidForException;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/group/welcome/")
public class GroupWelcomeController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(GroupWelcomeController.class);

    private final AccountGroupBroker accountGroupBroker;

    @Autowired
    public GroupWelcomeController(AccountGroupBroker accountGroupBroker) {
        this.accountGroupBroker = accountGroupBroker;
    }

    // todo : security checks, various
    @RequestMapping(value = "messages", method = RequestMethod.GET)
    public String manageGroupMessages(@RequestParam String groupUid, Model model) {
        accountGroupBroker.validateUserAccountAdminForGroup(getUserProfile().getUid(), groupUid);
        model.addAttribute("groupUid", groupUid);
        model.addAttribute("backUrl", String.format("/group/view?groupUid=%s", groupUid));
        model.addAttribute("existingTemplate", accountGroupBroker.loadTemplate(groupUid));
        model.addAttribute("hasChildren", accountGroupBroker.hasSubgroups(groupUid));
        return "group/welcome_messages";
    }

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createOrModifyGroupMessages(@RequestParam String groupUid,
                                              @RequestParam String messageText1,
                                              @RequestParam String messageText2,
                                              @RequestParam String messageText3,
                                              @RequestParam Long durationMillis,
                                              @RequestParam(required = false) Boolean updatingTemplate,
                                              @RequestParam(required = false) Boolean changedDelay,
                                              RedirectAttributes attributes,
                                              HttpServletRequest request) {
        accountGroupBroker.validateUserAccountAdminForGroup(getUserProfile().getUid(), groupUid);
        final User user = userManagementService.load(getUserProfile().getUid());
        if (user.getPrimaryAccount() == null) {
            addMessage(attributes, MessageType.ERROR, "group.welcome.noaccount", request);
            attributes.addAttribute("groupUid", groupUid);
            return "redirect:/group/view";
        }

        final Account groupAccount = accountGroupBroker.findAccountForGroup(groupUid);
        final String accountUid = groupAccount.getUid();
        logger.info("found group account UID = {}", accountUid);

        if (updatingTemplate == null || !updatingTemplate) {
            List<String> messages = new ArrayList<>();
            messages.add(messageText1);
            if (!StringUtils.isEmpty(messageText2)) {
                messages.add(messageText2);
            }
            if (!StringUtils.isEmpty(messageText3)) {
                messages.add(messageText3);
            }

            accountGroupBroker.createGroupWelcomeMessages(user.getUid(), accountUid, groupUid,
                    messages, Duration.of(durationMillis, ChronoUnit.MILLIS), null, true);
            addMessage(attributes, MessageType.SUCCESS, "group.welcome.done", request);
        } else {
            accountGroupBroker.updateGroupWelcomeNotifications(user.getUid(), groupUid,
                    Arrays.asList(messageText1, messageText2, messageText3),
                    changedDelay == null || changedDelay ? Duration.of(durationMillis, ChronoUnit.MILLIS) : null);
            addMessage(attributes, MessageType.SUCCESS, "group.welcome.updated", request);
        }

        attributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/welcome/messages";
    }

    @RequestMapping(value = "deactivate", method = RequestMethod.POST)
    public String deactivateGroupMessages(@RequestParam String groupUid, RedirectAttributes attributes, HttpServletRequest request) {
        accountGroupBroker.validateUserAccountAdminForGroup(getUserProfile().getUid(), groupUid);
        accountGroupBroker.deactivateGroupWelcomes(getUserProfile().getUid(), groupUid);
        addMessage(attributes, MessageType.INFO, "group.welcome.deactivated", request);
        attributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/view";
    }

    @RequestMapping(value = "cascade/enable", method = RequestMethod.POST)
    public String cascadeMessages(@RequestParam String groupUid, RedirectAttributes attributes, HttpServletRequest request) {
        accountGroupBroker.cascadeWelcomeMessages(getUserProfile().getUid(), groupUid);
        return addMessageAndGroupUid("group.welcome.cascaded", groupUid, attributes, request);
    }

    @RequestMapping(value = "cascade/disable", method = RequestMethod.POST)
    public String disableCascade(@RequestParam String groupUid, RedirectAttributes attributes, HttpServletRequest request) {
        accountGroupBroker.disableCascadingMessages(getUserProfile().getUid(), groupUid);
        return addMessageAndGroupUid("group.welcome.cascade.disable", groupUid, attributes, request);
    }

    private String addMessageAndGroupUid(String messageKey, String groupUid, RedirectAttributes attributes, HttpServletRequest request) {
        addMessage(attributes, MessageType.SUCCESS, messageKey, request);
        attributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/view";
    }

    @ExceptionHandler(GroupNotPaidForException.class)
    public String handleGroupNotPaidFor(Model model) {
        model.addAttribute("pageTitle", "Group not paid for");
        model.addAttribute("pageHeader", "Error");
        model.addAttribute("subHeader", "Group not sponsored");
        model.addAttribute("message", "Sorry, but only groups that are linked to a Grassroot Extra account can create " +
                "welcome messages. Please add this group to a Grassroot Extra account, either by signing up (via 'Grassroot Extra') " +
                "at the top, or adding it to an existing account - just click 'add to account' on the 'manage group' drop down, or " +
                "'Add Group' in your Grassroot Extra page.");
        return "error_generic";
    }

    @ExceptionHandler(AccountLimitExceededException.class)
    public String handleAccountNotOnPayPerMessage(Model model) {
        model.addAttribute("pageTitle", "Account Exceeded");
        model.addAttribute("pageHeader", "Error");
        model.addAttribute("subHeader", "Account does not provide welcome messages");
        model.addAttribute("message", "Sorry, but your Grassroot Extra account does not provide access to this feature. " +
                "Please contact Grassroot to enable it.");
        return "error_generic";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(Model model) {
        model.addAttribute("pageTitle", "Access denied");
        model.addAttribute("pageHeader", "Error");
        model.addAttribute("subHeader", "Account access error");
        model.addAttribute("message", "Sorry, to add or modify welcome messages for this group, you must be an " +
                "administrator of the account that pays for the group. Please ask one of the existing admins to " +
                "add you to the account.");
        return "error_generic";
    }
}
