package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.services.exception.TodoTypeMismatchException;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;


@Service
public class UssdTodoServiceImpl implements UssdTodoService {
	private static final String REL_PATH = "todo";

	private final Logger log = LoggerFactory.getLogger(UssdTodoServiceImpl.class);

	private final UssdSupport ussdSupport;
	private final USSDMessageAssembler messageAssembler;

	public UssdTodoServiceImpl(UssdSupport ussdSupport, USSDMessageAssembler messageAssembler) {
		this.ussdSupport = ussdSupport;
		this.messageAssembler = messageAssembler;
	}

	@Override
	public USSDMenu respondToTodo(User user, Todo todo) {
		log.info("Generating response menu for entity: {}", todo);
		switch (todo.getType()) {
			case INFORMATION_REQUIRED:
				final String infoPrompt = messageAssembler.getMessage("todo.info.prompt",
						new String[]{todo.getCreatorAlias(), todo.getMessage()}, user);
				return new USSDMenu(infoPrompt, REL_PATH + "/respond/info?todoUid=" + todo.getUid());
			case VOLUNTEERS_NEEDED:
				final String volunteerPrompt = messageAssembler.getMessage("todo.volunteer.prompt",
						new String[]{todo.getCreatorAlias(), todo.getMessage()}, user);
				log.info("volunteer todo assembled, prompt: {}", volunteerPrompt);
				return new USSDMenu(volunteerPrompt, ussdSupport.optionsYesNo(user, "todo/respond/volunteer?todoUid=" + todo.getUid()));
			case VALIDATION_REQUIRED:
				final String confirmationPrompt = messageAssembler.getMessage("todo.validate.prompt",
						new String[]{todo.getCreatorAlias(), todo.getMessage()}, user);
				USSDMenu menu = new USSDMenu(confirmationPrompt);
				menu.addMenuOptions(ussdSupport.optionsYesNo(user, "todo/respond/validate?todoUid=" + todo.getUid()));
				menu.addMenuOption(REL_PATH + "/respond/validate?todoUid=" + todo.getUid() + "&" + UssdSupport.yesOrNoParam + "=unsure",
						messageAssembler.getMessage("todo.validate.option.unsure", user));
				return menu;
			default:
				throw new TodoTypeMismatchException();
		}
	}
}
