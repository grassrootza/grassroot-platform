package za.org.grassroot.webapp.controller.webapp.todo;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoAssignment;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.MemberPicker;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller @Slf4j
@RequestMapping("/todo/")
public class TodoWebViewModifyController extends BaseController {

    private final TodoBroker todoBroker;
    private final MemberDataExportBroker memberData;

    @Autowired
    public TodoWebViewModifyController(TodoBroker todoBroker, MemberDataExportBroker exportBroker) {
        this.todoBroker = todoBroker;
        this.memberData = exportBroker;
    }

    @RequestMapping(value = "view", method = RequestMethod.GET)
    public String viewTodoDetails(Model model, @RequestParam String todoUid) {
        Todo todo = todoBroker.load(todoUid);
        model.addAttribute("todo", todo);

        final String userUid = getUserProfile().getUid();
        if (todoBroker.canUserViewResponses(userUid, todoUid)) {
            model.addAttribute("responses", fetchResponses(getUserProfile(), todo));
            model.addAttribute("userResponse", todoBroker.fetchUserTodoDetails(userUid, todoUid));
        }

        if (todoBroker.canUserModify(userUid, todoUid)) {
            model.addAttribute("canModify", true);
            model.addAttribute("memberPicker", MemberPicker.taskAssigned(todo));
        } else {
            model.addAttribute("canModify", false);
        }

        return "todo/view";
    }

    private List<TodoAssignment> fetchResponses(User user, Todo todo) {
        if (TodoType.INFORMATION_REQUIRED.equals(todo.getType())) {
            return todoBroker.fetchAssignedUserResponses(user.getUid(), todo.getUid(), false, true, false)
                    .stream().sorted(Comparator.comparing(TodoAssignment::isHasResponded, Comparator.reverseOrder()))
                    .collect(Collectors.toList());
        } else if (TodoType.VOLUNTEERS_NEEDED.equals(todo.getType())) {
            return todoBroker.fetchAssignedUserResponses(user.getUid(), todo.getUid(), true, false, false);
        } else if (TodoType.VALIDATION_REQUIRED.equals(todo.getType())) {
            return todoBroker.fetchAssignedUserResponses(user.getUid(), todo.getUid(), false, false, true);
        } else {
            return todoBroker.fetchAssignedUserResponses(user.getUid(), todo.getUid(), false, true, false);
        }
    }

    @RequestMapping(value = "export")
    public void exportResponses(@RequestParam String todoUid, HttpServletResponse response) throws IOException {
        log.info("exporting tood responses .. ");

        String fileName = "todo_responses.xlsx";
        response.setContentType(MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        XSSFWorkbook xls = memberData.exportTodoData(getUserProfile().getUid(), todoUid);
        log.info("got a workbook back, looks like: {}", xls);
        xls.write(response.getOutputStream());
        response.flushBuffer();

    }

    @RequestMapping(value = "cancel", method = RequestMethod.POST)
    public String cancelTodo(@RequestParam String todoUid, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        // service layer will test for permission etc and throw errors
        Todo todo = todoBroker.load(todoUid);
        todoBroker.cancel(getUserProfile().getUid(), todoUid, null);
        addMessage(redirectAttributes, MessageType.INFO, "todo.cancelled.done", request);
        redirectAttributes.addAttribute("groupUid", todo.getAncestorGroup().getUid());
        return "redirect:/group/view";
    }

    @RequestMapping(value = "explanation", method = RequestMethod.POST)
    public String changeExplanation(@RequestParam String todoUid,
                                    @RequestParam String explanation,
                                    RedirectAttributes attributes, HttpServletRequest request) {
        todoBroker.updateSubject(getUserProfile().getUid(), todoUid, explanation);
        addMessage(attributes, MessageType.SUCCESS, "todo.explanation.updated", request);
        attributes.addAttribute("todoUid", todoUid);
        return "redirect:/todo/view";
    }

    @RequestMapping(value = "extend", method = RequestMethod.POST)
    public String changeDueDate(@RequestParam String todoUid,
                                @RequestParam LocalDateTime actionByDate,
                                RedirectAttributes attributes, HttpServletRequest request) {
        todoBroker.extend(getUserProfile().getUid(), todoUid, DateTimeUtil.convertToSystemTime(actionByDate, DateTimeUtil.getSAST()));
        addMessage(attributes, MessageType.SUCCESS, "todo.deadline.updated", request);
        attributes.addAttribute("todoUid", todoUid);
        return "redirect:/todo/view";
    }



}
