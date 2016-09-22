package za.org.grassroot.webapp.controller.webapp;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.TodoBroker;
import za.org.grassroot.services.enums.TodoStatus;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.MemberPicker;
import za.org.grassroot.webapp.model.web.TodoWrapper;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by luke on 2016/01/02.
 */
@Controller
@RequestMapping("/todo/")
public class TodoController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(TodoController.class);

    @Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
    private double COMPLETION_PERCENTAGE_BOUNDARY;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private TodoBroker todoBroker;

    @Autowired
    private EventBroker eventBroker;

    /**
     * SECTION: Views and methods for creating action/to-do entries
     */

    @RequestMapping("create")
    public String createTodo(Model model, @RequestParam(value="parentUid", required=false) String parentUid,
                             @RequestParam(value="parentType", required=false) JpaEntityType passedParentType) {

        TodoWrapper wrapper;

        if (!StringUtils.isEmpty(parentUid)) {
            JpaEntityType parentType = passedParentType == null ? JpaEntityType.GROUP : passedParentType;
            boolean isParentGroup = JpaEntityType.GROUP.equals(parentType);
            TodoContainer parent = isParentGroup ? groupBroker.load(parentUid) : eventBroker.load(parentUid);

            wrapper = new TodoWrapper(parentType, parentUid, parent.getName());
            model.addAttribute("parent", parent);
            model.addAttribute("parentSpecified", true);

            wrapper.setMemberPicker(MemberPicker.create(parent, parentType, false));
            wrapper.setAssignmentType(JpaEntityType.GROUP.equals(parentType) ? "group" : "non-group");
            wrapper.setReminderType(parent.getReminderType());
            wrapper.setReminderMinutes(parent.getTodoReminderMinutes() == null ? AbstractTodoEntity.DEFAULT_REMINDER_MINUTES
                    : parent.getTodoReminderMinutes());
            model.addAttribute("actionTodo", wrapper);
        } else {
            // reload user entity in case things have changed during session (else bug w/ list of possible groups)
            User userFromDb = userManagementService.load(getUserProfile().getUid());
            model.addAttribute("parentSpecified", false);
            model.addAttribute("userUid", userFromDb.getUid());
            model.addAttribute("possibleGroups", permissionBroker.getActiveGroupsSorted(userFromDb, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY));

            wrapper = new TodoWrapper(JpaEntityType.GROUP);

            wrapper.setAssignmentType("group");
            wrapper.setReminderType(EventReminderType.GROUP_CONFIGURED);
            wrapper.setReminderMinutes(AbstractTodoEntity.DEFAULT_REMINDER_MINUTES);

        }

        model.addAttribute("actionTodo", wrapper);
        return "todo/create";

    }

    // todo : abstract & consolidate these two

    @RequestMapping(value = "record", method = RequestMethod.POST)
    public String recordTodo(@ModelAttribute("entry") TodoWrapper todoEntry,
                             HttpServletRequest request, RedirectAttributes redirectAttributes) {

        log.info("TodoWrapper received, looks like: {}", todoEntry.toString());

        if (todoEntry.getReminderType().equals(EventReminderType.GROUP_CONFIGURED)) {
            int convertedMinutes = -(groupBroker.load(todoEntry.getParentUid()).getReminderMinutes());
            todoEntry.setReminderMinutes(convertedMinutes);
        }

        Set<String> assignedUids;
        if ("members".equals(todoEntry.getAssignmentType())) {
            MemberPicker listOfMembers = todoEntry.getMemberPicker();
            log.info("The memberUids are : ..." + Joiner.on(", ").join(listOfMembers.getSelectedUids()));
            assignedUids = listOfMembers.getSelectedUids();
        } else {
            assignedUids = Collections.emptySet();
        }

        Long startTime = System.currentTimeMillis();
        todoBroker.create(getUserProfile().getUid(), todoEntry.getParentEntityType(), todoEntry.getParentUid(),
                todoEntry.getMessage(), todoEntry.getActionByDate(), todoEntry.getReminderMinutes(),
                todoEntry.isReplicateToSubGroups(), assignedUids);

        log.info("Time to create, store, todos: {} msecs", System.currentTimeMillis() - startTime);

        addMessage(redirectAttributes, MessageType.SUCCESS, "todo.creation.success", request);
        // redirectAttributes.addAttribute("todoUid", created.getUid());

        return "redirect:/home";
    }

    @RequestMapping(value = "record/meeting", method = RequestMethod.POST)
    public String recordEntryWithMeetingParent(Model model, @ModelAttribute("actionTodo") TodoWrapper todo,
                                               HttpServletRequest request, RedirectAttributes attributes) {

        Todo created = todoBroker.create(getUserProfile().getUid(), todo.getParentEntityType(),
                todo.getParentUid(), todo.getMessage(), todo.getActionByDate(),
                todo.getReminderMinutes(), false, Collections.emptySet());

        addMessage(attributes, MessageType.SUCCESS, "todo.creation.success", request);
        attributes.addAttribute("todoUid", created.getUid());

        return "redirect:/todo/details";

    }

    /**
     * SECTION: Views and methods for examining a group's actions and todos
     * The standard view just looks at the entry as applied to the group ... There's a click through to check sub-group ones
     */
    @RequestMapping(value = "view")
    public String viewGroupActions(Model model, @RequestParam String groupUid) {

        log.info("Okay, pulling up action records ... primarily for the currently assigned group");

        Group group = groupBroker.load(groupUid);
        model.addAttribute("group", group);
        model.addAttribute("incompleteEntries", todoBroker.fetchTodosForGroupByStatus(group.getUid(), false, TodoStatus.INCOMPLETE));

        List<Todo> completedEntries = todoBroker.fetchTodosForGroupByStatus(group.getUid(), false, TodoStatus.COMPLETE);
        model.addAttribute("completedEntries", completedEntries);
        log.info("Got back this many complete entries ... " + completedEntries.size());

        return "todo/view";
    }

    // major todo : restore replication to subgroups when that feature is properly redesigned
    @RequestMapping(value = "details")
    public String viewTodoDetails(Model model, @RequestParam String todoUid) {

        Todo todoEntry = todoBroker.load(todoUid);
        User user = userManagementService.load(getUserProfile().getUid());

        log.info("Retrieved todo entry with these details ... " + todoEntry);

        model.addAttribute("entry", todoEntry);
        model.addAttribute("parent", todoEntry.getParent());
        model.addAttribute("creatingUser", todoEntry.getCreatedByUser());
        model.addAttribute("isComplete", todoEntry.isCompleted(COMPLETION_PERCENTAGE_BOUNDARY));
        model.addAttribute("canModify", todoEntry.getCreatedByUser().equals(user) ||
                permissionBroker.isGroupPermissionAvailable(user, todoEntry.getAncestorGroup(), Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS));

        return "todo/details";
    }

    @RequestMapping(value = "complete", method = RequestMethod.GET)
    public String confirmTodoComplete(@RequestParam String todoUid, RedirectAttributes attributes, HttpServletRequest request) {

        log.info("Marking action as completed ... ");

        Todo todo = todoBroker.load(todoUid);
        todoBroker.confirmCompletion(getUserProfile().getUid(), todo.getUid(), TodoCompletionConfirmType.COMPLETED, LocalDateTime.now());

        String priorUrl = request.getHeader(HttpHeaders.REFERER);

        addMessage(attributes, MessageType.SUCCESS, "todo.completed.done", request);
        if (priorUrl.contains("group")) {
            attributes.addAttribute("groupUid", todo.getAncestorGroup().getUid());
            return "redirect:/group/view";
        } else if (priorUrl.contains("todo")) {
            attributes.addAttribute("todoUid", todoUid);
            return "redirect:/todo/details";
        } else {
            return "redirect:/home";
        }
    }

    // todo : add back assignment
    @RequestMapping("modify")
    public String modifyTodo(Model model, @RequestParam String todoUid) {

        User user = userManagementService.load(getUserProfile().getUid());
        Todo todo = todoBroker.load(todoUid);
        Group group = todo.getAncestorGroup();

        if (!todo.getCreatedByUser().equals(user)) {
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }

        model.addAttribute("todo", todo);
        model.addAttribute("groupMembers", group.getMembers());

        return "todo/modify";
    }

    @RequestMapping(value = "cancel", method = RequestMethod.POST)
    public String cancelTodo(@RequestParam String todoUid, @RequestParam String parentUid, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        // service layer will test for permission etc and throw errors
        todoBroker.cancel(getUserProfile().getUid(), todoUid);
        addMessage(redirectAttributes, MessageType.INFO, "todo.cancelled.done", request);
        // for now, assuming it's a group
        redirectAttributes.addAttribute("groupUid", parentUid);
        return "redirect:/group/view";
    }

    // todo: cancellation & assignment
    @RequestMapping(value = "modify", method = RequestMethod.POST)
    public String changeTodoEntry(Model model, @ModelAttribute("todo") Todo todo, HttpServletRequest request) {

        // may consider doing some of this in services layer, but main point is can't just use to-do entity passed
        // back from form as thymeleaf whacks all the attributes we don't explicitly code into hidden inputs

        Todo savedTodo = todoBroker.load(todo.getUid());
        User user = userManagementService.load(getUserProfile().getUid());
        Group group = savedTodo.getAncestorGroup();

        if (!todo.getCreatedByUser().equals(user)) {
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }

        if (!todo.getMessage().equals(savedTodo.getMessage()))
            savedTodo.setMessage(todo.getMessage());

        if (!todo.getActionByDate().equals(savedTodo.getActionByDate()))
            savedTodo.setActionByDate(todo.getActionByDate());

        if (todo.getReminderMinutes() != savedTodo.getReminderMinutes())
            savedTodo.setReminderMinutes(todo.getReminderMinutes());

        savedTodo = todoBroker.update(savedTodo);

        addMessage(model, MessageType.SUCCESS, "todo.modified.done", request);
        return viewTodoDetails(model, savedTodo.getUid());
    }

}