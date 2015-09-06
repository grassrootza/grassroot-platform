package za.org.grassroot.services;

import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

import java.util.List;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 8/20/15.
 */
@Component
public class EventManager implements EventManagementService {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());


    @Autowired
    EventRepository  eventRepository;

    @Autowired
    GroupManagementService groupManager;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GenericJmsTemplateProducerService jmsTemplateProducerService;

    /*
    This createEvent method is used primarily by the USSD interface, where we do not have all the information yet.
    At this stage we would have created the user, group and asked the Name of the Event
     */
    @Override
    public Event createEvent(String name, User createdByUser, Group appliesToGroup) {
        return eventRepository.save(new Event(name,createdByUser,appliesToGroup));
    }

    @Override
    public Event createEvent(String name, Long createdByUserId, Long appliesToGroupId) {
        return createEvent(name, userManagementService.getUserById(createdByUserId),
                groupManager.getGroupById(appliesToGroupId));
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
    public Event createEvent(String name, Long createdByUserId) {
        return createEvent(name, userManagementService.getUserById(createdByUserId));
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
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);
        eventToUpdate.setName(subject);
        return saveandCheckChanges(beforeEvent, eventToUpdate);
    }

    @Override
    public Event setGroup(Long eventId, Long groupId) {
        // todo: check if there isn't a quicker way to do this than running these queries (could get expensive if many events & groups?)
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);

        if (eventToUpdate.getAppliesToGroup() != null && eventToUpdate.getAppliesToGroup().getId() == groupId) {
            return eventToUpdate;
        } else {
            eventToUpdate.setAppliesToGroup(groupManager.loadGroup(groupId));
            return saveandCheckChanges(beforeEvent, eventToUpdate);
        }
    }

    @Override
    public Event setLocation(Long eventId, String location) {
        log.info("setLocation...");
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);
        eventToUpdate.setEventLocation(location);
        return saveandCheckChanges(beforeEvent, eventToUpdate);
    }

    @Override
    public Event setDay(Long eventId, String day) {
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);
        eventToUpdate.setDayOfEvent(day);
        return saveandCheckChanges(beforeEvent,eventToUpdate);
    }

    @Override
    public Event setTime(Long eventId, String time) {
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);
        eventToUpdate.setTimeOfEvent(time);
        return saveandCheckChanges(beforeEvent,eventToUpdate);
    }

    @Override
    public Event cancelEvent(Long eventId) {
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);
        eventToUpdate.setCanceled(true);
        return saveandCheckChanges(beforeEvent,eventToUpdate);
    }

    @Override
    public List<Event> findByAppliesToGroup(Group group) {
        return eventRepository.findByAppliesToGroup(group);
    }

    private Event saveandCheckChanges(Event beforeEvent, Event changedEvent) {

        log.info("saveandCheckChanges...starting");
        Event savedEvent = eventRepository.save(changedEvent);
        log.info("saveandCheckChanges..." + savedEvent.toString());
        /*
        Check if we need to send meeting notifications
         */

            if (!minimumDataAvailable(beforeEvent) && minimumDataAvailable(savedEvent) && !savedEvent.isCanceled()) {
                jmsTemplateProducerService.sendWithNoReply("event-added",savedEvent);
                log.info("queued to event-added");
            }
            if (minimumDataAvailable(beforeEvent) && minimumDataAvailable(savedEvent) && !savedEvent.isCanceled()) {
                // let's send out a change notification
                //todo but first see if something actually changed
                jmsTemplateProducerService.sendWithNoReply("event-changed",savedEvent);
                log.info("queued to event-changed");
            }
            if (!beforeEvent.isCanceled() && savedEvent.isCanceled()) {
                // ok send out cancelation notifications
                jmsTemplateProducerService.sendWithNoReply("event-cancelled", savedEvent);
                log.info("queued to event-cancelled");

            }

        return savedEvent;
    }

    private boolean minimumDataAvailable(Event event) {
        boolean minimum = true;
        log.info("minimumDataAvailable..." + event.toString());
        if (event.getName() == null || event.getName().trim().equals("")) minimum = false;
        if (event.getEventLocation() == null || event.getEventLocation().trim().equals("")) minimum = false;
        if (event.getAppliesToGroup() == null ) minimum = false;
        if (event.getCreatedByUser() == null) minimum = false;
        if (event.getDayOfEvent() == null || event.getDayOfEvent().trim().equals("")) minimum = false;
        if (event.getTimeOfEvent() == null || event.getTimeOfEvent().trim().equals("")) minimum = false;
        log.info("minimumDataAvailable...returning..." + minimum);

        return minimum;
    }

}
