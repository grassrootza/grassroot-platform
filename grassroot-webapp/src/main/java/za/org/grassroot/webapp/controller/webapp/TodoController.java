package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.task.enums.TodoStatus;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.MemberPicker;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static za.org.grassroot.core.domain.Permission.*;

/**
 * Created by luke on 2016/01/02.
 */
@Controller
@RequestMapping("/todo/")
public class TodoController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(TodoController.class);

    @Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
    private double COMPLETION_PERCENTAGE_BOUNDARY;

    private GroupBroker groupBroker;
    private TodoBroker todoBroker;
    private EventBroker eventBroker;

    @Autowired
    public TodoController(GroupBroker groupBroker, TodoBroker todoBroker, EventBroker eventBroker) {
        this.groupBroker = groupBroker;
        this.todoBroker = todoBroker;
        this.eventBroker = eventBroker;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(2048);
    }

    /**
     * SECTION: Views and methods for creating action/to-do entries
     */

    /**
     * SECTION: Views and methods for examining a group's actions and todos
     * The standard view just looks at the entry as applied to the group ... There's a click through to check sub-group ones
     */
    @RequestMapping(value = "list")
    public String viewGroupActions(Model model, @RequestParam String groupUid) {

        User user = userManagementService.load(getUserProfile().getUid());
        Group group = groupBroker.load(groupUid);

        permissionBroker.validateGroupPermission(user, group, null);

        model.addAttribute("user", user);
        model.addAttribute("group", group);

        model.addAttribute("canCallMeeting", permissionBroker.isGroupPermissionAvailable(user, group, GROUP_PERMISSION_CREATE_GROUP_MEETING));
        model.addAttribute("canCallVote", permissionBroker.isGroupPermissionAvailable(user, group, GROUP_PERMISSION_CREATE_GROUP_VOTE));
        model.addAttribute("canRecordAction", permissionBroker.isGroupPermissionAvailable(user, group, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY));

        model.addAttribute("incompleteEntries", todoBroker.fetchTodosForGroupByStatus(group.getUid(), false, TodoStatus.INCOMPLETE));
        model.addAttribute("completedEntries", todoBroker.fetchTodosForGroupByStatus(group.getUid(), false, TodoStatus.COMPLETE));

        return "todo/list";
    }

    @RequestMapping(value = "view")
    public String viewTodoDetails(Model model, @RequestParam String todoUid, @RequestParam(required = false) SourceMarker source) {

        Todo todoEntry = todoBroker.load(todoUid);
        User user = userManagementService.load(getUserProfile().getUid());

        model.addAttribute("todo", todoEntry);
        model.addAttribute("isAssigned", todoEntry.getAssignedMembers().contains(user));
        model.addAttribute("parent", todoEntry.getParent());
        model.addAttribute("isComplete", todoEntry.isCompleted(COMPLETION_PERCENTAGE_BOUNDARY));
        model.addAttribute("hasReminders", todoEntry.isReminderActive());
        model.addAttribute("canModify", todoEntry.getCreatedByUser().equals(user) ||
                permissionBroker.isGroupPermissionAvailable(user, todoEntry.getAncestorGroup(), GROUP_PERMISSION_UPDATE_GROUP_DETAILS));
        model.addAttribute("fromGroup", SourceMarker.GROUP.equals(source));
        model.addAttribute("memberPicker", MemberPicker.taskAssigned(todoEntry));

        return "todo/view";
    }

    @RequestMapping(value = "cancel", method = RequestMethod.POST)
    public String cancelTodo(@RequestParam String todoUid, @RequestParam String parentUid,
                             RedirectAttributes redirectAttributes, HttpServletRequest request) {
        // service layer will test for permission etc and throw errors
        todoBroker.cancel(getUserProfile().getUid(), todoUid);
        addMessage(redirectAttributes, MessageType.INFO, "todo.cancelled.done", request);
        // for now, assuming it's a group
        redirectAttributes.addAttribute("groupUid", parentUid);
        return "redirect:/group/view";
    }

    @PostMapping("description")
    public String changeDescription(@RequestParam String todoUid, @RequestParam String description,
                                    RedirectAttributes attributes, HttpServletRequest request) {
        todoBroker.updateDescription(getUserProfile().getUid(), todoUid, description);
        addMessage(attributes, MessageType.SUCCESS, "todo.description.updated", request);
        attributes.addAttribute("todoUid", todoUid);
        return "redirect:/todo/view";
    }

    @PostMapping("changeduedate")
    public String changeDueDate(@RequestParam String todoUid, @RequestParam LocalDateTime actionByDate,
                                RedirectAttributes attributes, HttpServletRequest request) {
        todoBroker.updateActionByDate(getUserProfile().getUid(), todoUid, actionByDate);
        addMessage(attributes, MessageType.SUCCESS, "todo.deadline.updated", request);
        attributes.addAttribute("todoUid", todoUid);
        return "redirect:/todo/view";
    }

    @PostMapping("assignment")
    public String changeAssignment(@RequestParam String todoUid, @ModelAttribute("memberPicker") MemberPicker memberPicker,
                                   RedirectAttributes attributes, HttpServletRequest request) {
        Todo todo = todoBroker.load(todoUid);
        attributes.addAttribute("todoUid", todoUid);

        Set<String> assignedUids = memberPicker.getSelectedUids();
        if (assignedUids.isEmpty()) {
            addMessage(attributes, MessageType.ERROR, "todo.assigned.error.empty", request);
            return "redirect:/todo/view";
        }

        Set<String> originalUids = todo.isAllGroupMembersAssigned() ?
                todo.getAncestorGroup().getMembers().stream().map(User::getUid).collect(Collectors.toSet()) :
                todo.getAssignedMembers().stream().map(User::getUid).collect(Collectors.toSet());

        Set<String> addedUids = assignedUids.stream()
                .filter(s -> !originalUids.contains(s))
                .collect(Collectors.toSet());

        Set<String> removedUids = originalUids.stream()
                .filter(s -> !assignedUids.contains(s))
                .collect(Collectors.toSet());

        todoBroker.removeAssignedMembers(getUserProfile().getUid(), todoUid, removedUids);
        todoBroker.assignMembers(getUserProfile().getUid(), todoUid, addedUids);

        addMessage(attributes, MessageType.SUCCESS, "todo.assigned.changed", request);
        return "redirect:/todo/view";
    }

    @RequestMapping(value = "complete", method = RequestMethod.GET)
    public String confirmTodoComplete(@RequestParam String todoUid, @RequestParam(required = false) String source,
                                      RedirectAttributes attributes, HttpServletRequest request) {

        Todo todo = todoBroker.load(todoUid);
        todoBroker.confirmCompletion(getUserProfile().getUid(), todo.getUid(), TodoCompletionConfirmType.COMPLETED, LocalDateTime.now());

        addMessage(attributes, MessageType.SUCCESS, "todo.completed.done", request);
        if (StringUtils.isEmpty(source) || "group".equalsIgnoreCase(source)) {
            attributes.addAttribute("groupUid", todo.getAncestorGroup().getUid());
            return "redirect:/group/view";
        } else if ("todolist".equalsIgnoreCase(source)) {
            attributes.addAttribute("groupUid", todo.getAncestorGroup().getUid());
            return "redirect:/todo/list";
        } else if ("todoview".equalsIgnoreCase(source)) {
            attributes.addAttribute("todoUid", todo.getUid());
            return "redirect:/todo/view";
        } else {
            return "redirect:/home";
        }
    }

}