package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.services.exception.TodoTypeMismatchException;
import za.org.grassroot.services.task.TodoBrokerNew;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;

@Slf4j
@RestController
@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDTodoNewController extends USSDBaseController {

    private final TodoBrokerNew todoBrokerNew;
    private final USSDMessageAssembler messageAssembler;

    private static final String thisPath = "/todo2";
    private static final String fullPath = homePath + "todo2/";

    public USSDTodoNewController(TodoBrokerNew todoBrokerNew, USSDMessageAssembler messageAssembler) {
        this.todoBrokerNew = todoBrokerNew;
        this.messageAssembler = messageAssembler;
    }

    public USSDMenu respondToTodo(User user, EntityForUserResponse entity) {
        Todo todo = (Todo) entity;
        switch (todo.getType()) {
            case INFORMATION_REQUIRED:
                final String infoPrompt = messageAssembler.getMessage("todo2.info.prompt",
                        new String[] { todo.getCreatorAlias(), todo.getMessage() }, user);
                return new USSDMenu(infoPrompt, thisPath + "/respond/info?todoUid=" + todo.getUid());
            case VOLUNTEERS_NEEDED:
                final String volunteerPrompt = messageAssembler.getMessage("todo2.volunteer.prompt",
                        new String[] { todo.getCreatorAlias(), todo.getMessage() }, user);
                return new USSDMenu(volunteerPrompt, optionsYesNo(user, thisPath + "/respond/volunteer?todoUid=" + todo.getUid()));
            case VALIDATION_REQUIRED:
                final String confirmationPrompt = messageAssembler.getMessage("todo2.validate.prompt",
                        new String[] { todo.getCreatorAlias(), todo.getMessage() }, user);
                USSDMenu menu = new USSDMenu(confirmationPrompt);
                menu.addMenuOptions(optionsYesNo(user, thisPath + "/respond/validate?todoUid=" + todo.getUid()));
                menu.addMenuOption(thisPath + "/respond/validate?todoUid=" + todo.getUid() + " &" + yesOrNoParam + "=unsure",
                        getMessage("todo2.validate.option.unsure", user));
                return menu;
            default:
                throw new TodoTypeMismatchException();
        }
    }

    @RequestMapping(fullPath + "/respond/volunteer")
    public Request volunteerResponse(@RequestParam(value = phoneNumber) String msisdn,
                                     @RequestParam String todoUid,
                                     @RequestParam(value = yesOrNoParam) String userResponse) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        todoBrokerNew.recordResponse(user.getUid(), todoUid, userResponse);
        // todo : if user responds yes, allow them to share? in case yes, leaving a little duplication in here
        String promptMessage = "yes".equalsIgnoreCase(userResponse) ?
                getMessage("todo2.volunteer.yes.prompt", user) :
                getMessage("todo2.volunteer.no.prompt", user);
        return menuBuilder(welcomeMenu(promptMessage, user));
    }

    @RequestMapping(fullPath + "/respond/info")
    public Request confirmInfoResponse(@RequestParam(value = phoneNumber) String msisdn,
                                       @RequestParam String todoUid,
                                       @RequestParam(value = userInputParam) String userResponse,
                                       @RequestParam(required = false) String priorInput) throws URISyntaxException {
        final String userInput = StringUtils.isEmpty(priorInput) ? userResponse : priorInput;
        User user = userManager.findByInputNumber(msisdn, saveUrl("/respond/info", todoUid, userInput));
        USSDMenu menu = new USSDMenu(getMessage(USSDSection.TODO2, "info", promptKey + ".confirm", userInput, user));
        menu.addMenuOption(fullPath + "/respond/info/confirmed?todoUid=" + todoUid + "&response=" + userInput,
                getMessage("options.yes", user));
        menu.addMenuOption(fullPath + "/respond/info/revise", getMessage("todo2.info.response.change", user));
        return menuBuilder(menu);
    }

    @RequestMapping(fullPath + "/respond/info/confirmed")
    public Request recordInfoResponse(@RequestParam(value = phoneNumber) String msisdn,
                                      @RequestParam String todoUid,
                                      @RequestParam String response) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, null);
        todoBrokerNew.recordResponse(user.getUid(), todoUid, response);
        return menuBuilder(welcomeMenu(getMessage("todo2.info.prompt.done", user), user));
    }

    @RequestMapping(fullPath + "/respond/info/revise")
    public Request reviseInfoRequest(@RequestParam(value = phoneNumber) String msisdn,
                                     @RequestParam String todoUid,
                                     @RequestParam(value = userInputParam) String userResponse,
                                     @RequestParam(required = false) String priorInput) throws URISyntaxException {
        final String userInput = StringUtils.isEmpty(priorInput) ? userResponse : priorInput;
        User user = userManager.findByInputNumber(msisdn, saveUrl("/respond/info/revise", todoUid, userInput));
        // note: probably want to come back & test whether to re-include original request
        final String prompt = messageAssembler.getMessage("todo2.info.revise.prompt", new String[] { userInput },  user);
        return menuBuilder(new USSDMenu(prompt, thisPath + "/respond/info?todoUid=" + todoUid));
    }

    // note : ask when it was done?
    @RequestMapping(fullPath + "/respond/validate")
    public Request validateTodoCompletion(@RequestParam(value = phoneNumber) String msisdn,
                                          @RequestParam String todoUid,
                                          @RequestParam(value = yesOrNoParam) String userResponse) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        if (!"unsure".equalsIgnoreCase(userResponse)) {
            todoBrokerNew.recordResponse(user.getUid(), todoUid, userResponse);
        }
        return menuBuilder(welcomeMenu(getMessage("todo2.validate." + userResponse + ".prompt", user), user));
    }


    private String saveUrl(String menu, String todoUid, String userInput) {
        return thisPath + menu + "?todoUid=" + todoUid + "&priorInput=" + USSDUrlUtil.encodeParameter(userInput);
    }

}
