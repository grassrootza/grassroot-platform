package za.org.grassroot.webapp.controller.webapp;

import com.google.common.collect.ImmutableMap;
import edu.emory.mathcs.backport.java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.integration.services.SmsSendingService;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.controller.BaseController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;


/**
 * Created by luke on 2015/09/11.
 */
@Controller
@SessionAttributes("meeting")
public class MeetingController extends BaseController {

    Logger log = LoggerFactory.getLogger(MeetingController.class);

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    EventLogManagementService eventLogManagementService;
    
    

    @RequestMapping("/meeting/view")
    public String viewMeetingDetails(Model model, @RequestParam("eventId") Long eventId) {

        Event meeting = eventManagementService.loadEvent(eventId);
        
        int rsvpYesTotal = eventManagementService.getListOfUsersThatRSVPYesForEvent(meeting).size();
        Set<Map.Entry<User, EventRSVPResponse>> rsvpResponses =
                eventManagementService.getRSVPResponses(meeting).entrySet();
        

        model.addAttribute("meeting", meeting);
        model.addAttribute("rsvpYesTotal", rsvpYesTotal);
        model.addAttribute("rsvpResponses", rsvpResponses);

        log.info("Number of yes RSVPd: " + rsvpYesTotal);
        log.info("Size of response map: " + rsvpResponses);

        return "meeting/view";
    }

    /**
     * Meeting creation
     */

    @RequestMapping("/meeting/create")
    public String createMeetingIndex(Model model, @RequestParam(value="groupId", required=false) Long groupId) {

        boolean groupSpecified;
        User sessionUser = getUserProfile();
        Event meeting = eventManagementService.createMeeting(sessionUser);


        if (groupId != null) {
            log.info("Came here from a group");
            model.addAttribute("group", groupManagementService.loadGroup(groupId));
            meeting = eventManagementService.setGroup(meeting.getId(), groupId);
            groupSpecified = true;
        } else {
            log.info("No group selected, pass the list of possible");
            model.addAttribute("userGroups", groupManagementService.getGroupsPartOf(sessionUser)); // todo: or just use user.getGroupsPartOf?
            groupSpecified = false;
        }


        // defaulting to this until we are comfortable that reminders are robust and use cases are sorted out
        meeting = eventManagementService.setEventNoReminder(meeting.getId());

        model.addAttribute("meeting", meeting);
        model.addAttribute("groupSpecified", groupSpecified); // slightly redundant, but use it to tell Thymeleaf what to do
        model.addAttribute("reminderOptions", reminderMinuteOptions());

        log.info("Meeting that we are passing: " + meeting.toString());
        return "meeting/create";

    }

    @RequestMapping(value = "/meeting/create", method = RequestMethod.POST)
    public String createMeeting(Model model, @ModelAttribute("meeting") Event meeting, BindingResult bindingResult,
                                @RequestParam(value="selectedGroupId", required=false) Long selectedGroupId,
                                HttpServletRequest request, RedirectAttributes redirectAttributes) {

        // todo: add error handling and validation
        // todo: check that we have all the needed information and/or add a confirmation screen
        // todo: put this data transformation else where:Maybe Wrapper?





        log.info("The event passed back to us: " + meeting.toString());

        /*
        This is a bit clunky. Unfortunately, Thymeleaf isn't handling the mapping of group IDs from selection box back
          to the event.groupAppliesTo field, nor does it do it as a Group (just passes the toString() output around), hence ...
         */

        meeting = eventManagementService.updateEvent(meeting);

        if (selectedGroupId != null) { // now we need to load a group and then pass it to meeting
            log.info("Okay, we were passed a group Id, so we need to set it to this groupId: " + selectedGroupId);
            meeting = eventManagementService.setGroup(meeting.getId(), selectedGroupId);
        }

        log.info("Stored meeting, at end of creation method: " + meeting.toString());

        addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.creation.success", request);
        return "redirect:/home";

    }

    /**
     * Meeting modification
     */

    @RequestMapping(value = "/meeting/modify", params={"change"})
    public String initiateMeetingModification(Model model, @ModelAttribute("meeting") Event meeting) {

        // todo: check for permission ...
        meeting = eventManagementService.loadEvent(meeting.getId()); // load all details, as may not have been passed by Th
        model.addAttribute("meeting", meeting);
        model.addAttribute("rsvpYesTotal", eventManagementService.getListOfUsersThatRSVPYesForEvent(meeting).size());

        return "meeting/modify";
    }

    @RequestMapping(value = "/meeting/modify", method=RequestMethod.POST, params={"modify"})
    public String changeMeeting(Model model, @ModelAttribute("meeting") Event meeting,
                                BindingResult bindingResult, HttpServletRequest request) {

        log.info("Meeting we are passed: " + meeting);
        meeting = eventManagementService.updateEvent(meeting);
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
    public String confirmReminder(Model model, @ModelAttribute("meeting") Event meeting, RedirectAttributes redirectAttributes,
                               HttpServletRequest request) {

        meeting = eventManagementService.loadEvent(meeting.getId());
        // todo: make sure this is a paid group before allowing it (not just that user is admin)
        if (request.isUserInRole("ROLE_SYSTEM_ADMIN") || request.isUserInRole("ROLE_ACCOUNT_ADMIN")) {

            model.addAttribute("entityId", meeting.getId());
            model.addAttribute("action", "remind");
            model.addAttribute("includeSubGRoups", meeting.isIncludeSubGroups());

            String groupLanguage = meeting.getAppliesToGroup().getDefaultLanguage();
            log.info("Composing dummy message for confirmation ... Group language is ... " + groupLanguage);
            String message = (groupLanguage == null || groupLanguage.trim().equals("")) ?
                    eventManagementService.getDefaultLocaleReminderMessage(getUserProfile(), meeting) :
                    eventManagementService.getReminderMessageForConfirmation(groupLanguage, getUserProfile(), meeting);

            model.addAttribute("message", message);
            model.addAttribute("recipients", eventManagementService.getNumberInvitees(meeting));
            model.addAttribute("cost", eventManagementService.getCostOfMessagesDefault(meeting));
            return "meeting/remind_confirm";

        } else {

            // todo: go back to the meeting instead
            addMessage(redirectAttributes, MessageType.ERROR, "permission.denied.error", request);
            return "redirect:/home";

        }
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/meeting/remind", method=RequestMethod.POST)
    public String sendReminder(Model model, @RequestParam("entityId") Long eventId, RedirectAttributes redirectAttributes,
                               HttpServletRequest request) {

        eventManagementService.sendManualReminder(eventManagementService.loadEvent(eventId), "");
        addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.reminder.success", request);
        return "redirect:/home";

    }

    /**
     * Free text entry, for authorized accounts
     */

    // Major todo: make this secured against the user's role as 'admin' on an institutional account
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
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

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/meeting/free", method = RequestMethod.POST)
    public String confirmFreeMsg(Model model, @RequestParam(value="groupId") Long groupId, @RequestParam(value="message") String message,
                                 @RequestParam(value="includeSubGroups", required=false) boolean includeSubgroups) {

        model.addAttribute("action", "free");
        model.addAttribute("entityId", groupId);
        model.addAttribute("includeSubGroups", includeSubgroups);

        model.addAttribute("message", message);
        int recipients = groupManagementService.getGroupSize(groupManagementService.loadGroup(groupId), includeSubgroups);
        model.addAttribute("recipients", recipients);
        model.addAttribute("cost", recipients * 0.2);
        return "meeting/remind_confirm";

    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/meeting/free", method = RequestMethod.POST, params = {"confirmed"})
    public String sendFreeMsg(Model model, @RequestParam(value="entityId") Long groupId, @RequestParam(value="message") String message,
                              @RequestParam(value="includeSubGroups", required=false) boolean includeSubgroups,
                              RedirectAttributes redirectAttributes, HttpServletRequest request) {

        // todo: check that this group is paid for (and filter on previous page)

        log.info("Sending free form message ... includeSubGroups set to ... " + includeSubgroups);

        Group group = groupManagementService.loadGroup(groupId);
        Event dummyEvent = eventManagementService.createEvent("", getUserProfile(), group, includeSubgroups);
        boolean messageSent = eventManagementService.sendManualReminder(dummyEvent, message);

        log.info("We just sent a free form message with result: " + messageSent);


        redirectAttributes.addAttribute("groupId", groupId);
        addMessage(redirectAttributes, MessageType.SUCCESS, "sms.message.sent", request);
        return "redirect:/group/view";

    }

    /**
     * RSVP handling
     */

    /* These are to handle RSVPs that come in via the web application
    If it's a 'yes', we add it as such; it it's a 'no, we just need to make sure the service layer excludes the user
    from reminders and cancellations, but does include them on change notices (in case that changes RSVP ...). */
    @RequestMapping(value = "/meeting/rsvp", method = RequestMethod.POST, params={"yes"})
    public String rsvpYes(Model model, @RequestParam(value="eventId") Long eventId, HttpServletRequest request) {

        Event meeting = eventManagementService.loadEvent(eventId);
        User user = getUserProfile();

        eventLogManagementService.rsvpForEvent(meeting, user, EventRSVPResponse.YES);


        int rsvpYesTotal = eventManagementService.getListOfUsersThatRSVPYesForEvent(meeting).size();
        Set<Map.Entry<User, EventRSVPResponse>> rsvpResponses =
                eventManagementService.getRSVPResponses(meeting).entrySet();

        model.addAttribute("meeting", meeting);
        model.addAttribute("rsvpYesTotal", rsvpYesTotal);
        model.addAttribute("rsvpResponses", rsvpResponses);

        addMessage(model, MessageType.SUCCESS, "meeting.rsvp.yes", request);

        return "meeting/view";
    }

    @RequestMapping(value = "/meeting/rsvp", method = RequestMethod.POST, params={"no"})
    public String rsvpNo(Model model, @RequestParam(value="eventId") Long eventId,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Event event = eventManagementService.loadEvent(eventId);
        User user = getUserProfile();

        eventLogManagementService.rsvpForEvent(event, user, EventRSVPResponse.NO);

        addMessage(redirectAttributes, MessageType.ERROR, "meeting.rsvp.no", request);
        return "redirect:/home";
    }

    /*
    Helper functions for reminders -- may make this call group, if there is group, for defaults, and so on ... for now, just assembles the list
    Would have done this as a list of array strings but Java has seriously terrible array/list handling for this sort of thing
     */

    private List<String[]> reminderMinuteOptions() {

        List<String[]> minuteOptions = new ArrayList<>();

        String[] oneDay = new String[]{"" + 24 * 60, "One day ahead"};
        String[] halfDay = new String[]{"" + 6 * 60, "Half a day ahead"};
        String[] oneHour = new String[]{"60", "An hour before"};
        String[] noReminder = new String[]{"-1", "No reminder"};

        minuteOptions.add(oneDay);
        minuteOptions.add(halfDay);
        minuteOptions.add(oneHour);
        minuteOptions.add(noReminder);

        return minuteOptions;

    }

    
  
    

}
