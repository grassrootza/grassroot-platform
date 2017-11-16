package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.services.exception.TodoTypeMismatchException;
import za.org.grassroot.services.task.TodoBrokerNew;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

@Slf4j
@RestController
@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDTodoNewController extends USSDBaseController {

    private final TodoBrokerNew todoBrokerNew;

    private static final String path = homePath + "todo2/";

    public USSDTodoNewController(TodoBrokerNew todoBrokerNew) {
        this.todoBrokerNew = todoBrokerNew;
    }

    protected static String todoResponsePrompt(Todo todo) {
        switch (todo.getType()) {
            case VOLUNTEERS_NEEDED:
                return "todo2.volunteer.prompt";
            case INFORMATION_REQUIRED:
                return "todo2.info.prompt";
            default:
                throw new TodoTypeMismatchException();
        }
    }

    @RequestMapping(path + "/respond/volunteer")
    public Request volunteerResponse(@RequestParam(value = phoneNumber) String msisdn,
                                     @RequestParam String todoUid,
                                     @RequestParam String userResponse) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        todoBrokerNew.recordResponse(user.getUid(), todoUid, userResponse);
        USSDMenu menu = new USSDMenu();
        if ("yes".equalsIgnoreCase(userResponse)) {
            // todo : if user responds yes, allow them to share? in case yes, leaving a little duplication in here
            menu.setPromptMessage(getMessage("todo2.volunteer.yes.prompt", user));
            menu.addMenuOptions(optionsHomeExit(user, false));
        } else {
            menu.setPromptMessage(getMessage("todo2.volunteer.no.prompt", user));
            menu.addMenuOptions(optionsHomeExit(user, false));
        }
        return menuBuilder(menu);
    }

    // todo : just move to opening screen
    @RequestMapping(path + "/respond/info/open")
    public Request infoResponsePrompt(@RequestParam(value = phoneNumber) String msisdn,
                                      @RequestParam String todoUid,
                                      @RequestParam boolean interrupted) throws URISyntaxException {
        // todo : cache it
        User user = userManager.findByInputNumber(msisdn);
        Todo todo = todoBrokerNew.load(todoUid);

        return menuBuilder(new USSDMenu(
                getMessage(USSDSection.TODO2, "info", promptKey, todo.getMessage(), user),
                path + "/respond/info/confirm"));
    }

    @RequestMapping(path  + "/respond/info")
    public Request confirmInfoResponse(@RequestParam(value = phoneNumber) String msisdn,
                                       @RequestParam String todoUid,
                                       @RequestParam(value = userInputParam) String userResponse,
                                       @RequestParam boolean interrupted) throws URISyntaxException {
        // todo : cache for interruption
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu = new USSDMenu(
                getMessage(USSDSection.TODO2, "info", promptKey + ".confirm", userResponse, user));
        menu.addMenuOption(path + "/respond/info/confirmed", getMessage("options.yes", user));
        menu.addMenuOption(path + "/respond/info/revise", getMessage("todo2.info.response.change", user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + "/respond/confirmed")
    public Request recordInfoResponse(@RequestParam(value = phoneNumber) String msisdn,
                                      @RequestParam String todoUid,
                                      @RequestParam String userResponse) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        todoBrokerNew.recordResponse(user.getUid(), todoUid, userResponse);
        USSDMenu menu = new USSDMenu(getMessage("todo2.info.prompt.done", user));
        menu.addMenuOptions(optionsHomeExit(user, false));
        return menuBuilder(menu);
    }




}
