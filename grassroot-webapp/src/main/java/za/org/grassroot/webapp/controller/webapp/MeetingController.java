package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


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
    public String viewMeetingDetails(Model model, @RequestParam Long eventId) {

        Event meeting = eventManagementService.loadEvent(eventId);
       /* boolean canViewDetails = groupAccessControlManagementService.hasGroupPermission(
                BasePermissions.GROUP_PERMISSION_SEE_MEMBER_DETAILS, meeting.getAppliesToGroup(), getUserProfile());*/
        
        int rsvpYesTotal = eventManagementService.getListOfUsersThatRSVPYesForEvent(meeting).size();

        model.addAttribute("meeting", meeting);
        model.addAttribute("rsvpYesTotal", rsvpYesTotal);
        model.addAttribute("canViewMemberDetails", true); // note: just setting it for now todo: try use Th sec)

      //  if (canViewDetails) {
            Set<Map.Entry<User, EventRSVPResponse>> rsvpResponses =
                    eventManagementService.getRSVPResponses(meeting).entrySet();
            model.addAttribute("rsvpResponses", rsvpResponses);
            log.info("Size of response map: " + rsvpResponses);
      //  }

        log.info("Number of yes RSVPd: " + rsvpYesTotal);

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
            // todo: filter by permissions
            log.info("No group selected, pass the list of possible");
            model.addAttribute("userGroups", groupManagementService.getActiveGroupsPartOf(sessionUser));
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
        Long groupId = (meeting.getAppliesToGroup() == null) ? selectedGroupId : meeting.getAppliesToGroup().getId();
      /*  if (!groupManagementService.canUserCallMeeting(groupId, getUserProfile()))
            throw new AccessDeniedException("You do not have permission to call a meeting of this group");*/

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

        // todo: replace canUserCall with canUserModify
        meeting = eventManagementService.loadEvent(meeting.getId()); // load all details, as may not have been passed by Th
      /*  if (!groupManagementService.canUserCallMeeting(meeting.getAppliesToGroup().getId(), getUserProfile()))
            throw new AccessDeniedException("");*/
        model.addAttribute("meeting", meeting);
        model.addAttribute("rsvpYesTotal", eventManagementService.getListOfUsersThatRSVPYesForEvent(meeting).size());

        return "meeting/modify";
    }

    @RequestMapping(value = "/meeting/modify", method=RequestMethod.POST, params={"modify"})
    public String changeMeeting(Model model, @ModelAttribute("meeting") Event meeting,
                                BindingResult bindingResult, HttpServletRequest request) {

        log.info("Meeting we are passed: " + meeting);
     /*   if (!groupManagementService.canUserCallMeeting(meeting.getAppliesToGroup().getId(), getUserProfile()))
            throw new AccessDeniedException("");*/
        meeting = eventManagementService.updateEvent(meeting);
        model.addAttribute("meeting", meeting);
        addMessage(model, MessageType.SUCCESS, "meeting.update.success", request);
        return "meeting/view";

    }

    @RequestMapping(value = "/meeting/modify", method=RequestMethod.POST, params={"cancel"})
    public String cancelMeeting(Model model, @ModelAttribute("meeting") Event meeting, BindingResult bindingResult,
                                RedirectAttributes redirectAttributes, HttpServletRequest request) {

        log.info("Meeting that is about to be cancelled: " + meeting.toString());
      /*  if (!groupManagementService.canUserCallMeeting(meeting.getAppliesToGroup().getId(), getUserProfile()))
            throw new AccessDeniedException("");*/
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
            model.addAttribute("includeSubGroups", meeting.isIncludeSubGroups());

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

        // todo: check for paid group & for feature enabled
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
            model.addAttribute("group", groupManagementService.loadGroup(groupId));
            groupSpecified = true;
        } else {

            System.out.println("No group selected, pass the list of possible");
            model.addAttribute("userGroups", groupManagementService.getActiveGroupsPartOf(sessionUser)); // todo: or just use user.getGroupsPartOf?
            List<Group> activeGroups = groupManagementService.getActiveGroupsPartOf(sessionUser);
            model.addAttribute("userGroups", activeGroups);
            log.info("ZOG: MTG: userGroups ..." + activeGroups);

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
        int recipients = groupManagementService.getGroupSize(groupId, includeSubgroups);
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

    @RequestMapping(value = "/meeting/rsvp")
    public String rsvpYes(Model model, @RequestParam Long eventId, @RequestParam String answer,
                          HttpServletRequest request, RedirectAttributes attributes) {

        Event meeting = eventManagementService.loadEvent(eventId);
        User user = getUserProfile();

        switch (answer) {
            case "yes":
                eventLogManagementService.rsvpForEvent(meeting, user, EventRSVPResponse.YES);
                addMessage(model, MessageType.SUCCESS, "meeting.rsvp.yes", request);
                return viewMeetingDetails(model, eventId);
            case "no":
                eventLogManagementService.rsvpForEvent(meeting, user, EventRSVPResponse.NO);
                addMessage(attributes, MessageType.ERROR, "meeting.rsvp.no", request);
                return "redirect:/home";
            default:
                addMessage(attributes, MessageType.ERROR, "meeting.rsvp.no", request);
                return "redirect:/home";
        }
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
