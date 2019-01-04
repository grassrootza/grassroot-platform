package za.org.grassroot.webapp.controller.ussd;

import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.SafetyEventBroker;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.util.USSDUrlUtil;

@Service
public class UssdSafetyGroupServiceImpl implements UssdSafetyGroupService {
	private final UssdSupport ussdSupport;
	private final SafetyEventBroker safetyEventBroker;
	private final GroupQueryBroker groupQueryBroker;

	public UssdSafetyGroupServiceImpl(UssdSupport ussdSupport, SafetyEventBroker safetyEventBroker, GroupQueryBroker groupQueryBroker) {
		this.ussdSupport = ussdSupport;
		this.safetyEventBroker = safetyEventBroker;
		this.groupQueryBroker = groupQueryBroker;
	}

	@Override
	public USSDMenu assemblePanicButtonActivationMenu(User user) {
		USSDMenu menu;
		if (user.hasSafetyGroup()) {
			boolean isBarred = safetyEventBroker.isUserBarred(user.getUid());
			String message = (!isBarred) ? ussdSupport.getMessage(USSDSection.HOME, "safety.activated", UssdSupport.promptKey, user)
					: ussdSupport.getMessage(USSDSection.HOME, "safety.barred", UssdSupport.promptKey, user);
			if (!isBarred) {
				safetyEventBroker.create(user.getUid(), user.getSafetyGroup().getUid());
			}
			menu = new USSDMenu(message);
		} else {
			menu = new USSDMenu(ussdSupport.getMessage(USSDSection.HOME, "safety.not-activated", UssdSupport.promptKey, user));
			if (groupQueryBroker.fetchUserCreatedGroups(user, 0, 1).getTotalElements() != 0) {
				menu.addMenuOption(UssdSupport.safetyMenus + "pick-group", ussdSupport.getMessage(USSDSection.HOME, "safety", UssdSupport.optionsKey + "existing", user));
			}
			menu.addMenuOption(UssdSupport.safetyMenus + "new-group", ussdSupport.getMessage(USSDSection.HOME, "safety", UssdSupport.optionsKey + "new", user));
			menu.addMenuOption(UssdSupport.startMenu, ussdSupport.getMessage(UssdSupport.optionsKey + "back.main", user));
		}
		return menu;
	}

	@Override
	public USSDMenu assemblePanicButtonActivationResponse(User user, SafetyEvent safetyEvent) {
		String activateByDisplayName = safetyEvent.getActivatedBy().getDisplayName();
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(USSDSection.HOME, "safety.responder", UssdSupport.promptKey, activateByDisplayName, user));
		menu.addMenuOptions(ussdSupport.optionsYesNo(user, USSDUrlUtil.safetyMenuWithId("record-response", safetyEvent.getUid())));
		return menu;
	}
}
