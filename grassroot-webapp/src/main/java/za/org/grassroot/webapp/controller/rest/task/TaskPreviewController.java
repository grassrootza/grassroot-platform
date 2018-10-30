package za.org.grassroot.webapp.controller.rest.task;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.enums.EventSpecialForm;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.exception.TodoTypeMismatchException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Slf4j @RestController @Grassroot2RestController
@Api("/v2/api/task/preview") @RequestMapping(value = "/v2/api/task/preview/")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class TaskPreviewController extends BaseRestController {

    private static final String SAMPLE_BITLY_LINK = "https://bit.ly/2zr3Jl9"; // just for Grassroot main website

    private final GroupBroker groupBroker;
    private final MessageSource messageSource; // for the menus
    private final MessageAssemblingService messageService; // for the SMSs

    public TaskPreviewController(JwtService jwtService, UserManagementService userManagementService, GroupBroker groupBroker,
                                 @Qualifier("messageSource") MessageSource messageSource, MessageAssemblingService messageService) {
        super(jwtService, userManagementService);
        this.groupBroker = groupBroker;
        this.messageSource = messageSource;
        this.messageService = messageService;
    }

    // only implemented for group parent type for now
    @RequestMapping(value = "/event/{eventType}/{groupUid}", method = RequestMethod.GET)
    public ResponseEntity<TaskPreview> fetchPreviewOfEvent(HttpServletRequest request,
                                                           @PathVariable TaskType eventType,
                                                           @PathVariable String groupUid,
                                                           @RequestParam String subject,
                                                           @RequestParam long dateTimeEpochMillis,
                                                           @RequestParam(required = false) String description,
                                                           @RequestParam(required = false) String mediaFileUid,
                                                           @RequestParam(required = false) String location,
                                                           @RequestParam(required = false) List<String> voteOptions,
                                                           @RequestParam(required = false) EventSpecialForm specialForm) {
        User user = getUserFromRequest(request);
        Group group = groupBroker.load(groupUid);
        Instant startDateTime = Instant.ofEpochMilli(dateTimeEpochMillis);

        Event previewEvent = TaskType.VOTE.equals(eventType) ?
                new Vote(subject, startDateTime, user, group) :
                new Meeting(subject, startDateTime, user, group, location);
        if (!StringUtils.isEmpty(mediaFileUid))
            previewEvent.setImageUrl(SAMPLE_BITLY_LINK);
        if (specialForm != null)
            previewEvent.setSpecialForm(specialForm);
        if (!StringUtils.isEmpty(description))
            previewEvent.setDescription(description);

        if (previewEvent instanceof Vote && voteOptions != null && !voteOptions.isEmpty())
            previewEvent.setVoteOptions(voteOptions);

        TaskPreview preview = new TaskPreview();

        final String previewMsg = trimMessageAsInRealLife(messageService.createEventInfoMessage(user, previewEvent));
        preview.setSmsMessage(previewMsg);

        USSDMenu previewMenu = TaskType.VOTE.equals(eventType) ?
                generateSampleVoteMenu(user, group, (Vote) previewEvent) :
                generateSampleMeetingMenu(user, group, (Meeting) previewEvent);

        preview.addUssdMenu(trimPromptAsInRealLife(previewMenu));

        return ResponseEntity.ok(preview);
    }

    @RequestMapping(value = "/todo/{todoType}/{groupUid}", method = RequestMethod.GET)
    public ResponseEntity<TaskPreview> fetchPreviewOfEvent(HttpServletRequest request,
                                                           @PathVariable TodoType todoType,
                                                           @PathVariable String groupUid,
                                                           @RequestParam String subject,
                                                           @RequestParam long dateTimeEpochMillis,
                                                           @RequestParam(required = false) String description,
                                                           @RequestParam(required = false) String mediaFileUid,
                                                           @RequestParam(required = false) String responseTag) {
        User user = getUserFromRequest(request);
        Group group = groupBroker.load(groupUid);
        Instant dueDateTime = Instant.ofEpochMilli(dateTimeEpochMillis);

        Todo previewTodo = new Todo(user, group, todoType, subject, dueDateTime);

        if (!StringUtils.isEmpty(mediaFileUid))
            previewTodo.setImageUrl(SAMPLE_BITLY_LINK);
        if (!StringUtils.isEmpty(responseTag))
            previewTodo.setResponseTag(responseTag);
        if (!StringUtils.isEmpty(description))
            previewTodo.setDescription(description);

        TaskPreview preview = new TaskPreview();
        final String previewMessage = trimMessageAsInRealLife(messageService.createTodoAssignedMessage(user, previewTodo));
        preview.setSmsMessage(previewMessage);

        if (todoType != TodoType.ACTION_REQUIRED) { // since action required doesn't have a menu
            USSDMenu previewMenu = generateSampleTodoMenu(user, group, previewTodo);
            preview.addUssdMenu(trimPromptAsInRealLife(previewMenu));
        }

        return ResponseEntity.ok(preview);
    }

    private USSDMenu generateSampleVoteMenu(User user, Group group, Vote vote) {
        // since user is by definition the creating user
        final String[] promptFields = new String[]{group.getName(), user.getDisplayName(), vote.getName()};

        final String prompt = EventSpecialForm.MASS_VOTE.equals(vote.getSpecialForm()) ? "prompt-vote-mass" : "prompt-vote";
        USSDMenu openingMenu = new USSDMenu(messageSource.getMessage("ussd.home.start." + prompt, promptFields, Locale.ENGLISH));

        if (vote.getVoteOptions().isEmpty()) {
            openingMenu.addMenuOptions(optionsYesNo("ussd.vote.options.", user));
            openingMenu.addMenuOption("ABSTAIN", messageSource.getMessage("ussd.vote.options.abstain", null, user.getLocale()));
        } else {
            vote.getVoteOptions().forEach(o -> openingMenu.addMenuOption("sample?option=" + o, o));
        }

        if (!StringUtils.isEmpty(vote.getDescription())) {
            openingMenu.addMenuOption("ussd/vote/description?voteUid=" + vote.getUid() + "&back=respond",
                    messageSource.getMessage("ussd.home.generic.moreinfo", null, Locale.ENGLISH));
        }

        return openingMenu;
    }

    private USSDMenu generateSampleMeetingMenu(User user, Group group, Meeting meeting) {
        String[] meetingDetails = new String[]{group.getName(), user.getName(), meeting.getName(),
                meeting.getEventDateTimeAtSAST().format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a"))};

        // if the composed message is longer than 120 characters, we are going to go over, so return a shortened message
        String defaultPrompt = messageSource.getMessage("ussd.home.start.prompt-rsvp", meetingDetails, user.getLocale());
        if (defaultPrompt.length() > 120)
            defaultPrompt = messageSource.getMessage("ussd.home.start.prompt-rsvp.short", meetingDetails, user.getLocale());

        USSDMenu openingMenu = new USSDMenu(defaultPrompt);
        openingMenu.setMenuOptions(optionsYesNo("ussd.options.", user));

        if (!StringUtils.isEmpty(meeting.getDescription())) {
            openingMenu.addMenuOption("description", messageSource.getMessage("ussd.home.generic.moreinfo", null, user.getLocale()));
        }

        return openingMenu;
    }

    private USSDMenu generateSampleTodoMenu(User user, Group group, Todo todo) {
        switch (todo.getType()) {
            case INFORMATION_REQUIRED:
                final String infoPrompt = messageSource.getMessage("ussd.todo.info.prompt",
                        new String[] { todo.getCreatorAlias(), todo.getMessage() }, user.getLocale());
                return new USSDMenu(infoPrompt, "respond");
            case VOLUNTEERS_NEEDED:
                final String volunteerPrompt = messageSource.getMessage("ussd.todo.volunteer.prompt",
                        new String[] { todo.getCreatorAlias(), todo.getMessage() }, user.getLocale());
                return new USSDMenu(volunteerPrompt, optionsYesNo("ussd.options.", user));
            case VALIDATION_REQUIRED:
                final String confirmationPrompt = messageSource.getMessage("ussd.todo.validate.prompt",
                        new String[] { todo.getCreatorAlias(), todo.getMessage() }, user.getLocale());
                USSDMenu menu = new USSDMenu(confirmationPrompt);
                menu.addMenuOptions(optionsYesNo("ussd.options.", user));
                menu.addMenuOption("respond", messageSource.getMessage("ussd.todo.validate.option.unsure", null, user.getLocale()));
                return menu;
            default:
                throw new TodoTypeMismatchException();
        }
    }

    private LinkedHashMap<String, String> optionsYesNo(String optionMsgKey, User user) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("YES", messageSource.getMessage(optionMsgKey + "yes", null, user.getLocale()));
        map.put("NO", messageSource.getMessage(optionMsgKey + "no", null, user.getLocale()));
        return map;
    }

    private USSDMenu trimPromptAsInRealLife(USSDMenu menu) {
        final Integer charsToTrim = menu.getMenuCharLength() - (140 - 1); // adding a character, for safety
        if (charsToTrim < 0)
            return menu;

        String currentPrompt = menu.getPromptMessage();
        log.info("about to trim this current prompt = {}, and going to trim this many characters: {}", currentPrompt, charsToTrim);
        String revisedPrompt = currentPrompt.substring(0, Math.max(1, currentPrompt.length() - charsToTrim));
        menu.setPromptMessage(revisedPrompt);
        return menu;
    }

    private String trimMessageAsInRealLife(String message) {
        final Integer charsToTrim = message.length() - (160 - 1); // adding a character, for safety
        return charsToTrim < 0 ? message : message.substring(0, 160 - 1);

    }

}
