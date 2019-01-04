package za.org.grassroot.webapp.controller.ussd;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.dto.task.TaskMinimalDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;

import java.util.LinkedHashMap;

@Service
public class UssdMeetingServiceImpl implements UssdMeetingService {

	private final TaskBroker taskBroker;
	private final UssdSupport ussdSupport;

	public UssdMeetingServiceImpl(TaskBroker taskBroker, UssdSupport ussdSupport) {
		this.taskBroker = taskBroker;
		this.ussdSupport = ussdSupport;
	}

	@Override
	public USSDMenu assembleRsvpMenu(User user, Event meeting) {
		// do this so various bits of assembly are guaranteed to happen in a TX
		TaskMinimalDTO mtgDetails = taskBroker.fetchDescription(user.getUid(), meeting.getUid(), TaskType.MEETING);
		String[] meetingDetails = new String[]{mtgDetails.getAncestorGroupName(),
				mtgDetails.getCreatedByUserName(), mtgDetails.getTitle(),
				meeting.getEventDateTimeAtSAST().format(UssdSupport.dateTimeFormat)};

		// if the composed message is longer than 120 characters, we are going to go over, so return a shortened message
		String defaultPrompt = ussdSupport.getMessage(USSDSection.HOME, UssdSupport.startMenu, UssdSupport.promptKey + "-rsvp", meetingDetails, user);
		if (defaultPrompt.length() > 120) {
			defaultPrompt = ussdSupport.getMessage(USSDSection.HOME, UssdSupport.startMenu, UssdSupport.promptKey + "-rsvp.short", meetingDetails, user);
		}

		String optionUri = UssdSupport.meetingMenus + "rsvp" + UssdSupport.entityUidUrlSuffix + meeting.getUid();
		USSDMenu openingMenu = new USSDMenu(defaultPrompt);
		openingMenu.setMenuOptions(new LinkedHashMap<>(ussdSupport.optionsYesNo(user, optionUri, optionUri)));

		if (!StringUtils.isEmpty(meeting.getDescription())) {
			openingMenu.addMenuOption(UssdSupport.meetingMenus + "description?mtgUid=" + meeting.getUid() + "&back=respond", ussdSupport.getMessage("home.generic.moreinfo", user));
		}

		return openingMenu;
	}
}
