package za.org.grassroot.webapp.controller.webapp.todo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.task.TodoHelper;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;

@Controller @Slf4j
@RequestMapping("/todo/")
public class TodoWebCreateController extends BaseController {

    private final TodoBroker todoBroker;
    private final GroupBroker groupBroker;
    private final PermissionBroker permissionBroker;

    @Autowired
    public TodoWebCreateController(TodoBroker todoBroker, GroupBroker groupBroker, PermissionBroker permissionBroker) {
        this.todoBroker = todoBroker;
        this.groupBroker = groupBroker;
        this.permissionBroker = permissionBroker;
    }

    @RequestMapping(value = "create", method = RequestMethod.GET)
    public String createTodoPrompt(Model model, @RequestParam(required = false) String parentUid) {
        model.addAttribute("parentSpecified", parentUid != null);
        if (parentUid == null) {
            List<Group> groups = permissionBroker.getActiveGroupsSorted(getUserProfile(), Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
            Map<String, String> groupOptions = new LinkedHashMap<>();
            groups.forEach(g -> groupOptions.put(g.getUid(), g.getName())); // to preserve order
            model.addAttribute("possibleGroups", groupOptions);
            model.addAttribute("parentSpecified", false); // should be redundant but Thymeleaf being very annoying
        } else {
            model.addAttribute("parent", groupBroker.load(parentUid));
            model.addAttribute("parentSpecified", true);
        }

        List<TodoType> availableTypes = Arrays.asList(TodoType.ACTION_REQUIRED,
                TodoType.INFORMATION_REQUIRED,
                TodoType.VOLUNTEERS_NEEDED,
                TodoType.VALIDATION_REQUIRED);

        model.addAttribute("availableTypes", availableTypes);

        return "todo/create";
    }

    // todo : align & standardize nomenclature
    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createTodoDone(@RequestParam String parentUid,
                                 @RequestParam TodoType type,
                                 @RequestParam String explanation,
                                 @RequestParam LocalDateTime deadline,
                                 @RequestParam(required = false) String responseTag,
                                 @RequestParam(required = false) Set<String> assignedMemberUids,
                                 @RequestParam(required = false) Set<String> witnessingMemberUids,
                                 RedirectAttributes attributes, HttpServletRequest request) {
        TodoHelper helper = TodoHelper.builder()
                .parentType(JpaEntityType.GROUP)
                .parentUid(parentUid)
                .todoType(type)
                .subject(explanation)
                .dueDateTime(DateTimeUtil.convertToSystemTime(deadline, DateTimeUtil.getSAST()))
                .userUid(getUserProfile().getUid()).build();

        // todo : validation
        if (TodoType.INFORMATION_REQUIRED.equals(type)) {
            helper.setResponseTag(responseTag);
        }

        if (TodoType.VALIDATION_REQUIRED.equals(type)) {
            if (assignedMemberUids != null && !assignedMemberUids.isEmpty()) {
                helper.setAssignedMemberUids(assignedMemberUids);
            }
            if (witnessingMemberUids != null && !witnessingMemberUids.isEmpty()) {
                helper.setConfirmingMemberUids(witnessingMemberUids);
            }
        }

        String todoUid = todoBroker.create(helper);

        attributes.addAttribute("todoUid", todoUid);
        addMessage(attributes, MessageType.SUCCESS, "todo.created", request);
        return "redirect:/todo/view";
    }

}
