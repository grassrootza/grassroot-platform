package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;

public interface UssdMeetingService {
	USSDMenu assembleRsvpMenu(User user, Event meeting);
}
