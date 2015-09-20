package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.services.SmsSendingService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Created by luke on 2015/09/11.
 */
@Controller
public class MeetingController extends BaseController {

    Logger log = LoggerFactory.getLogger(MeetingController.class);

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    EventManagementService eventManagementService;

    // temporary, just to make sure the SMS sends, will later leave this to service layer
    @Autowired
    SmsSendingService smsSendingService;

    @RequestMapping("/meeting/view")
    public String viewMeetingDetails(Model model, @RequestParam("eventId") Long eventId) {

        model.addAttribute("meeting", eventManagementService.loadEvent(eventId));
        return "meeting/view";

    }

    @RequestMapping("/meeting/create")
    public String createMeetingIndex(Model model, @RequestParam(value="groupId", required=false) Long groupId) {

        boolean groupSpecified;
        User sessionUser = getUserProfile();
        Event meeting = eventManagementService.createMeeting(sessionUser);

        if (groupId != null) {
            System.out.println("Came here from a group");
            model.addAttribute("group", groupManagementService.loadGroup(groupId));
            meeting = eventManagementService.setGroup(meeting.getId(), groupId);
            groupSpecified = true;
        } else {
            System.out.println("No group selected, pass the list of possible");
            model.addAttribute("userGroups", groupManagementService.getGroupsPartOf(sessionUser)); // todo: or just use user.getGroupsPartOf?
            groupSpecified = false;
        }
        model.addAttribute("meeting", meeting);
        model.addAttribute("groupSpecified", groupSpecified); // slightly redundant, but use it to tell Thymeleaf what to do
        log.info("Meeting that we are passing: " + meeting.toString());
        return "meeting/create";

    }

    @RequestMapping(value = "/meeting/create", method = RequestMethod.POST)
    public String createMeeting(Model model, @ModelAttribute("meeting") Event meeting, BindingResult bindingResult,
                                @RequestParam(value="subgroups", required=false) boolean subgroups, HttpServletRequest request, RedirectAttributes redirectAttributes) {

        // todo: add error handling and validation
        // todo: check that we have all the needed information and/or add a confirmation screen


        // todo: put this data transformation else where:Maybe Wrapper?
        meeting.setDateTimeString(new SimpleDateFormat("E d MMM HH:mm").format(meeting.getEventStartDateTime()));

        log.info("The timestamp is: " + meeting.getEventStartDateTime().toString());
        log.info("The string is: " + meeting.getDateTimeString());

        log.info("The event passed back to us: " + meeting.toString());
        meeting = eventManagementService.updateEvent(meeting);
        System.out.println("Meeting currently: " + meeting.toString());

        // todo: need a way to get the response of sending meeting

        addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.creation.success", request);
        return "redirect:/home";

    }

    @RequestMapping(value = "/meeting/modify", method=RequestMethod.POST, params={"modify"})
    public String changeMeeting(Model model, @ModelAttribute("meeting") Event meeting,
                                BindingResult bindingResult, HttpServletRequest request) {

        log.info("Meeting we are passed: " + meeting);
        eventManagementService.updateEvent(meeting);
        model.addAttribute("meeting", meeting);
        addMessage(model, MessageType.SUCCESS, "meeting.update.success", request);
        return "meeting/view";

    }

    @RequestMapping(value = "/meeting/modify", method=RequestMethod.POST, params={"cancel"})
    public String cancelMeeting(Model model, @ModelAttribute("meeting") Event meeting, BindingResult bindingResult,
                                RedirectAttributes redirectAttributes, HttpServletRequest request) {

        log.info("Meeting that is about to be cancelled: " + meeting.toString());
        eventManagementService.cancelEvent(meeting.getId());
        addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.cancel.success", request);
        return "redirect:/home";

    }

    @RequestMapping(value = "/meeting/modify", method=RequestMethod.POST, params={"reminder"})
    public String sendReminder(Model model, @ModelAttribute("meeting") Event meeting, RedirectAttributes redirectAttributes,
                               HttpServletRequest request) {

        // we need a method in eventManagementService to force a reminder

        eventManagementService.updateEvent(meeting);
        addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.reminder.success", request);
        return "redirect:/home";

    }

    // Major todo: make this secured against the user's role as 'admin' on an institutional account
    // @Secured()
    @RequestMapping(value = "/meeting/free")
    public String sendFreeForm(Model model, @RequestParam(value="groupId", required=false) Long groupId) {

        boolean groupSpecified;
        User sessionUser = getUserProfile();

        if (groupId != null) {
            System.out.println("Came here from a group");
            model.addAttribute("group", groupManagementService.loadGroup(groupId));
            groupSpecified = true;
        } else {
            System.out.println("No group selected, pass the list of possible");
            model.addAttribute("userGroups", groupManagementService.getGroupsPartOf(sessionUser)); // todo: or just use user.getGroupsPartOf?
            groupSpecified = false;
        }
        model.addAttribute("groupSpecified", groupSpecified); // slightly redundant, but use it to tell Thymeleaf what to do
        return "meeting/free";
    }

    @RequestMapping(value = "/meeting/free", method = RequestMethod.POST)
    public String sendFreeMsg(Model model, HttpServletRequest request, @RequestParam(value="groupId") Long groupId,
                              @RequestParam(value="message") String message, @RequestParam(value="includeSubGroups", required=false) boolean includeSubgroups) {

        Group group = groupManagementService.loadGroup(groupId);

        List<User> usersToMessage = (!includeSubgroups) ?  group.getGroupMembers() :
                groupManagementService.getAllUsersInGroupAndSubGroups(groupId);

        // removing duplicates ...
        Set<User> usersSet = new HashSet<>();
        usersSet.addAll(usersToMessage);
        usersToMessage.clear();
        usersToMessage.addAll(usersSet);

        for (User user : usersToMessage) {
            smsSendingService.sendSMS(group.getName("") + ": " + message, user.getPhoneNumber());
        }

        log.info("We just sent a message to " + usersToMessage.size() + " members");

        model.addAttribute("groupId");
        addMessage(model, MessageType.SUCCESS, "sms.message.sent", request);
        return "/home";

    }

}
