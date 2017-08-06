package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.ActionLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.services.task.MeetingBuilderHelper;
import za.org.grassroot.services.task.TaskImageBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.enums.EntityPublicOption;
import za.org.grassroot.webapp.model.web.MeetingWrapper;
import za.org.grassroot.webapp.model.web.MemberPicker;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.DateTimeUtil.getSAST;


/**
 * Created by luke on 2015/09/11.
 */
@Controller
@RequestMapping("/meeting/")
@SessionAttributes("meeting")
public class MeetingController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(MeetingController.class);

    private final GroupBroker groupBroker;
    private final EventBroker eventBroker;
    private final EventLogBroker eventLogBroker;
    private final AccountGroupBroker accountBroker;
    private final TaskImageBroker taskImageBroker;

    @Autowired
    public MeetingController(GroupBroker groupBroker, EventBroker eventBroker, EventLogBroker eventLogBroker,
                             AccountGroupBroker accountBroker, TaskImageBroker taskImageBroker) {
        this.groupBroker = groupBroker;
        this.eventBroker = eventBroker;
        this.eventLogBroker = eventLogBroker;
        this.accountBroker = accountBroker;
        this.taskImageBroker = taskImageBroker;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(2048);
    }

    /**
     * Meeting creation
     */

    @RequestMapping(value = "create", method = RequestMethod.GET)
    public String createMeetingIndex(Model model, @RequestParam(value="groupUid", required=false) String groupUid,
                                     RedirectAttributes attributes, HttpServletRequest request) {

        MeetingWrapper meeting = MeetingWrapper.makeEmpty(EventReminderType.GROUP_CONFIGURED, 24*60);

        if (permissionBroker.countActiveGroupsWithPermission(getUserProfile(), Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING) == 0) {
            addMessage(attributes, MessageType.INFO, "meeting.create.group", request);
            return "redirect:/group/create";
        } else {
            if (groupUid != null) {
                Group group = groupBroker.load(groupUid);
                setModelForSelectedGroup(model, group);
                meeting.setMemberPicker(MemberPicker.create(group, JpaEntityType.GROUP, true));
                meeting.setParentUid(groupUid);
            } else {
                User user = userManagementService.load(getUserProfile().getUid()); // refresh user entity, in case permissions changed
                setModelWithoutSelectedGroup(model, user);
            }

            meeting.setAssignmentType("group");

            model.addAttribute("meeting", meeting);
            model.addAttribute("reminderOptions", reminderMinuteOptions(false));

            return "meeting/create";
        }
    }

    private void setModelForSelectedGroup(Model model, Group group) {
        final String groupUid = group.getUid();
        model.addAttribute("group", group);
        model.addAttribute("parentSpecified", true);
        model.addAttribute("thisGroupPaidFor", accountBroker.isGroupOnAccount(groupUid)); // slightly more robust check than "is paid for"
        model.addAttribute("eventsLeft", accountBroker.numberEventsLeftForGroup(groupUid));
        model.addAttribute("accountAdmin", getUserProfile().getPrimaryAccount() != null);
    }

    private void setModelWithoutSelectedGroup(Model model, User user) {
        model.addAttribute("parentSpecified", false);
        model.addAttribute("thisGroupPaidFor", false); // by definition ... instead taken from group properties
        model.addAttribute("userGroups", permissionBroker.getActiveGroupsSorted(user, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING));
        model.addAttribute("eventsLeft", 99);
        model.addAttribute("accountAdmin", user.getPrimaryAccount() != null);
    }

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createMeeting(Model model,
                                @ModelAttribute("meeting") MeetingWrapper meeting, BindingResult bindingResult,
                                @RequestParam(value="selectedGroupUid", required=false) String selectedGroupUid,
                                HttpServletRequest request, RedirectAttributes redirectAttributes) {

        // todo: move parent selection into MeetingWrapper when implement non-group meetings
        log.info("Meeting long: {}, lat: {}, public option: {}, file incl: {}", meeting.getLongitude(), meeting.getLatitude(),
                meeting.getPublicOption(), meeting.getMeetingImage() != null);

        try {
            Set<String> invitedMemberUids = "members".equalsIgnoreCase(meeting.getAssignmentType()) ?
                    meeting.getMemberPicker().getSelectedUids() : Collections.emptySet();

            if (!invitedMemberUids.isEmpty() && !invitedMemberUids.contains(getUserProfile().getUid())) {
                invitedMemberUids.add(getUserProfile().getUid()); // in future ask if they're sure
            }

            MeetingBuilderHelper helper = new MeetingBuilderHelper()
                    .userUid(getUserProfile().getUid())
                    .parentType(JpaEntityType.GROUP)
                    .parentUid(selectedGroupUid)
                    .name(meeting.getTitle())
                    .startDateTime(meeting.getEventDateTime())
                    .location(meeting.getLocation())
                    .reminderType(meeting.getReminderType())
                    .customReminderMinutes(meeting.getCustomReminderMinutes())
                    .description(meeting.getDescription())
                    .assignedMemberUids(invitedMemberUids)
                    .importance(meeting.getImportance())
                    .includeSubGroups(meeting.isIncludeSubGroups());

            // todo: move this into async when the user hits upload
            if (meeting.getMeetingImage() != null && meeting.getMeetingImage().getSize() > 0) {
                log.info("meeting image not null! size: {} bytes", meeting.getMeetingImage().getSize());
                helper.taskImageKey(taskImageBroker.storeImagePreTask(TaskType.MEETING, meeting.getMeetingImage()));
            }

            Meeting createdMeeting = eventBroker.createMeeting(helper);

            if (!EntityPublicOption.PRIVATE.equals(meeting.getPublicOption())) {
                makeMeetingPublic(meeting, createdMeeting);
            }

            addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.creation.success", request);
            redirectAttributes.addAttribute("groupUid", selectedGroupUid);
            return "redirect:/group/view";
        } catch (EventStartTimeNotInFutureException e) {
            addMessage(model, MessageType.ERROR, "meeting.creation.time.error", request);
            Group group = groupBroker.load(selectedGroupUid);
            model.addAttribute("group", group);
            model.addAttribute("reminderOptions", reminderMinuteOptions(false));
            return "meeting/create";
        } catch (AccountLimitExceededException e) {
            addMessage(model, MessageType.ERROR, "meeting.creation.limit.error", request);
            User user = userManagementService.load(getUserProfile().getUid());
            setModelWithoutSelectedGroup(model, user);
            return "meeting/create";
        }
    }

    // todo : shift to async?
    private void makeMeetingPublic(MeetingWrapper meetingWrapper, Meeting createdMeeting) {
        log.info("marking meeting public ... option: {}", meetingWrapper.getPublicOption());
        if (EntityPublicOption.PUBLIC_GET_GPS.equals(meetingWrapper.getPublicOption()) &&
                meetingWrapper.hasLongLat()) {
            eventBroker.updateMeetingPublicStatus(getUserProfile().getUid(), createdMeeting.getUid(),
                    true, new GeoLocation(meetingWrapper.getLatitude(), meetingWrapper.getLongitude()), UserInterfaceType.WEB);
        } else {
            eventBroker.updateMeetingPublicStatus(getUserProfile().getUid(), createdMeeting.getUid(), true, null, UserInterfaceType.WEB);
        }

    }

    /**
     * Meeting view and modification
     */

    @RequestMapping("view")
    public String viewMeetingDetails(Model model, @RequestParam String eventUid, @RequestParam(required = false) SourceMarker source) {

        Meeting meeting = eventBroker.loadMeeting(eventUid);
        User user = getUserProfile();

        ResponseTotalsDTO meetingResponses = eventLogBroker.getResponseCountForEvent(meeting);
        boolean canViewRsvps = meeting.getCreatedByUser().equals(user) || permissionBroker.isGroupPermissionAvailable(
                user, meeting.getAncestorGroup(), Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS);
        boolean canAlterDetails = meeting.getCreatedByUser().equals(user) ||
                permissionBroker.isGroupPermissionAvailable(user, meeting.getAncestorGroup(), Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        model.addAttribute("meeting", meeting);
        model.addAttribute("responseTotals", meetingResponses);
        model.addAttribute("canViewRsvps", canViewRsvps);

        model.addAttribute("canAlterDetails", canAlterDetails);

        model.addAttribute("meetingImages",
                taskImageBroker.countImagesForTask(getUserProfile().getUid(), eventUid, TaskType.MEETING));

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
        model.addAttribute("fromGroup", SourceMarker.GROUP.equals(source));
        model.addAttribute("parentUid", meeting.getParent().getUid());

        model.addAttribute("memberPicker", MemberPicker.taskAssigned(meeting));

        return "meeting/view";
    }

    @RequestMapping(value = "modify", method=RequestMethod.POST)
    public String changeMeeting(Model model, @RequestParam String eventUid, @RequestParam String location,
                                @RequestParam(required = false) LocalDateTime eventDateTime, HttpServletRequest request) {
        log.info("Changing meeting, location: {}, datetime: {}", location,eventDateTime);
        if (eventDateTime == null || eventDateTime.isAfter(LocalDateTime.now())) {
            boolean change = eventBroker.updateMeeting(getUserProfile().getUid(), eventUid, null, eventDateTime, location);
            if (change) {
                addMessage(model, MessageType.SUCCESS, "meeting.update.success", request);
            } else {
                addMessage(model, MessageType.INFO, "meeting.update.unchanged", request);
            }
        } else {
            addMessage(model, MessageType.ERROR, "meeting.update.error.time", request);
        }
        return viewMeetingDetails(model, eventUid, null);
    }

    @PostMapping("description")
    public String changeDescription(@RequestParam String eventUid, @RequestParam String meetingDescription,
                                    RedirectAttributes attributes, HttpServletRequest request) {
        eventBroker.updateDescription(getUserProfile().getUid(), eventUid, meetingDescription);
        attributes.addAttribute("eventUid", eventUid);
        addMessage(attributes, MessageType.SUCCESS, "meeting.description.changed", request);
        return "redirect:/meeting/view";
    }

    @RequestMapping(value = "cancel", method=RequestMethod.POST)
    public String cancelMeeting(@RequestParam String eventUid, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        eventBroker.cancel(getUserProfile().getUid(), eventUid);
        addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.cancel.success", request);
        return "redirect:/home"; // todo : send to group if came from group
    }

    @PostMapping("assignment")
    public String changeAssignment(@RequestParam String eventUid, @ModelAttribute("memberPicker") MemberPicker memberPicker,
                                   RedirectAttributes attributes, HttpServletRequest request) {
        Event meeting = eventBroker.load(eventUid);
        attributes.addAttribute("eventUid", eventUid);

        Set<String> assignedUids = memberPicker.getSelectedUids();
        if (assignedUids.isEmpty()) {
            addMessage(attributes, MessageType.ERROR, "meeting.assigned.error.empty", request);
            return "redirect:/todo/view";
        }

        @SuppressWarnings("unchecked")
        Set<User> priorUsers = (Set<User>) meeting.getMembers();
        Set<String> originalUids = priorUsers.stream().map(User::getUid).collect(Collectors.toSet());

        Set<String> addedUids = assignedUids.stream()
                .filter(s -> !originalUids.contains(s))
                .collect(Collectors.toSet());

        Set<String> removedUids = originalUids.stream()
                .filter(s -> !assignedUids.contains(s))
                .collect(Collectors.toSet());

        eventBroker.removeAssignedMembers(getUserProfile().getUid(), eventUid, removedUids);
        eventBroker.assignMembers(getUserProfile().getUid(), eventUid, addedUids);

        addMessage(attributes, MessageType.SUCCESS, "meeting.assigned.changed", request);
        return "redirect:/meeting/view";
    }

    @RequestMapping(value = "reminder", method=RequestMethod.POST)
    public String changeReminderSettings(Model model, @RequestParam String eventUid, @RequestParam EventReminderType reminderType,
                                         @RequestParam(value = "custom_minutes", required = false) Integer customMinutes, HttpServletRequest request) {
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
        return viewMeetingDetails(model, eventUid, null);
    }


    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/meeting/remind", method=RequestMethod.POST)
    public String sendReminder(@RequestParam("entityUid") String meetingUid, RedirectAttributes redirectAttributes,
                               HttpServletRequest request) {
        // todo: check for paid group & for feature enabled
        Meeting meeting = eventBroker.loadMeeting(meetingUid);
        eventBroker.sendManualReminder( getUserProfile().getUid(), meeting.getUid());
        addMessage(redirectAttributes, MessageType.SUCCESS, "meeting.reminder.success", request);
        return "redirect:/home";

    }

    /**
     * RSVP handling
     */

    @RequestMapping(value = "rsvp")
    public String rsvpYes(@RequestParam String eventUid, @RequestParam String answer, HttpServletRequest request, RedirectAttributes attributes) {

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
