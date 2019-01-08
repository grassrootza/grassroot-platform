package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;

import java.util.List;

import static za.org.grassroot.webapp.controller.ussd.UssdSupport.shortDateFormat;
import static za.org.grassroot.webapp.enums.USSDSection.LIVEWIRE;

@Service
public class UssdLiveWireServiceImpl implements UssdLiveWireService {
	private final Logger log = LoggerFactory.getLogger(UssdLiveWireServiceImpl.class);

	private final UssdSupport ussdSupport;
	private final LiveWireAlertBroker liveWireAlertBroker;

	public UssdLiveWireServiceImpl(UssdSupport ussdSupport, LiveWireAlertBroker liveWireAlertBroker) {
		this.ussdSupport = ussdSupport;
		this.liveWireAlertBroker = liveWireAlertBroker;
	}

	@Override
	public USSDMenu assembleLiveWireOpening(User user, int page) {
		long startTime = System.currentTimeMillis();
		long groupsForInstant = liveWireAlertBroker.countGroupsForInstantAlert(user.getUid());
		List<Meeting> meetingList = liveWireAlertBroker.meetingsForAlert(user.getUid());

		log.info("Generating LiveWire menu, groups for instant alert {}, meetings {}, took {} msecs",
				groupsForInstant, meetingList.size(), System.currentTimeMillis() - startTime);

		USSDMenu menu;
		if (groupsForInstant == 0L && meetingList.isEmpty()) {
			menu = new USSDMenu(ussdSupport.getMessage(LIVEWIRE, ussdSupport.startMenu, "prompt.nomeetings", user));
			menu.addMenuOption(ussdSupport.meetingMenus + ussdSupport.startMenu + "?newMtg=1", "Create a meeting");
			menu.addMenuOption(ussdSupport.startMenu, "Main menu");
			menu.addMenuOption("exit", "Exit");
		} else if (meetingList.isEmpty()) {
			menu = new USSDMenu(ussdSupport.getMessage(LIVEWIRE, ussdSupport.startMenu, "prompt.instant.only", user));
			menu.addMenuOption("livewire/instant", ussdSupport.getMessage(LIVEWIRE, ussdSupport.startMenu, ussdSupport.optionsKey + "instant", user));
			menu.addMenuOption(ussdSupport.meetingMenus + ussdSupport.startMenu + "?newMtg=1", ussdSupport.getMessage(LIVEWIRE, ussdSupport.startMenu, ussdSupport.optionsKey + "mtg.create", user));
			menu.addMenuOption(ussdSupport.startMenu, ussdSupport.getMessage(LIVEWIRE, ussdSupport.startMenu, ussdSupport.optionsKey + "home", user));
		} else {
			final String prompt = groupsForInstant != 0L ?
					ussdSupport.getMessage(LIVEWIRE, ussdSupport.startMenu, "prompt.meetings.only", user) :
					ussdSupport.getMessage(LIVEWIRE, ussdSupport.startMenu, "prompt.both", user);
			menu = new USSDMenu(prompt);

			int pageLimit = page == 0 ? 2 : (page + 1) * 3 - 1; // because of opening page lower chars
			int pageStart = page == 0 ? 0 : (page * 3) - 1;
			for (int i = pageStart; i < pageLimit && i < meetingList.size(); i++) {
				Meeting meeting = meetingList.get(i);
				String[] fields = new String[]{
						trimMtgName(meeting.getName()),
						meeting.getEventDateTimeAtSAST().format(ussdSupport.shortDateFormat)};
				menu.addMenuOption("livewire/mtg?mtgUid=" + meeting.getUid(),
						ussdSupport.getMessage(LIVEWIRE, ussdSupport.startMenu, ussdSupport.optionsKey + "meeting", fields, user));
			}

			if (pageLimit < meetingList.size()) {
				menu.addMenuOption(ussdSupport.startMenu + "_livewire?page=" + (page + 1), ussdSupport.getMessage("options.more", user));
			}

			if (page > 0) {
				menu.addMenuOption(ussdSupport.startMenu + "_livewire?page=" + (page - 1), ussdSupport.getMessage("options.back", user));
			}
		}

		if (groupsForInstant != 0L) {
			menu.addMenuOption("livewire/instant", ussdSupport.getMessage(LIVEWIRE, ussdSupport.startMenu, ussdSupport.optionsKey + "instant", user));
			if (!user.isLiveWireContact()) {
				menu.addMenuOption("livewire/register", ussdSupport.getMessage(LIVEWIRE, ussdSupport.startMenu, ussdSupport.optionsKey + "register", user));
			}
		}
		return menu;
	}

	private String trimMtgName(String name) {
		return name.length() < 20 ? name : name.substring(0, 20) + "...";
	}
}
