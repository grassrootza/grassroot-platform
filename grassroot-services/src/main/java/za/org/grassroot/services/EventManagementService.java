package za.org.grassroot.services;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

/**
 * @author Lesetse Kimwaga
 */
public interface EventManagementService {

    public Event createEvent(String name, User createdByUser, Group group);

}
