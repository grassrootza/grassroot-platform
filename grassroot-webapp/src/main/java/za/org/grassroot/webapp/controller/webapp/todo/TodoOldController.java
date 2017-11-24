package za.org.grassroot.webapp.controller.webapp.todo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.MemberPicker;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/01/02.
 */
@Controller
@RequestMapping("/todo/")
public class TodoOldController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(TodoOldController.class);

    @Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
    private double COMPLETION_PERCENTAGE_BOUNDARY;

    private TodoBroker todoBroker;

    @Autowired
    public TodoOldController(GroupBroker groupBroker, TodoBroker todoBroker) {
        this.todoBroker = todoBroker;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(2048);
    }

    /**
     * SECTION: Views and methods for creating action/to-do entries
     */

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

        todoBroker.removeUsers(getUserProfile().getUid(), todoUid, removedUids);
        todoBroker.addAssignments(getUserProfile().getUid(), todoUid, addedUids);

        addMessage(attributes, MessageType.SUCCESS, "todo.assigned.changed", request);
        return "redirect:/todo/view";
    }

    @RequestMapping(value = "complete", method = RequestMethod.GET)
    public String confirmTodoComplete(@RequestParam String todoUid, @RequestParam(required = false) String source,
                                      RedirectAttributes attributes, HttpServletRequest request) {

        // todo : or, is this for creator?
        Todo todo = todoBroker.load(todoUid);
        todoBroker.recordValidation(getUserProfile().getUid(), todo.getUid(), null, null);

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