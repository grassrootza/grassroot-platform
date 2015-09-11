package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface EventManagementService {

    public Event createEvent(String name, User createdByUser, Group group);

    public Event createEvent(String name, Long createdByUserId, Long groupId);

    public Event createEvent(String name, User createdByUser);

    public Event createMeeting(User createdByUser);

    public Event loadEvent(Long eventId);

    public Event getLastCreatedEvent(User creatingUser);

    public Event setSubject(Long eventId, String subject);

    public Event setGroup(Long eventId, Long groupId);

    public Event setLocation(Long eventId, String location);

    public Event setDateTimeString(Long eventId, String dateTimeString);

    public Event setEventTimestamp(Long eventId, Timestamp eventDateTime);

    public Event updateEvent(Event eventToUpdate);

    public Event cancelEvent(Long eventId);

    List<Event> findByAppliesToGroup(Group appliesToGroup);

    List<Event> getUpcomingEvents(Group group);

}
