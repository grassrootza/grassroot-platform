package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.EventRepository;

import java.util.List;

/**
 * Created by aakilomar on 8/20/15.
 */
@Component
public class EventManager implements EventManagementService {

    @Autowired
    EventRepository  eventRepository;

    @Autowired
    GroupManagementService groupManager;

    /*
    This createEvent method is used primarily by the USSD interface, where we do not have all the information yet.
    At this stage we would have created the user, group and asked the Name of the Event
     */
    @Override
    public Event createEvent(String name, User createdByUser, Group appliesToGroup) {
        return eventRepository.save(new Event(name,createdByUser,appliesToGroup));
    }

    /*
    Depending on the menu flow in the USSD interface, we might not know the group at a point when we want to create
    the event, hence generating this method and the one that follows
     */
    @Override
    public Event createEvent(String name, User createdByUser) {
        return eventRepository.save(new Event(name, createdByUser));
    }

    @Override
    public Event loadEvent(Long eventId) {
        return eventRepository.findOne(eventId);
    }

    @Override
    public Event getLastCreatedEvent(User creatingUser) {
        return eventRepository.findFirstByCreatedByUserOrderByIdDesc(creatingUser);
    }

    @Override
    public Event setSubject(Long eventId, String subject) {
        Event eventToUpdate = eventRepository.findOne(eventId);
        eventToUpdate.setName(subject);
        return eventRepository.save(eventToUpdate);
    }

    @Override
    public Event setGroup(Long eventId, Long groupId) {
        // todo: check if there isn't a quicker way to do this than running these queries (could get expensive if many events & groups?)
        Event eventToUpdate = eventRepository.findOne(eventId);
        if (eventToUpdate.getAppliesToGroup() != null && eventToUpdate.getAppliesToGroup().getId() == groupId) {
            return eventToUpdate;
        } else {
            eventToUpdate.setAppliesToGroup(groupManager.loadGroup(groupId));
            return eventRepository.save(eventToUpdate);
        }
    }

    @Override
    public Event setLocation(Long eventId, String location) {
        Event eventToUpdate = eventRepository.findOne(eventId);
        eventToUpdate.setEventLocation(location);
        return eventRepository.save(eventToUpdate);
    }

    @Override
    public Event setDay(Long eventId, String day) {
        Event eventToUpdate = eventRepository.findOne(eventId);
        eventToUpdate.setDayOfEvent(day);
        return eventRepository.save(eventToUpdate);
    }

    @Override
    public Event setTime(Long eventId, String time) {
        Event eventToUpdate = eventRepository.findOne(eventId);
        eventToUpdate.setTimeOfEvent(time);
        return eventRepository.save(eventToUpdate);
    }

    @Override
    public List<Event> findByAppliesToGroup(Group group) {
        return eventRepository.findByAppliesToGroup(group);
    }
}
