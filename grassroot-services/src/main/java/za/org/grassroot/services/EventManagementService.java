package za.org.grassroot.services;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

/**
 * @author Lesetse Kimwaga
 */
public interface EventManagementService {

    public Event createEvent(String name, User createdByUser, Group group);

    public Event createEvent(String name, User createdByUser);

    public Event loadEvent(Long eventId);

    public Event getLastCreatedEvent(User creatingUser);

    public Event setGroup(Long eventId, Long groupId);

    public Event setLocation(Long eventId, String location);

    public Event setDay(Long eventId, String day);

    public Event setTime(Long eventId, String time);

}
