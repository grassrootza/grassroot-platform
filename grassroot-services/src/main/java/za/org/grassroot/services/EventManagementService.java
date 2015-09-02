package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface EventManagementService {

    public Event createEvent(String name, User createdByUser, Group group);

    public Event createEvent(String name, User createdByUser);

    public Event loadEvent(Long eventId);

    public Event getLastCreatedEvent(User creatingUser);

    public Event setSubject(Long eventId, String subject);

    public Event setGroup(Long eventId, Long groupId);

    public Event setLocation(Long eventId, String location);

    public Event setDay(Long eventId, String day);

    public Event setTime(Long eventId, String time);

    public Event cancelEvent(Long eventId);

    List<Event> findByAppliesToGroup(Group appliesToGroup );

}
