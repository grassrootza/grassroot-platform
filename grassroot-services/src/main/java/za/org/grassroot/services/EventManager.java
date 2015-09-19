package za.org.grassroot.services;

import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
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
    public Event createEvent(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups) {
        return eventRepository.save(new Event(name, createdByUser, appliesToGroup,includeSubGroups));
    }

    @Override
    public Event createEvent(String name, User createdByUser, Group appliesToGroup) {
        return  createEvent(name, createdByUser, appliesToGroup, false);
    }

    @Override
    public Event createEvent(String name, Long createdByUserId, Long appliesToGroupId, boolean includeSubGroups) {
        return createEvent(name, userManagementService.getUserById(createdByUserId),
                           groupManager.getGroupById(appliesToGroupId),includeSubGroups);
    }

    @Override
    public Event createEvent(String name, Long createdByUserId, Long appliesToGroupId) {
        return createEvent(name, createdByUserId, appliesToGroupId, false);
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
    public Event createMeeting(User createdByUser) {
        return eventRepository.save(new Event(createdByUser, EventType.Meeting));
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
    public Event setDateTimeString(Long eventId, String dateTimeString) {
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);
        eventToUpdate.setDateTimeString(dateTimeString);
        return saveandCheckChanges(beforeEvent,eventToUpdate);
    }

    @Override
    public Event setEventTimestamp(Long eventId, Timestamp eventDateTime) {
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);
        eventToUpdate.setEventStartDateTime(eventDateTime);
        return saveandCheckChanges(beforeEvent, eventToUpdate);
    }

    @Override
    public Event updateEvent(Event eventToUpdate) {
        // generic update for use from the web, where we get a bunch of changes applied at once

        Event beforeEvent = loadEvent(eventToUpdate.getId());
        eventToUpdate = fillOutEvent(eventToUpdate, beforeEvent);
        return saveandCheckChanges(beforeEvent, eventToUpdate);

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

    @Override
    public List<Event> getUpcomingEvents(Group group) {

        // todo: rather implement this in the repository I think, by a suitable method
        List<Event> allEvents = findByAppliesToGroup(group);
        List<Event> comingEvents = new ArrayList<>();

        for (Event event : allEvents) {
            if (event != null && !event.isCanceled() && event.getEventStartDateTime() != null &&
                    event.getEventStartDateTime().after(new Timestamp(Calendar.getInstance().getTimeInMillis()))) {
                comingEvents.add(event);
            }
        }

        return comingEvents;
    }

    private Event saveandCheckChanges(Event beforeEvent, Event changedEvent) {

        log.info("saveandCheckChanges...starting ... with before event .. " + beforeEvent.toString());

        // need to set this befoer saving the changed event, or the getters in minimuDataAvailable get confused
        boolean priorEventComplete = minimumDataAvailable(beforeEvent);
        Event savedEvent = eventRepository.save(changedEvent);

        log.info("saveandCheckChanges..." + savedEvent.toString());

        /*
        Check if we need to send meeting notifications
         */

            if (!priorEventComplete && minimumDataAvailable(savedEvent) && !savedEvent.isCanceled()) {
                jmsTemplateProducerService.sendWithNoReply("event-added",savedEvent);
                log.info("queued to event-added");
            }
            if (priorEventComplete && minimumDataAvailable(savedEvent) && !savedEvent.isCanceled()) {
                // let's send out a change notification if something changed in minimum required values
                if (!savedEvent.minimumEquals(beforeEvent)) {
                    jmsTemplateProducerService.sendWithNoReply("event-changed",savedEvent);
                    log.info("queued to event-changed");

                } else {
                    log.info("NOT queued to event-changed as minimum required values did not change...");

                }
            }
            if (priorEventComplete && !beforeEvent.isCanceled() && savedEvent.isCanceled()) {
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
        if (event.getDateTimeString() == null || event.getDateTimeString().trim().equals("")) minimum = false;
        if (event.getEventStartDateTime() == null) minimum = false;
        log.info("minimumDataAvailable...returning..." + minimum);

        return minimum;
    }

    /*
    Method to take a partially filled out event, from the web application, and add in a series of fields at once
     */
    private Event fillOutEvent(Event passedEvent, Event savedEvent) {

        // todo throw a proper exception if the two events don't have matching IDs

        if (passedEvent.getId() != savedEvent.getId()) {
            return null;
        }

        if (passedEvent.getName() == null && savedEvent.getName() != null) passedEvent.setName(savedEvent.getName());

        if (passedEvent.getEventLocation() == null && savedEvent.getEventLocation() != null)
            passedEvent.setEventLocation(savedEvent.getEventLocation());

        if (passedEvent.getAppliesToGroup() == null && savedEvent.getAppliesToGroup() != null)
            passedEvent.setAppliesToGroup(savedEvent.getAppliesToGroup());

        if (passedEvent.getCreatedByUser() == null && savedEvent.getCreatedByUser() != null)
            passedEvent.setCreatedByUser(savedEvent.getCreatedByUser());

        if (passedEvent.getCreatedDateTime() == null && savedEvent.getCreatedDateTime() != null)
            passedEvent.setCreatedDateTime(savedEvent.getCreatedDateTime());

        if (passedEvent.getEventStartDateTime() == null && savedEvent.getEventStartDateTime() != null)
            passedEvent.setEventStartDateTime(savedEvent.getEventStartDateTime());

        if (passedEvent.getEventType() == null && savedEvent.getEventType() != null)
            passedEvent.setEventType(savedEvent.getEventType());

        if (passedEvent.getDateTimeString() == null && savedEvent.getDateTimeString() != null)
            passedEvent.setDateTimeString(savedEvent.getDateTimeString());

        // todo: think through this -- may make it impossible to switch off include subgroups
        if (!passedEvent.isIncludeSubGroups() && savedEvent.isIncludeSubGroups())
            passedEvent.setIncludeSubGroups(savedEvent.isIncludeSubGroups());

        return passedEvent;

    }

}
