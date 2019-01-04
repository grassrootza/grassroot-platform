package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;

public interface UssdSafetyGroupService {
	USSDMenu assemblePanicButtonActivationMenu(User user);

	USSDMenu assemblePanicButtonActivationResponse(User user, SafetyEvent safetyEvent);
}
