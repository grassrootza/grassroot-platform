package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;

public interface UssdLiveWireService {
	USSDMenu assembleLiveWireOpening(User user, int page);
}
