package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.MeetingImportance;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.MeetingWrapper;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static za.org.grassroot.core.util.DateTimeUtil.getSAST;


/**
 * Created by luke on 2015/09/11.
 */
@Controller
@RequestMapping("/meeting/")
@SessionAttributes("meeting")
public class MeetingController extends BaseController {

    Logger log = LoggerFactory.getLogger(MeetingController.class);

    private GroupBroker groupBroker;
    private EventBroker eventBroker;
    private PermissionBroker permissionBroker;
    private EventLogBroker eventLogBroker;
    private AccountGroupBroker accountBroker;

    @Autowired
    public MeetingController(GroupBroker groupBroker, EventBroker eventBroker, PermissionBroker permissionBroker,
                             EventLogBroker eventLogBroker, AccountGroupBroker accountBroker) {
        this.groupBroker = groupBroker;
        this.eventBroker = eventBroker;
        this.permissionBroker = permissionBroker;
        this.eventLogBroker = eventLogBroker;
        this.accountBroker = accountBroker;
    }

    /**
     * Meeting creation
     */

    @RequestMapping("create")
    public String createMeetingIndex(Model model, @RequestParam(value="groupUid", required=false) String groupUid) {

        MeetingWrapper meeting = MeetingWrapper.makeEmpty(EventReminderType.GROUP_CONFIGURED, 24*60, true);

        if (groupUid != null) {
            Group group = groupBroker.load(groupUid);
            model.addAttribute("group", group);
            meeting.setParentUid(groupUid);
        } else {
            User user = userManagementService.load(getUserProfile().getUid()); // refresh user entity, in case permissions changed
            model.addAttribute("userGroups", permissionBroker.getActiveGroupsSorted(user, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING));
        }

        model.addAttribute("meeting", meeting);
        model.addAttribute("reminderOptions", reminderMinuteOptions(false));

        log.info("Wrapper we are passing: " + meeting.toString());
        return "meeting/create";

    }

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createMeeting(Model model, @ModelAttribute("meeting") MeetingWrapper meeting, BindingResult bindingResult,
                                @RequestParam(value="selectedGroupUid", required=false) String selectedGroupUid,
                                HttpServletRequest request, RedirectAttributes redirectAttributes) {

        // todo: add error handling and validation

        log.info("The meeting wrapper as passed back to us: " + meeting.toString());

        // todo: move parent selection into MeetingWrapper when implement non-group meetings
        // todo : clean up a lot of old / now redundant properties (include rsvp, is relayable, etc
        try {
            eventBroker.createMeeting(getUserProfile().getUid(), selectedGroupUid, JpaEntityType.GROUP,
                    meeting.getTitle(), meeting.getEventDateTime(), meeting.getLocation(),
                    meeting.isIncludeSubGroups(), true, meeting.isRelayable(),
                    meeting.getReminderType(), meeting.getCustomReminderMinutes(), meeting.getDescription(),
                    Collections.emptySet(), MeetingImportance.ORDINARY);

            addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.creation.success", request);
            redirectAttributes.addAttribute("groupUid", selectedGroupUid);
            return "redirect:/group/view";
        } catch (EventStartTimeNotInFutureException e) {
            addMessage(model, MessageType.ERROR, "meeting.creation.time.error", request);
            Group group = groupBroker.load(selectedGroupUid);
            model.addAttribute("group", group);
            model.addAttribute("reminderOptions", reminderMinuteOptions(false));
            return "meeting/create";
        }
    }

    /**
     * Meeting view and modification
     */

    @RequestMapping("view")
    public String viewMeetingDetails(Model model, @RequestParam String eventUid) {

        Meeting meeting = eventBroker.loadMeeting(eventUid);
        User user = getUserProfile();

        ResponseTotalsDTO meetingResponses = eventLogBroker.getResponseCountForEvent(meeting);
        boolean canViewRsvps = meeting.getCreatedByUser().equals(user) || permissionBroker.isGroupPermissionAvailable(
                user, meeting.getAncestorGroup(), Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS);
        boolean canAlterDetails = meeting.getCreatedByUser().equals(user) ||
                permissionBroker.isGroupPermissionAvailable(user, meeting.getAncestorGroup(), Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        model.addAttribute("meeting", new MeetingWrapper(meeting));
        model.addAttribute("responseTotals", meetingResponses);
        model.addAttribute("canViewRsvps", canViewRsvps);

        model.addAttribute("canAlterDetails", canAlterDetails);

        if (canViewRsvps) {
            // this is clunky, but it's for Thymeleaf
            Set<Map.Entry<User, EventRSVPResponse>> rsvpResponses = eventBroker.getRSVPResponses(meeting).entrySet();
            model.addAttribute("rsvpResponses", rsvpResponses);
        }

        if (canAlterDetails) {
            model.addAttribute("reminderOptions", EventReminderType.values());
            model.addAttribute("customReminderOptions", reminderMinuteOptions(false));
            model.addAttribute("todos", meeting.getTodos());
        }

        if (meeting.getScheduledReminderTime() != null)
            model.addAttribute("scheduledReminderTime", DateTimeUtil.convertToUserTimeZone(meeting.getScheduledReminderTime(),
                                                                                           getSAST()));

        return "meeting/view";
    }


    @RequestMapping(value = "modify", method=RequestMethod.POST)
    public String changeMeeting(Model model, @ModelAttribute("meeting") MeetingWrapper changedMeeting, HttpServletRequest request) {

        log.info("Okay, here is the meeting passed back ... " + changedMeeting);

        eventBroker.updateMeeting(getUserProfile().getUid(), changedMeeting.getEntityUid(), changedMeeting.getTitle(),
                                  changedMeeting.getEventDateTime(), changedMeeting.getLocation());

        addMessage(model, MessageType.SUCCESS, "meeting.update.success", request);
        return viewMeetingDetails(model, changedMeeting.getEntityUid());
    }

    @RequestMapping(value = "cancel", method=RequestMethod.POST)
    public String cancelMeeting(Model model, @ModelAttribute("meeting") MeetingWrapper meeting, BindingResult bindingResult,
                                RedirectAttributes redirectAttributes, HttpServletRequest request) {

        log.info("Meeting that is about to be cancelled: " + meeting.toString());
        eventBroker.cancel(getUserProfile().getUid(), meeting.getEntityUid());
        addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.cancel.success", request);
        return "redirect:/home";

    }

    @RequestMapping(value = "reminder", method=RequestMethod.POST)
    public String changeReminderSettings(Model model, @RequestParam String eventUid, @RequestParam EventReminderType reminderType,
                                         @RequestParam(value = "custom_minutes", required = false) Integer customMinutes, HttpServletRequest request) {

        // todo: maybe move this into a common place ... since call it from all controllers ...
        int minutes = (customMinutes != null && reminderType.equals(EventReminderType.CUSTOM)) ? customMinutes : 0;
        eventBroker.updateReminderSettings(getUserProfile().getUid(), eventUid, reminderType, minutes);
        Instant newScheduledTime = eventBroker.loadMeeting(eventUid).getScheduledReminderTime();
        if (newScheduledTime != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a' on 'E, d MMMM").withZone(ZoneId.systemDefault());
            String reminderTime = formatter.format(newScheduledTime);
            addMessage(model, MessageType.SUCCESS, "meeting.reminder.changed", new String[] {reminderTime}, request);
        } else {
            addMessage(model, MessageType.SUCCESS, "meeting.reminder.changed.none", request);
        }
        return viewMeetingDetails(model, eventUid);
    }


    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/meeting/remind", method=RequestMethod.POST)
    public String sendReminder(Model model, @RequestParam("entityUid") String meetingUid, RedirectAttributes redirectAttributes,
                               HttpServletRequest request) {

        // todo: check for paid group & for feature enabled
        Meeting meeting = eventBroker.loadMeeting(meetingUid);
        eventBroker.sendManualReminder( getUserProfile().getUid(), meeting.getUid());
        addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.reminder.success", request);
        return "redirect:/home";

    }

    /**
     * Free text entry, for authorized accounts
     */

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "free")
    public String sendFreeForm(Model model, @RequestParam(value="groupUid", required=false) String groupUid) {

        boolean groupSpecified;
        User sessionUser = getUserProfile();

        if (groupUid != null) {
            model.addAttribute("group", groupBroker.load(groupUid));
            groupSpecified = true;
        } else {
            model.addAttribute("userGroups", permissionBroker.getActiveGroupsSorted(sessionUser, null));
            groupSpecified = false;
        }
        model.addAttribute("groupSpecified", groupSpecified); // slightly redundant, but use it to tell Thymeleaf what to do
        return "meeting/free";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "free", method = RequestMethod.POST)
    public String confirmFreeMsg(Model model, @RequestParam String groupUid, @RequestParam(value="message") String message) {

        model.addAttribute("action", "free");
        model.addAttribute("groupUid", groupUid);

        model.addAttribute("message", message);
        Group group = groupBroker.load(groupUid);

        int recipients = group.getMembers().size();
        model.addAttribute("recipients", recipients);
        model.addAttribute("cost", recipients * 0.2);
        return "meeting/remind_confirm";

    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "free", method = RequestMethod.POST, params = {"confirmed"})
    public String sendFreeMsg(Model model, @RequestParam(value="groupUid") String groupUid,
                              @RequestParam(value="message") String message,
                              RedirectAttributes redirectAttributes, HttpServletRequest request) {

        // todo: check that this group is paid for (and filter on previous page)
        log.info("Sending free form message: {}, to this group: {}", message, groupUid);

        try {
            accountBroker.sendFreeFormMessage(getUserProfile().getUid(), groupUid, message);
            addMessage(redirectAttributes, MessageType.SUCCESS, "sms.message.sent", request);
            log.info("Sent message, redirecting to home");

        } catch (AccessDeniedException e) {
            addMessage(redirectAttributes, MessageType.ERROR, "sms.message.error", request);
            e.printStackTrace();
        }
        return "redirect:/home";
    }

    /**
     * RSVP handling
     */

    @RequestMapping(value = "rsvp")
    public String rsvpYes(Model model, @RequestParam String eventUid, @RequestParam String answer,
                          HttpServletRequest request, RedirectAttributes attributes) {

        Meeting meeting = eventBroker.loadMeeting(eventUid);
        User user = getUserProfile();

        String priorUrl = request.getHeader(HttpHeaders.REFERER);
        String redirect;

        if (priorUrl.contains("group")) {
            attributes.addAttribute("groupUid", meeting.getAncestorGroup().getUid());
            redirect = "/group/view";
        } else if (priorUrl.contains("meeting")) {
            attributes.addAttribute("eventUid", eventUid);
            redirect = "/meeting/view";
        } else {
            redirect = "/home";
        }

        switch (answer) {
            case "yes":
                eventLogBroker.rsvpForEvent(meeting.getUid(), user.getUid(), EventRSVPResponse.YES);
                addMessage(attributes, MessageType.SUCCESS, "meeting.rsvp.yes", request);
                break;
            case "no":
                eventLogBroker.rsvpForEvent(meeting.getUid(), user.getUid(), EventRSVPResponse.NO);
                addMessage(attributes, MessageType.ERROR, "meeting.rsvp.no", request);
                break;
            default:
                addMessage(attributes, MessageType.ERROR, "meeting.rsvp.no", request);
                break;
        }

        return "redirect:" + redirect;
    }

}
