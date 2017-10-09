package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.integration.storage.ImageType;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.TaskImageBroker;
import za.org.grassroot.webapp.controller.BaseController;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by luke on 2016/09/22.
 */
@Controller
@RequestMapping("/task/")
public class TaskController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);

    private final TaskBroker taskBroker;
    private final TaskImageBroker taskImageBroker;
    private final StorageBroker storageBroker;

    @Autowired
    public TaskController(TaskBroker taskBroker, TaskImageBroker taskImageBroker,
                          StorageBroker storageBroker) {
        this.taskBroker = taskBroker;
        this.taskImageBroker = taskImageBroker;
        this.storageBroker = storageBroker;
    }

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
                return "redirect:/todo/view";
            default:
                logger.error("Error! Task with no type passed to view router");
                return "redirect:/home";
        }
    }

    @RequestMapping("gallery")
    public String taskImageController(@RequestParam String taskUid, @RequestParam TaskType taskType,
                                      Model model) {
        model.addAttribute("taskUid", taskUid);
        Map<String, String> keysAndSource = new LinkedHashMap<>();
        // using for each instead of streams, because of keeping order
        taskImageBroker
                .fetchImagesForTask(getUserProfile().getUid(), taskUid, taskType)
                .forEach(ir -> keysAndSource.put(ir.getActionLogUid(),
                        encodedImage(storageBroker.fetchThumbnail(ir.getActionLogUid(), ImageType.LARGE_THUMBNAIL))));
        model.addAttribute("images", keysAndSource);
        return "media/image_gallery";
    }

    private String encodedImage(byte[] imageByteArray) {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageByteArray);
    }
}
