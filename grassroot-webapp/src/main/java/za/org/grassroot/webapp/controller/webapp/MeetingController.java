package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Event;
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
import java.util.List;
import java.util.Locale;

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

    @RequestMapping("/meeting/create")
    public String createMeetingIndex(Model model, @RequestParam(value="groupId", required=false) Long groupId) {

        User sessionUser = getUserProfile();
        Event meeting = eventManagementService.createMeeting(sessionUser);
        if (groupId != null) {
            model.addAttribute("group", groupManagementService.loadGroup(groupId));
            meeting = eventManagementService.setGroup(meeting.getId(), groupId);
        } else {
            model.addAttribute("userGroups", groupManagementService.getGroupsPartOf(sessionUser)); // todo: or just use user.getGroupsPartOf?
            groupId = 0L;
        }
        model.addAttribute("meeting", meeting);
        model.addAttribute("groupId", groupId); // slightly redundant, but use it to tell Thymeleaf what to do
        System.out.println("Meeting that we are passing: " + meeting.toString());
        return "meeting/create";

    }

    @RequestMapping(value = "/meeting/create", method = RequestMethod.POST)
    public String createMeeting(Model model, @ModelAttribute("meeting") Event meeting, BindingResult bindingResult,
                                HttpServletRequest request, RedirectAttributes redirectAttributes) {

        // todo: add error handling and validation
        // todo: check that we have all the needed information and/or add a confirmation screen

        String receivedFormat = "yyyy-MM-dd HH:mm";
        DateFormat input = new SimpleDateFormat(receivedFormat);
        String outputFormat="E d MMM HH:mm";
        DateFormat output = new SimpleDateFormat(outputFormat);

        String dateTimeRaw = meeting.getDateTimeString() + ":00";
        Timestamp meetingDateTime = Timestamp.valueOf(dateTimeRaw);
        meeting.setEventStartDateTime(meetingDateTime);
        meeting.setDateTimeString(output.format(meetingDateTime));

        log.info("The timestamp is: " + meeting.getEventStartDateTime().toString());
        log.info("The string is: " + meeting.getDateTimeString());

        log.info("The event passed back to us: " + meeting.toString());
        meeting = eventManagementService.updateEvent(meeting);
        System.out.println("Meeting currently: " + meeting.toString());

        // todo: need a way to get the response of sending meeting

        List<User> usersToMessage = meeting.getAppliesToGroup().getGroupMembers();

        String[] msgParams = new String[]{
                getUserProfile().getDisplayName(),
                meeting.getName(),
                meeting.getEventLocation(),
                meeting.getDateTimeString()
        };

        String msgText = messageSourceAccessor.getMessage("ussd.mtg.send.template", msgParams, new Locale("en"));

        for (int i = 1; i <= usersToMessage.size(); i++) {
            smsSendingService.sendSMS(msgText, usersToMessage.get(i - 1).getPhoneNumber());
        }

        addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.creation.success", request);
        return "redirect:/home";

    }

}
