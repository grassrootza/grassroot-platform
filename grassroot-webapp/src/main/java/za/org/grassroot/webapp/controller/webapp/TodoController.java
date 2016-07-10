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
import za.org.grassroot.webapp.model.web.TodoWrapper;
import za.org.grassroot.webapp.model.web.MemberPicker;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Created by luke on 2016/01/02.
 */
@Controller
@RequestMapping("/log/")
public class TodoController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(TodoController.class);
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
    public String createTodo(Model model, @RequestParam(value="groupUid", required=false) String parentUid,
                             @RequestParam(value="parentType", required=false) JpaEntityType parentType) {

        TodoWrapper entryWrapper;

        // todo: clean this up / consolidate it
        if (JpaEntityType.MEETING.equals(parentType)) {

            Meeting parent = eventBroker.loadMeeting(parentUid);
            TodoWrapper wrapper = new TodoWrapper(JpaEntityType.MEETING, parentUid, parent.getName());

            model.addAttribute("parent", parent);
            model.addAttribute("logBook", wrapper);

            return "log/create_meeting";

        } else {

            if (parentUid == null || parentUid.trim().equals("")) {

                // reload user entity in case things have changed during session (else bug w/ list of possible groups)
                User userFromDb = userManagementService.load(getUserProfile().getUid());
                model.addAttribute("groupSpecified", false);
                model.addAttribute("userUid", userFromDb.getUid());
                model.addAttribute("possibleGroups", permissionBroker.
                        getActiveGroups(userFromDb, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY));
                entryWrapper = new TodoWrapper(JpaEntityType.GROUP);

            } else {

                model.addAttribute("groupSpecified", true);
                Group group = groupBroker.load(parentUid);
                model.addAttribute("group", group);
                entryWrapper = new TodoWrapper(JpaEntityType.GROUP, group.getUid(), group.getName(""));
                entryWrapper.setMemberPicker(new MemberPicker(group, false));
            }

            entryWrapper.setAssignmentType("group");
            entryWrapper.setReminderType(EventReminderType.GROUP_CONFIGURED);
            entryWrapper.setReminderMinutes(-60);

            model.addAttribute("entry", entryWrapper);
            return "log/create";

        }

    }

    @RequestMapping(value = "record", method = RequestMethod.POST)
    public String recordTodo(@ModelAttribute("entry") TodoWrapper logBookEntry,
                             HttpServletRequest request, RedirectAttributes redirectAttributes) {

        log.info("TodoWrapper received, looks like: {}", logBookEntry.toString());

        if (logBookEntry.getReminderType().equals(EventReminderType.GROUP_CONFIGURED)) {
            int convertedMinutes = -(groupBroker.load(logBookEntry.getParentUid()).getReminderMinutes());
            logBookEntry.setReminderMinutes(convertedMinutes);
        }

        Set<String> assignedUids;
        if ("members".equals(logBookEntry.getAssignmentType())) {
            MemberPicker listOfMembers = logBookEntry.getMemberPicker();
            log.info("The memberUids are : ..." + Joiner.on(", ").join(listOfMembers.getSelectedUids()));
            assignedUids = listOfMembers.getSelectedUids();
        } else {
            assignedUids = Collections.emptySet();
        }

        Long startTime = System.currentTimeMillis();
        LogBook created = logBookBroker.create(getUserProfile().getUid(), logBookEntry.getParentEntityType(), logBookEntry.getParentUid(),
                                               logBookEntry.getMessage(), logBookEntry.getActionByDate(), logBookEntry.getReminderMinutes(),
                                               logBookEntry.isReplicateToSubGroups(), assignedUids);
        log.info("Time to create, store, logbooks: {} msecs", System.currentTimeMillis() - startTime);

        addMessage(redirectAttributes, MessageType.SUCCESS, "log.creation.success", request);
        // redirectAttributes.addAttribute("logBookUid", created.getUid());

        return "redirect:/home";
    }

    @RequestMapping(value = "record/meeting", method = RequestMethod.POST)
    public String recordEntryWithMeetingParent(Model model, @ModelAttribute("logBook") TodoWrapper logBook,
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
        model.addAttribute("incompleteEntries", logBookService.getAllLogBookEntriesForGroup(group, false));

        List<LogBook> completedEntries = logBookService.getAllLogBookEntriesForGroup(group, true);
        model.addAttribute("completedEntries", completedEntries);
        log.info("Got back this many complete entries ... " + completedEntries.size());

        return "log/view";
    }

    @RequestMapping(value = "details")
    public String viewTodoDetails(Model model, @RequestParam String logBookUid) {

        // todo: be able to view "children" of the log book once design changed to allow it
        // (replicate by logbook rather than group)

        log.info("Finding details about logbook entry with Id ..." + logBookUid);

        LogBook logBookEntry = logBookBroker.load(logBookUid);

        log.info("Retrieved logBook entry with these details ... " + logBookEntry);

        model.addAttribute("entry", logBookEntry);
        model.addAttribute("parent", logBookEntry.getParent());
        model.addAttribute("creatingUser", logBookEntry.getCreatedByUser());
        model.addAttribute("isComplete", logBookEntry.isCompleted());

        if(logBookEntry.isCompleted()) {
            log.info("Entry is marked as completed, by user: " + logBookEntry.getCreatedByUser());
            model.addAttribute("completedByUser", "<UNKNOWN>");
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
    public String completeTodoForm(Model model, @RequestParam String logBookUid) {

        LogBook logBookEntry = logBookBroker.load(logBookUid);
        model.addAttribute("entry", logBookEntry);
        Set<User> assignedMembers = (logBookEntry.isAllGroupMembersAssigned()) ?
                logBookEntry.getAncestorGroup().getMembers() : logBookEntry.getAssignedMembers();
        model.addAttribute("assignedMembers", assignedMembers);

        return "log/complete";
    }

    @RequestMapping(value = "complete-do", method = RequestMethod.POST)
    public String confirmTodoComplete(Model model, @RequestParam String logBookUid,
                                      @RequestParam(value="completedByAssigned", required=false) boolean completedByAssigned,
                                      @RequestParam(value="designateCompletingUser", required=false) boolean designateCompletor,
                                      @RequestParam(value="specifyCompletedDate", required=false) boolean setCompletedDate,
                                      @RequestParam(value="completingUserUid", required=false) String completedByUserUid,
                                      @RequestParam(value="completedOnDate", required=false) LocalDateTime completedOnDate,
                                      HttpServletRequest request) {

        log.info("Marking logbook entry as completed ... ");

        LocalDateTime completedDate = (setCompletedDate) ? completedOnDate : LocalDateTime.now();

	    String sessionUserUid = getUserProfile().getUid();
        LogBook logBook = logBookBroker.load(logBookUid);

        if (setCompletedDate) {
	        logBookBroker.confirmCompletion(sessionUserUid, logBook.getUid(), completedDate);
        } else {
	        logBookBroker.confirmCompletion(sessionUserUid, logBook.getUid(), LocalDateTime.now());
        }

        addMessage(model, MessageType.SUCCESS, "log.completed.done", request);
        Group group = (Group) logBook.getParent();
        return viewGroupLogBook(model, group.getUid());
    }

    // todo : more permissions than just the below!
    @RequestMapping("modify")
    public String modifyTodo(Model model, @RequestParam(value="logBookUid") String logBookUid) {

        LogBook logBook = logBookBroker.load(logBookUid);
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
    public String changeTodoEntry(Model model, @ModelAttribute("logBook") LogBook logBook,
                                  @RequestParam(value = "assignToUser", required = false) boolean assignToUser, HttpServletRequest request) {

        // may consider doing some of this in services layer, but main point is can't just use logBook entity passed
        // back from form as thymeleaf whacks all the attributes we don't explicitly code into hidden inputs

        LogBook savedLogBook = logBookBroker.load(logBook.getUid());
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
        return viewTodoDetails(model, savedLogBook.getUid());
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