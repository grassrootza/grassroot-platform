package za.org.grassroot.webapp.controller.webapp;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.LogBookBroker;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.LogBookWrapper;
import za.org.grassroot.webapp.model.web.MemberPicker;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Created by luke on 2016/01/02.
 */
@Controller
@RequestMapping("/log/")
public class LogBookController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(LogBookController.class);
    private static final DateTimeFormatter pickerParser = DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a");

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private LogBookService logBookService;

    @Autowired
    private LogBookBroker logBookBroker;

    @Autowired
    private EventBroker eventBroker;

    /**
     * SECTION: Views and methods for creating logbook entries
     */

    @RequestMapping("create")
    public String createLogBook(Model model, @RequestParam(value="groupUid", required=false) String groupUid) {

        // Thymeleaf insists on messing everything up if we try to set groupId, or just in general create the entity
        // on the next page instead of here, so we have to do some redundant & silly entity creation
        LogBookWrapper entryWrapper;
        User user = getUserProfile();

        if (groupUid == null || groupUid.trim().equals("")) {
            model.addAttribute("groupSpecified", false);
            model.addAttribute("possibleGroups", permissionBroker.
                    getActiveGroups(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY));
            entryWrapper = new LogBookWrapper(JpaEntityType.GROUP);
        } else {
            model.addAttribute("groupSpecified", true);
            Group group = groupBroker.load(groupUid);
            model.addAttribute("group", group);
            entryWrapper = new LogBookWrapper(JpaEntityType.GROUP, group.getUid(), group.getName(""));
        }

        model.addAttribute("entry", entryWrapper);
        return "log/create";
    }

    /*
    Okay, trying to implement new, meeting-as-parent-model
    todo: consolidate with above, once comfortable in how it works
     */

    @RequestMapping("create/meeting")
    public String createLogBookWithMeetingParent(Model model, @RequestParam String meetingUid) {

        Meeting parent = eventBroker.loadMeeting(meetingUid);
        LogBookWrapper wrapper = new LogBookWrapper(JpaEntityType.MEETING, meetingUid, parent.getName());

        model.addAttribute("parent", parent);
        model.addAttribute("logBook", wrapper);

        return "log/create_meeting";
    }

    @RequestMapping(value = "confirm", method = RequestMethod.POST)
    public String confirmLogBookEntry(Model model, @ModelAttribute("entry") LogBookWrapper logBookEntry,
                                      @RequestParam(value="selectedGroupUid", required = false) String selectedGroupUid,
                                      @RequestParam(value="subGroups", required=false) boolean subGroups, HttpServletRequest request) {

        log.info("The potential logBookEntry passed back to us ... " + logBookEntry);

        Group group;
        if (selectedGroupUid != null) {
            group = groupBroker.load(selectedGroupUid);
            logBookEntry.setParentEntityType(group.getJpaEntityType());
            logBookEntry.setParentUid(group.getUid());
            logBookEntry.setParentName(group.getName(""));
        } else {
            group = groupBroker.load(logBookEntry.getParentUid()); // todo: do we even need this?
        }

        model.addAttribute("group", group);
        model.addAttribute("replicatingToSubGroups", subGroups);

        if (subGroups) {
            // todo: use the tree methods to make this more coherent
            // todo: restrict this to paid groups, and add in message numbers / cost estimates
            model.addAttribute("numberSubGroups", groupBroker.subGroups(group.getUid()).size());
            model.addAttribute("numberMembers", userManagementService.fetchByGroup(group.getUid(), true).size());
        } else {
            logBookEntry.setMemberPicker(new MemberPicker(group, false));
        }

        model.addAttribute("reminderTime", logBookEntry.getScheduledReminderTime());
        model.addAttribute("entry", logBookEntry);

        return "log/confirm";

    }

    @RequestMapping(value = "record", method = RequestMethod.POST)
    public String recordLogBookEntry(Model model, @ModelAttribute("entry") LogBookWrapper logBookEntry,
                                     HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Set<String> assignedUids;
        if ("members".equals(logBookEntry.getAssignmentType())) {
            MemberPicker listOfMembers = logBookEntry.getMemberPicker();
            log.info("The memberUids are : ..." + Joiner.on(", ").join(listOfMembers.getSelectedUids()));
            assignedUids = listOfMembers.getSelectedUids();
        } else {
            assignedUids = Collections.emptySet();
        }

        User user = getUserProfile();

        LogBook created = logBookBroker.create(user.getUid(), logBookEntry.getParentEntityType(), logBookEntry.getParentUid(),
                                               logBookEntry.getMessage(), logBookEntry.getActionByDate(), logBookEntry.getReminderMinutes(),
                                               logBookEntry.isReplicateToSubGroups(), assignedUids);

        addMessage(redirectAttributes, MessageType.SUCCESS, "log.creation.success", request);
        redirectAttributes.addAttribute("logBookUid", created.getUid());

        return "redirect:/log/details";
    }

    @RequestMapping(value = "record/meeting", method = RequestMethod.POST)
    public String recordEntryWithMeetingParent(Model model, @ModelAttribute("logBook") LogBookWrapper logBook,
                                               HttpServletRequest request, RedirectAttributes attributes) {

        LogBook created = logBookBroker.create(getUserProfile().getUid(), logBook.getParentEntityType(),
                                               logBook.getParentUid(), logBook.getMessage(), logBook.getActionByDate(),
                                               logBook.getReminderMinutes(), false, Collections.emptySet());

        addMessage(attributes, MessageType.SUCCESS, "log.creation.success", request);
        attributes.addAttribute("logBookUid", created.getUid());

        return "redirect:/log/details";

    }

    /**
     * SECTION: Views and methods for examining a group's logbook
     * The standard view just looks at the entry as applied to the group ... There's a click through to check sub-group ones
     */
    @RequestMapping(value = "view")
    public String viewGroupLogBook(Model model, @RequestParam String groupUid) {

        log.info("Okay, pulling up logbook records ... primarily for the currently assigned group");

        Group group = groupBroker.load(groupUid);
        model.addAttribute("group", group);
        model.addAttribute("incompleteEntries", logBookService.getAllLogBookEntriesForGroup(group.getId(), false));

        List<LogBook> completedEntries = logBookService.getAllLogBookEntriesForGroup(group.getId(), true);
        model.addAttribute("completedEntries", completedEntries);
        log.info("Got back this many complete entries ... " + completedEntries.size());

        return "log/view";
    }

    @RequestMapping(value = "details")
    public String viewLogBookDetails(Model model, @RequestParam String logBookUid) {

        // todo: be able to view "children" of the log book once design changed to allow it
        // (replicate by logbook rather than group)

        log.info("Finding details about logbook entry with Id ..." + logBookUid);

        LogBook logBookEntry = logBookBroker.load(logBookUid);

        log.info("Retrieved logBook entry with these details ... " + logBookEntry);

        model.addAttribute("entry", logBookEntry);
        model.addAttribute("parent", logBookEntry.getParent());
        model.addAttribute("creatingUser", logBookEntry.getCreatedByUser());
        model.addAttribute("isComplete", logBookEntry.isCompleted());

        // todo: implement this using new design
/*
        if(logBookEntry.getAssignedToUser() != null)
            model.addAttribute("assignedToUser", logBookEntry.getAssignedToUser());
*/
        if(logBookEntry.isCompleted() && logBookEntry.getCompletedByUser() != null) {
            log.info("Entry is marked as completed, by user: " + logBookEntry.getCreatedByUser());
            model.addAttribute("completedByUser", logBookEntry.getCompletedByUser());
        }

        if (logBookService.hasReplicatedEntries(logBookEntry)) {
            log.info("Found replicated entries ... adding them to model");
            List<LogBook> replicatedEntries = logBookService.getAllReplicatedEntriesFromParentLogBook(logBookEntry);
            log.info("Here are the replicated entries ... " + replicatedEntries);
            List<Group> relevantSubGroups = logBookBroker.retrieveGroupsFromLogBooks(replicatedEntries);
            model.addAttribute("hasReplicatedEntries", true);
            model.addAttribute("replicatedEntries", replicatedEntries);
            model.addAttribute("replicatedGroups", relevantSubGroups);
            log.info("Here are the groups ... " + relevantSubGroups);
        }

        if (logBookEntry.getReplicatedGroup() != null) {
            log.info("This one is replicated from a parent logBook entry ...");
            LogBook parentEntry = logBookService.getParentLogBookEntry(logBookEntry);
            model.addAttribute("parentEntry", parentEntry);
            model.addAttribute("parentEntryGroup", logBookEntry.getReplicatedGroup());
        }

        return "log/details";
    }

    @RequestMapping("complete")
    public String completeLogBookForm(Model model, @RequestParam String logBookUid) {

        LogBook logBookEntry = logBookBroker.load(logBookUid);
        model.addAttribute("entry", logBookEntry);
        Set<User> assignedMembers = (logBookEntry.isAllGroupMembersAssigned()) ?
                logBookEntry.resolveGroup().getMembers() : logBookEntry.getAssignedMembers();
        model.addAttribute("assignedMembers", assignedMembers);

        return "log/complete";
    }

    // todo: check that this user has permission to mark the logbook entry as completed
    @RequestMapping(value = "complete-do", method = RequestMethod.POST)
    public String markLogBookEntryComplete(Model model, @RequestParam String logBookUid,
                                           @RequestParam(value="completedByAssigned", required=false) boolean completedByAssigned,
                                           @RequestParam(value="designateCompletingUser", required=false) boolean designateCompletor,
                                           @RequestParam(value="specifyCompletedDate", required=false) boolean setCompletedDate,
                                           @RequestParam(value="completingUserUid", required=false) String completedByUserUid,
                                           @RequestParam(value="completedOnDate", required=false) Timestamp completedOnDate,
                                           HttpServletRequest request) {

        // todo: refactor this quite a bit
        log.info("Marking logbook entry as completed ... ");

        LocalDateTime completedDate = (setCompletedDate) ? completedOnDate.toLocalDateTime() : LocalDateTime.now();

        LogBook logBook = logBookBroker.load(logBookUid);
        User completedByUser = userManagementService.load(completedByUserUid);

        if (completedByAssigned || !designateCompletor) {
            log.info("No user assigned, so either setting as complete today or specifying a completion date");
            if (setCompletedDate) {
                logBookBroker.complete(logBook.getUid(), completedDate, null);
            } else {
                logBookBroker.complete(logBook.getUid(), LocalDateTime.now(), null);
            }
        } else {
            log.info("User assigned, so marking it accordingly, with completing user uid: {}", completedByUserUid);
            if (setCompletedDate) {
                logBookBroker.complete(logBook.getUid(), completedDate, completedByUser.getUid());
            } else {
                logBookBroker.complete(logBook.getUid(), LocalDateTime.now(), completedByUser.getUid());
            }
        }

        addMessage(model, MessageType.SUCCESS, "log.completed.done", request);
        Group group = (Group) logBook.getParent();
        return viewGroupLogBook(model, group.getUid());
    }

    // todo : more permissions than just the below!
    @RequestMapping("modify")
    public String modifyLogBookEntry(Model model, @RequestParam(value="logBookId") Long logBookId) {
        LogBook logBook = logBookService.load(logBookId);
        Group group = (Group) logBook.getParent();
        if (!group.getMembers().contains(getUserProfile())) throw new AccessDeniedException("");

        model.addAttribute("logBook", logBook);
        model.addAttribute("group", group);
        model.addAttribute("groupMembers", group.getMembers());
        model.addAttribute("reminderTime", reminderTimeDescriptions().get(logBook.getReminderMinutes()));
        model.addAttribute("reminderTimeOptions", reminderTimeDescriptions());

        // todo: implement this using new design
/*
        if (logBook.getAssignedToUser() != null)
            model.addAttribute("assignedUser", logBook.getAssignedToUser());
*/

        return "log/modify";
    }

    // todo: permission checking
    @RequestMapping(value = "modify", method = RequestMethod.POST)
    public String changeLogBookEntry(Model model, @ModelAttribute("logBook") LogBook logBook,
                                     @RequestParam(value = "assignToUser", required = false) boolean assignToUser, HttpServletRequest request) {

        // may consider doing some of this in services layer, but main point is can't just use logBook entity passed
        // back from form as thymeleaf whacks all the attributes we don't explicitly code into hidden inputs

        LogBook savedLogBook = logBookService.load(logBook.getId());
        if (!logBook.getMessage().equals(savedLogBook.getMessage()))
            savedLogBook.setMessage(logBook.getMessage());

        if (!logBook.getActionByDate().equals(savedLogBook.getActionByDate()))
            savedLogBook.setActionByDate(logBook.getActionByDate());

        if (logBook.getReminderMinutes() != savedLogBook.getReminderMinutes())
            savedLogBook.setReminderMinutes(logBook.getReminderMinutes());

        log.info("Are we going to assigned this to a user? ... " + assignToUser);
        // todo: implement this using new design
/*
        if (!assignToUser)
            savedLogBook.setAssignedToUser(null);
        else
            savedLogBook.setAssignedToUser(logBook.getAssignedToUser());
*/

        savedLogBook = logBookService.save(savedLogBook);

        addMessage(model, MessageType.SUCCESS, "log.modified.done", request);
        return viewLogBookDetails(model, savedLogBook.getUid());
    }

    private Map<Integer, String> reminderTimeDescriptions() {
        // could have created this once off, but doing it through function, for i18n later
        Map<Integer, String> descriptions = new LinkedHashMap<>();
        descriptions.put(-60, "On due date");
        descriptions.put(-1440, "One day before");
        descriptions.put(-2880, "Two days before");
        descriptions.put(-10080, "A week before deadline");
        return descriptions;
    }

}
