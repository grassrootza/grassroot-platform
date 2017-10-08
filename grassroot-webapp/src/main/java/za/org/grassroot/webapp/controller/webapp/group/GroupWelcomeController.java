package za.org.grassroot.webapp.controller.webapp.group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
        model.addAttribute("groupUid", groupUid);
        model.addAttribute("existingTemplate", accountGroupBroker.loadTemplate(groupUid));
        return "group/welcome_messages";
    }

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createOrModifyGroupMessages(@RequestParam String groupUid,
                                              @RequestParam String messageText1,
                                              @RequestParam String messageText2,
                                              @RequestParam String messageText3,
                                              @RequestParam Long durationMillis,
                                              RedirectAttributes attributes, HttpServletRequest request) {
        final User user = userManagementService.load(getUserProfile().getUid());
        if (user.getPrimaryAccount() == null) {
            addMessage(attributes, MessageType.ERROR, "group.welcome.noaccount", request);
            attributes.addAttribute("groupUid", groupUid);
            return "redirect:/group/view";
        }

        final String accountUid = user.getPrimaryAccount().getUid();
        logger.info("user primary account UID = {}", accountUid);

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
        attributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/welcome/messages";
    }
}
