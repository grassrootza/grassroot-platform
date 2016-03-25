package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.LogBookBroker;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2016/01/02.
 */
@Controller
public class LogBookController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(LogBookController.class);
    private static final DateTimeFormatter pickerParser = DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a");

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private LogBookService logBookService;

    @Autowired
    private LogBookBroker logBookBroker;

    /**
     * SECTION: Views and methods for creating logbook entries
     */

    @RequestMapping("/log/create")
    public String createLogBook(Model model, @RequestParam(value="groupUid", required=false) String groupUid) {

        // Thymeleaf insists on messing everything up if we try to set groupId, or just in general create the entity
        // on the next page instead of here, so we have to do some redundant & silly entity creation
        LogBook logBookToFill = LogBook.makeEmpty();

        if (groupUid == null || groupUid.trim().equals("")) {
            log.info("No group specified, pass a list and let user choose");
            model.addAttribute("groupSpecified", false);
            // todo: make this use permissions logic so only pass groups for which the user has permission to do this
            model.addAttribute("possibleGroups", groupManagementService.getActiveGroupsPartOf(getUserProfile()));
        } else {
            log.info("User came here from a group view so set group as specified");
            model.addAttribute("groupSpecified", true);
            // todo: another permission check
            Group group = groupBroker.load(groupUid);
            model.addAttribute("group", group);
            logBookToFill.setGroup(group);
        }

        model.addAttribute("entry", logBookToFill);
        return "log/create";
    }

    @RequestMapping(value = "/log/confirm", method = RequestMethod.POST)
    public String confirmLogBookEntry(Model model, @ModelAttribute("entry") LogBook logBookEntry, BindingResult bindingResult,
                                      @RequestParam(value="subGroups", required=false) boolean subGroups, HttpServletRequest request) {

        log.info("The potential logBookEntry passed back to us ... " + logBookEntry);
        log.info("Value of subGroups passed ... " + subGroups);

        Group groupSelected = logBookEntry.getGroup();
        model.addAttribute("group", groupSelected);
        model.addAttribute("subGroups", subGroups);

        if (subGroups) {
            // todo: use the tree methods to make this more coherent
            // todo: restrict this to paid groups, and add in message numbers / cost estimates
            model.addAttribute("numberSubGroups", groupManagementService.getSubGroups(groupSelected).size());
            model.addAttribute("numberMembers", groupManagementService.getGroupSize(groupSelected.getId(), true));
        } else {
            model.addAttribute("groupMembers", groupSelected.getMembers());
        }

        model.addAttribute("reminderTime", reminderTimeDescriptions().get(logBookEntry.getReminderMinutes()));

        return "log/confirm";

    }

    @RequestMapping(value = "/log/record", method = RequestMethod.POST)
    public String recordLogBookEntry(Model model, @ModelAttribute("entry") LogBook logBookEntry, BindingResult bindingResult,
                                     @RequestParam(value="subGroups", required=false) boolean subGroups,
                                     @RequestParam(value="assignToUser", required=false) boolean assignToUser,
                                     HttpServletRequest request, RedirectAttributes redirectAttributes) {

        log.info("The confirmed logBookEntry that we are recording is ... " + logBookEntry);
        log.info("Whether or not to replicate ... subGroups ..." + subGroups);

        User user = getUserProfile();

        // todo: implement this using new design
/*
        String assignedToUserUid = logBookEntry.getAssignedToUser() == null? null: logBookEntry.getAssignedToUser().getUid();
        if (!assignToUser || subGroups) {
            assignedToUserUid = null; // todo: in future version allow assignment per subgroup
        }

        logBookBroker.create(user.getUid(), logBookEntry.getGroup().getUid(), logBookEntry.getMessage(), logBookEntry.getActionByDate(), logBookEntry.getReminderMinutes(), assignedToUserUid, subGroups);
*/

        addMessage(redirectAttributes, MessageType.SUCCESS, "log.creation.success", request);
        redirectAttributes.addAttribute("logBookId", logBookEntry.getId()); // todo: rather to logbook description

        return "redirect:/log/details";
    }

    /**
     * SECTION: Views and methods for examining a group's logbook
     * The standard view just looks at the entry as applied to the group ... There's a click through to check sub-group ones
     */
    @RequestMapping(value = "/log/view")
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

    @RequestMapping(value = "/log/details")
    public String viewLogBookDetails(Model model, @RequestParam(value="logBookId", required=true) Long logBookId) {

        // todo: be able to view "children" of the log book once design changed to allow it (replicate by logbook rather than group)

        log.info("Finding details about logbook entry with Id ..." + logBookId);

        LogBook logBookEntry = logBookService.load(logBookId);

        log.info("Retrieved logBook entry with these details ... " + logBookEntry);

        model.addAttribute("entry", logBookEntry);
        model.addAttribute("group", logBookEntry.getGroup());
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
            List<Group> relevantSubGroups = groupManagementService.getListGroupsFromLogbooks(replicatedEntries);
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

    @RequestMapping("/log/complete")
    public String completeLogBookForm(Model model, @RequestParam(value="logBookId", required=true) Long logBookId) {

        LogBook logBookEntry = logBookService.load(logBookId);
        // todo: implement this using new design
/*
        boolean assignedEntry = logBookEntry.getAssignedToUser() != null;
        model.addAttribute("hasAssignedUser", assignedEntry);
        if (assignedEntry)
            model.addAttribute("assignedUser", logBookEntry.getAssignedToUser());
*/
        model.addAttribute("entry", logBookEntry);
        model.addAttribute("groupMembers", groupManagementService.getUsersInGroupNotSubGroups(logBookEntry.getGroup().getId()));

        return "log/complete";
    }

    // todo: check that this user has permission to mark the logbook entry as completed
    @RequestMapping(value = "/log/complete-do", method = RequestMethod.POST)
    public String markLogBookEntryComplete(Model model, @RequestParam(value="logBookId", required=true) Long logBookId,
                                           @RequestParam(value="completedByAssigned", required=false) boolean completedByAssigned,
                                           @RequestParam(value="designateCompletingUser", required=false) boolean designateCompletor,
                                           @RequestParam(value="specifyCompletedDate", required=false) boolean setCompletedDate,
                                           @RequestParam(value="completingUserId", required=false) Long completedByUserId,
                                           @RequestParam(value="completedOnDate", required=false) String completedOnDate,
                                           HttpServletRequest request) {

        // todo: refactor this quite a bit
        log.info("Marking logbook entry as completed ... ");

        Timestamp completedDate = (setCompletedDate) ? Timestamp.valueOf(LocalDateTime.parse(completedOnDate, pickerParser)) : null;

        LogBook logBook = logBookService.load(logBookId);
        User completedByUser = userManagementService.loadUser(completedByUserId);

        if (completedByAssigned || !designateCompletor) {
            log.info("No user assigned, so either setting as complete today or specifying a completion date");
            if (setCompletedDate) {
                logBookBroker.complete(logBook.getUid(), completedDate, null);
            } else {
                logBookBroker.complete(logBook.getUid(), null, null);
            }
        } else {
            log.info("User assigned, so marking it accordingly");
            if (setCompletedDate) {
                logBookBroker.complete(logBook.getUid(), completedDate, completedByUser.getUid());
            } else {
                logBookBroker.complete(logBook.getUid(), null, completedByUser.getUid());
            }
        }

        addMessage(model, MessageType.SUCCESS, "log.completed.done", request);
        return viewGroupLogBook(model, logBook.getGroup().getUid());
    }

    // todo : more permissions than just the below!
    @RequestMapping("/log/modify")
    public String modifyLogBookEntry(Model model, @RequestParam(value="logBookId", required = true) Long logBookId) {
        LogBook logBook = logBookService.load(logBookId);
        Group group = logBook.getGroup();
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
    @RequestMapping(value = "/log/modify", method = RequestMethod.POST)
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
        return viewLogBookDetails(model, savedLogBook.getId());
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
