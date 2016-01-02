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
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Created by luke on 2016/01/02.
 */
@Controller
public class LogBookController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(LogBookController.class);

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    LogBookService logBookService;

    /**
     * SECTION: Views and methods for creating logbook entries
     */

    @RequestMapping("/log/create")
    public String createLogBook(Model model, @RequestParam(value="groupId", required=false) Long groupId) {

        // Thymeleaf insists on messing everything up if we try to set groupId, or just in general create the entity
        // on the next page instead of here, so we have to do some redundant & silly entity creation
        LogBook logBookToFill = new LogBook();

        if (groupId == null) {
            log.info("No group specified, pass a list and let user choose");
            model.addAttribute("groupSpecified", false);
            // todo: make this use permissions logic so only pass groups for which the user has permission to do this
            model.addAttribute("possibleGroups", groupManagementService.getActiveGroupsPartOf(getUserProfile()));
        } else {
            log.info("User came here from a group view so set group as specified");
            model.addAttribute("groupSpecified", true);
            // todo: another permission check
            model.addAttribute("group", groupManagementService.loadGroup(groupId));
            logBookToFill.setGroupId(groupId);
        }

        model.addAttribute("entry", logBookToFill);
        return "log/create";
    }

    @RequestMapping(value = "/log/confirm", method = RequestMethod.POST)
    public String confirmLogBookEntry(Model model, @ModelAttribute("entry") LogBook logBookEntry, BindingResult bindingResult,
                                      @RequestParam(value="subGroups", required=false) boolean subGroups, HttpServletRequest request) {

        log.info("The potential logBookEntry passed back to us ... " + logBookEntry);
        Group groupSelected = groupManagementService.loadGroup(logBookEntry.getGroupId());
        model.addAttribute("group", groupSelected);
        model.addAttribute("subGroups", subGroups);

        if (subGroups) {
            // todo: use the tree methods to make this more coherent
            // todo: restrict this to paid groups, and add in message numbers / cost estimates
            model.addAttribute("numberSubGroups", groupManagementService.getSubGroups(groupSelected).size());
            model.addAttribute("numberMembers", groupManagementService.getGroupSize(groupSelected, true));
        } else {
            model.addAttribute("groupMembers", groupSelected.getGroupMembers());
        }

        return "log/confirm";

    }

    @RequestMapping(value = "/log/record", method = RequestMethod.POST)
    public String recordLogBookEntry(Model model, @ModelAttribute("entry") LogBook logBookEntry, BindingResult bindingResult,
                                     @RequestParam(value="subGroups", required=false) boolean subGroups,
                                     @RequestParam(value="assignToUser", required=false) boolean assignToUser,
                                     HttpServletRequest request, RedirectAttributes redirectAttributes) {

        log.info("The confirmed logBookEntry that we are recording is ... " + logBookEntry);

        logBookEntry.setCreatedByUserId(getUserProfile().getId());
        if (!assignToUser || subGroups) logBookEntry.setAssignedToUserId(null);

        logBookService.create(logBookEntry, subGroups);
        addMessage(redirectAttributes, MessageType.SUCCESS, "log.creation.success", request);
        redirectAttributes.addAttribute("groupId", logBookEntry.getGroupId()); // todo: rather to logbook description

        return "redirect:/group/view";
    }

    /**
     * SECTION: Views and methods for examining a group's logbook
     * The standard view just looks at the entry as applied to the group ... There's a click through to check sub-group ones
     */
    @RequestMapping(value = "/log/view")
    public String viewGroupLogBook(Model model, @RequestParam(value="groupId", required=true) Long groupId) {

        log.info("Okay, pulling up logbook records ... primarily for the currently assigned group");

        model.addAttribute("group", groupManagementService.loadGroup(groupId));
        model.addAttribute("incompleteEntries", logBookService.getAllLogBookEntriesForGroup(groupId, false));

        List<LogBook> completedEntries = logBookService.getAllLogBookEntriesForGroup(groupId, true);
        model.addAttribute("completedEntries", completedEntries);
        log.info("Got back this many complete entries ... " + completedEntries.size());

        return "log/view";
    }

    @RequestMapping(value = "/log/view/details")
    public String viewLogBookDetails(Model model, @RequestParam(value="logBookId", required=true) Long logBookId) {

        // todo: be able to view "children" of the log book once design changed to allow it (replicate by logbook rather than group)

        log.info("Finding details about logbook entry with Id ..." + logBookId);
        LogBook logBookEntry = logBookService.load(logBookId);
        model.addAttribute("entry", logBookEntry);
        model.addAttribute("group", groupManagementService.loadGroup(logBookEntry.getGroupId()));
        model.addAttribute("creatingUser", userManagementService.loadUser(logBookEntry.getCreatedByUserId()));
        model.addAttribute("isComplete", logBookEntry.isCompleted());

        if(logBookEntry.getAssignedToUserId() != null)
            model.addAttribute("assignedToUser", userManagementService.loadUser(logBookEntry.getAssignedToUserId()));
        if(logBookEntry.isCompleted())
            model.addAttribute("completedByUser", userManagementService.loadUser(logBookEntry.getCompletedByUserId()));

        return "log/details";
    }

    @RequestMapping("/log/complete")
    public String completeLogBookForm(Model model, @RequestParam(value="logBookId", required=true) Long logBookId) {

        LogBook logBookEntry = logBookService.load(logBookId);
        model.addAttribute("entry", logBookEntry);
        model.addAttribute("hasAssignedUser", logBookEntry.getAssignedToUserId() != null);
        model.addAttribute("groupMembers", groupManagementService.getUsersInGroupNotSubGroups(logBookEntry.getGroupId()));
        if (logBookEntry.getAssignedToUserId() != null)
            model.addAttribute("assignedUser", userManagementService.loadUser(logBookEntry.getAssignedToUserId()));

        return "log/complete";
    }

    // todo: check that this user has permission to mark the logbook entry as completed
    @RequestMapping(value = "/log/complete-do", method = RequestMethod.GET)
    public String markLogBookEntryComplete(Model model, @RequestParam(value="logBookId", required=true) Long logBookId,
                                           @RequestParam(value="completedByAssigned", required=false) boolean completedByAssigned,
                                           @RequestParam(value="designateCompletingUser", required=false) boolean designateCompletor,
                                           @RequestParam(value="specifyCompletedDate", required=false) boolean setCompletedDate,
                                           @RequestParam(value="completingUserId", required=false) Long completedByUserId,
                                           @RequestParam(value="completedOnDate", required=false) String completedOnDate,
                                           HttpServletRequest request) {

        log.info("Marking logbook entry as completed ... ");

        LogBook completedEntry;
        Timestamp completedDate = Timestamp.valueOf(LocalDateTime.now());
        if (completedByAssigned || !designateCompletor) {
            completedEntry = (setCompletedDate) ? logBookService.setCompleted(logBookId, completedDate) :
                    logBookService.setCompleted(logBookId);
        } else {
            completedEntry = (setCompletedDate) ? logBookService.setCompletedWithDate(logBookId, completedByUserId, completedDate):
                    logBookService.setCompleted(logBookId, completedByUserId);
        }

        log.info("Okay, marked as completed this logbook entry ... " + completedEntry);

        addMessage(model, MessageType.SUCCESS, "log.completed.done", request);
        return viewGroupLogBook(model, completedEntry.getGroupId());
    }

}
