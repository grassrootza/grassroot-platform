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
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.*;
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
@RequestMapping("/meeting/")
@SessionAttributes("meeting")
public class MeetingController extends BaseController {

    Logger log = LoggerFactory.getLogger(MeetingController.class);

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private EventManagementService eventManagementService;

    @Autowired
    private EventLogManagementService eventLogManagementService;

    @RequestMapping("view")
    public String viewMeetingDetails(Model model, @RequestParam Long eventId) {

        Event meeting = eventManagementService.loadEvent(eventId);
        User user = getUserProfile();
        
        int rsvpYesTotal = eventManagementService.getListOfUsersThatRSVPYesForEvent(meeting).size();
        boolean canViewRsvps = permissionBroker.isGroupPermissionAvailable(
                user, meeting.getAppliesToGroup(), Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS);

        model.addAttribute("meeting", meeting);
        model.addAttribute("rsvpYesTotal", rsvpYesTotal);
        model.addAttribute("canViewRsvps", canViewRsvps);
        model.addAttribute("canAlterDetails", meeting.getCreatedByUser().equals(user)); // todo: maybe, or is organizer/committee?

        if (canViewRsvps) {
            Set<Map.Entry<User, EventRSVPResponse>> rsvpResponses = eventManagementService.getRSVPResponses(meeting).entrySet();
            model.addAttribute("rsvpResponses", rsvpResponses);
            log.info("Size of response map: " + rsvpResponses);
        }

        return "meeting/view";
    }

    /**
     * Meeting creation
     */

    @RequestMapping("create")
    public String createMeetingIndex(Model model, @RequestParam(value="groupUid", required=false) String groupUid) {

        boolean groupSpecified;
        User sessionUser = getUserProfile();
        Meeting meeting = Meeting.makeEmpty(sessionUser);
        meeting.setRsvpRequired(true); // since this is default (and Thymeleaf doesn't handle setting it in template well)

        if (groupUid != null) {
            Group group = groupBroker.load(groupUid);
            model.addAttribute("group", group);
            meeting.setAppliesToGroup(group);
            groupSpecified = true;
        } else {
            // todo: filter by permissions, and include number of members (for confirm modal)
            model.addAttribute("userGroups", permissionBroker.getActiveGroupsWithPermission(sessionUser, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING));
            groupSpecified = false;
        }


        model.addAttribute("meeting", meeting);
        model.addAttribute("groupSpecified", groupSpecified); // slightly redundant, but use it to tell Thymeleaf what to do
        model.addAttribute("reminderOptions", reminderMinuteOptions());

        log.info("Meeting that we are passing: " + meeting.toString());
        return "meeting/create";

    }

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createMeeting(Model model, @ModelAttribute("meeting") Meeting meeting, BindingResult bindingResult,
                                @RequestParam(value="selectedGroupUid", required=false) String selectedGroupUid,
                                HttpServletRequest request, RedirectAttributes redirectAttributes) {

        // todo: add error handling and validation
        // todo: check that we have all the needed information and/or add a confirmation screen
        // todo: put this data transformation else where:Maybe Wrapper?

        log.info("The event passed back to us: " + meeting.toString());
        log.info("Event location set as: " + meeting.getEventLocation());

        String groupUid = (selectedGroupUid == null) ? meeting.getAppliesToGroup().getUid() : selectedGroupUid;

        eventBroker.createMeeting(getUserProfile().getUid(), groupUid, meeting.getName(),
                meeting.getEventStartDateTime(), meeting.getEventLocation(), meeting.isIncludeSubGroups(),
                meeting.isRsvpRequired(), meeting.isRelayable(), meeting.getReminderType(), meeting.getCustomReminderMinutes(), "");

        log.info("Stored meeting, at end of creation method: " + meeting.toString());

        addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.creation.success", request);
        return "redirect:/home";

    }

    /**
     * Meeting modification
     */

    @RequestMapping(value = "location", method=RequestMethod.GET)
    public String changeMeeting(Model model, @RequestParam String eventUid, @RequestParam String location,
                                HttpServletRequest request) {

        Event meeting = eventBroker.load(eventUid);
        log.info("Meeting we are passed: " + meeting);

        // todo: double check permissions in location update
        eventBroker.updateMeetingLocation(getUserProfile().getUid(), eventUid, location, true);

        addMessage(model, MessageType.SUCCESS, "meeting.update.success", request);
        return viewMeetingDetails(model, meeting.getId());

    }

    @RequestMapping(value = "/meeting/modify", method=RequestMethod.POST, params={"cancel"})
    public String cancelMeeting(Model model, @ModelAttribute("meeting") Meeting meeting, BindingResult bindingResult,
                                RedirectAttributes redirectAttributes, HttpServletRequest request) {

        log.info("Meeting that is about to be cancelled: " + meeting.toString());
        eventBroker.cancel(getUserProfile().getUid(), meeting.getUid());
        addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.cancel.success", request);
        return "redirect:/home";

    }

    @RequestMapping(value = "/meeting/modify", method=RequestMethod.POST, params={"reminder"})
    public String confirmReminder(Model model, @ModelAttribute("meeting") Meeting meeting, RedirectAttributes redirectAttributes,
                               HttpServletRequest request) {

        meeting = (Meeting) eventManagementService.loadEvent(meeting.getId());

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
    public String sendReminder(Model model, @RequestParam("entityId") Long meetingId, RedirectAttributes redirectAttributes,
                               HttpServletRequest request) {

        // todo: check for paid group & for feature enabled
        Meeting meeting = (Meeting) eventManagementService.loadEvent(meetingId);
        eventBroker.sendManualReminder( getUserProfile().getUid(), meeting.getUid(), "");
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

        // todo: this should be properly redesigned into directly calling "messaging service", bypassing whole fake event entity thing
        if (true) {
            throw new UnsupportedOperationException("This is not supported after event refactoring!!!");
        }
/*
        Event dummyEvent = eventManagementService.createEvent("", getUserProfile(), group, includeSubgroups);
        boolean messageSent = eventManagementService.sendManualReminder(dummyEvent, message);
        log.info("We just sent a free form message with result: " + messageSent);
*/

        redirectAttributes.addAttribute("groupUid", group.getUid());
        addMessage(redirectAttributes, MessageType.SUCCESS, "sms.message.sent", request);
        return "redirect:/group/view";

    }

    /**
     * RSVP handling
     */

    @RequestMapping(value = "rsvp")
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

        minuteOptions.add(oneDay);
        minuteOptions.add(halfDay);
        minuteOptions.add(oneHour);

        return minuteOptions;

    }
}
