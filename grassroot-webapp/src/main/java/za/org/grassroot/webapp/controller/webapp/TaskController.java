package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.TaskBroker;
import za.org.grassroot.webapp.controller.BaseController;

/**
 * Created by luke on 2016/09/22.
 */
@Controller
@RequestMapping("/task/")
public class TaskController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);

    @Autowired
    private TaskBroker taskBroker;

    @RequestMapping("view")
    public String viewTask(@RequestParam String taskUid, RedirectAttributes attributes) {
        TaskDTO taskDTO = taskBroker.load(getUserProfile().getUid(), taskUid);
        TaskType taskType = TaskType.valueOf(taskDTO.getType());
        switch (taskType) {
            case MEETING:
                attributes.addAttribute("eventUid", taskUid);
                return "redirect:/meeting/view";
            case VOTE:
                attributes.addAttribute("eventUid", taskUid);
                return "redirect:/vote/view";
            case TODO:
                attributes.addAttribute("todoUid", taskUid);
                return "redirect:/todo/details";
            default:
                logger.error("Error! Task with no type passed to view router");
                return "redirect:/home";
        }
    }
}
