package za.org.grassroot.services;

import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventChanged;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

import javax.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 8/20/15.
 */
@Component
public class EventManager implements EventManagementService {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

    //TODO aakil move this to the properties file as soon as you get the property injection to work
    private final int SITE_REMINDERMINUTES = 1440; // 24hours

    @Autowired
    EventRepository eventRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupManagementService groupManager;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GenericJmsTemplateProducerService jmsTemplateProducerService;

    @Autowired
    EventLogManagementService eventLogManagementService;

    @Autowired
    EntityManager entityManager;

    /*
    Variety of createEvent methods with different signatures caters to USSD menus in particular, so can create event
    with varying degrees of information. Am defaulting to a meeting requires an RSVP, since that will be most cases.
     */

    @Override
    public Event createEvent(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups, boolean rsvpRequired) {
        return createNewEvent(createdByUser, EventType.Meeting, rsvpRequired, name ,appliesToGroup, includeSubGroups,0);
    }

    @Override
    public Event createEvent(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups) {
        return createEvent(name, createdByUser, appliesToGroup, includeSubGroups, true);
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
    Depending on the user flow in the USSD/web interface, we might not know the group at a point when we want to create
    the event, hence generating this method and the one that follows
     */
    @Override
    public Event createEvent(String name, User createdByUser) {
        return createNewEvent(createdByUser, EventType.Meeting,true,name,null,false,0);
    }

    @Override
    public Event createMeeting(User createdByUser) {
        return createNewEvent(createdByUser, EventType.Meeting, true,"",null,false,0);
    }

    private Event createNewEvent(User createdByUser, EventType eventType, boolean rsvpRequired
            , String name, Group appliesToGroup, boolean includeSubGroups, int reminderMinutes) {
        Event event = new Event();
        event.setCreatedByUser(createdByUser);
        if (eventType == null) {
            event.setEventType(EventType.Meeting); //default
        } else {
            event.setEventType(eventType);
        }
        event.setRsvpRequired(rsvpRequired);
        event.setName(name);
        if (appliesToGroup != null) {
            event.setAppliesToGroup(appliesToGroup);
        }
        event.setIncludeSubGroups(includeSubGroups);
        //set the event reminder minutes
        if (reminderMinutes != 0) {
            event.setReminderMinutes(reminderMinutes);
        } else {
            // see if we can set it from the group
            if (appliesToGroup != null) {
                if (appliesToGroup.getReminderMinutes() != 0) {
                    reminderMinutes = appliesToGroup.getReminderMinutes();
                }
            }
            if (reminderMinutes == 0) {
                // set it to the site default
                reminderMinutes = SITE_REMINDERMINUTES;
            }
            event.setReminderMinutes(reminderMinutes);
        }
        event.setNoRemindersSent(0);

        return eventRepository.save(event);
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
        return saveandCheckChanges(new EventDTO(beforeEvent), eventToUpdate);
    }

    @Override
    public Event setGroup(Long eventId, Long groupId) {
        // todo: check if there isn't a quicker way to do this than running these queries (could get expensive if many events & groups?)
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);

        if (eventToUpdate.getAppliesToGroup() != null && eventToUpdate.getAppliesToGroup().getId() == groupId) {
            return eventToUpdate;
        } else {
            log.info("Okay, we are setting this event to this groupId: " + groupId);
            eventToUpdate.setAppliesToGroup(groupManager.loadGroup(groupId));
            return saveandCheckChanges(new EventDTO(beforeEvent), eventToUpdate);
        }
    }

    @Override
    public Event setLocation(Long eventId, String location) {
        log.info("setLocation...");
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);
        eventToUpdate.setEventLocation(location);
        return saveandCheckChanges(new EventDTO(beforeEvent), eventToUpdate);
    }

    @Override
    public Event setDateTimeString(Long eventId, String dateTimeString) {
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);
        eventToUpdate.setDateTimeString(dateTimeString);
        return saveandCheckChanges(new EventDTO(beforeEvent), eventToUpdate);
    }

    @Override
    public Event setEventTimestamp(Long eventId, Timestamp eventDateTime) {
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);
        eventToUpdate.setEventStartDateTime(eventDateTime);
        return saveandCheckChanges(new EventDTO(beforeEvent), eventToUpdate);
    }

    @Override
    public Event updateEvent(Event eventToUpdate) {
        // generic update for use from the web, where we get a bunch of changes applied at once

        Event savedEvent = loadEvent(eventToUpdate.getId());
        EventDTO beforeDTO = new EventDTO(savedEvent);
        savedEvent = applyChangesToEntity(eventToUpdate, savedEvent);
        return saveandCheckChanges(beforeDTO, savedEvent);

    }

    @Override
    public Event cancelEvent(Long eventId) {
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);
        eventToUpdate.setCanceled(true);
        return saveandCheckChanges(new EventDTO(beforeEvent), eventToUpdate);
    }

    @Override
    public List<Event> findByAppliesToGroup(Group group) {
        return eventRepository.findByAppliesToGroup(group);
    }

    @Override
    public List<Event> findByAppliesToGroupAndStartingAfter(Group group, Date date) {
        return eventRepository.findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceled(group, date, false);
    }

    @Override
    public List<Event> getUpcomingEvents(Group group) {

        return findByAppliesToGroupAndStartingAfter(group, new Date());
    }

    @Override
    public List<User> getListOfUsersThatRSVPYesForEvent(Event event) {
        return userRepository.findUsersThatRSVPYesForEvent(event);
    }

    @Override
    public List<User> getListOfUsersThatRSVPNoForEvent(Event event) {
        return userRepository.findUsersThatRSVPNoForEvent(event);
    }

    @Override
    public Map<User, EventRSVPResponse> getRSVPResponses(Event event) {
        // todo: there is almost certainly a faster/better way to do this
        Map<User, EventRSVPResponse> rsvpResponses = new LinkedHashMap<>();

        for (User user : event.getAppliesToGroup().getGroupMembers())
            rsvpResponses.put(user, EventRSVPResponse.NO_RESPONSE);

        for (User user : getListOfUsersThatRSVPYesForEvent(event))
            rsvpResponses.replace(user, EventRSVPResponse.YES);

        for (User user : getListOfUsersThatRSVPNoForEvent(event))
            rsvpResponses.replace(user, EventRSVPResponse.NO);

        return rsvpResponses;
    }

    @Override
    public List<Event> getOutstandingRSVPForUser(Long userId) {
        return getOutstandingRSVPForUser(userRepository.findOne(userId));
    }

    @Override
    public List<Event> getOutstandingRSVPForUser(User user) {
        // todo: we will probably use something like this at application load for every user that comes in via USSD
        // todo: so just wondering if we may want to do the below via a single query in one of the repositories?
        log.info("getOutstandingRSVPForUser..." + user.getId());
        List<Event> outstandingRSVPs = new ArrayList<Event>();
        List<Group> groups = groupManager.getGroupsPartOf(user);
        if (groups != null) {
            for (Group group : groups) {
                List<Event> upcomingEvents = getUpcomingEventsForGroupAndParentGroups(group);
                //log.info("getOustandingRSVPForUser ... checking " + upcomingEvents.size() + " events");
                if (upcomingEvents != null) {
                    for (Event event : upcomingEvents) {
                        if (event.isRsvpRequired() && event.getCreatedByUser().getId() != user.getId()) {
                            if (!eventLogManagementService.userRsvpForEvent(event, user)) {
                                outstandingRSVPs.add(event);
                                log.info("getOutstandingRSVPForUser...rsvpRequired..." + user.getId() + "...event..." + event.getId());
                            } else {
                                log.info("getOutstandingRSVPForUser...rsvp NOT Required..." + user.getId() + "...event..." + event.getId());

                            }
                        }
                    }
                }

            }
        }
        log.info("getOutstandingRSVPForUser..." + user.getId() + "...returning..." + outstandingRSVPs.size());

        return outstandingRSVPs;
    }

    @Override
    public List<Event> getUpcomingEventsForGroupAndParentGroups(Group group) {
        // check for events on this group level
        List<Event> upComingEvents = getUpcomingEvents(group);
        if (upComingEvents == null) {
            upComingEvents = new ArrayList<Event>();
        }

        // climb the tree and check events at each level if subgroups are included
        List<Group> parentGroups = groupManager.getAllParentGroups(group);

        if (parentGroups != null) {
            for (Group parentGroup : parentGroups) {
                List<Event> parentEvents = getUpcomingEvents(parentGroup);
                if (parentEvents != null) {
                    for (Event upComingEvent : parentEvents) {
                        if (upComingEvent.isIncludeSubGroups()) {
                            upComingEvents.add(upComingEvent);
                        }
                    }
                }

            }
        }
        return upComingEvents;
    }

    @Override
    public List<Event> getUpcomingEventsUserCreated(User requestingUser) {
        List<Event> possibleEvents = eventRepository.findByCreatedByUserAndEventStartDateTimeGreaterThanAndCanceled(requestingUser, new Date(), false);

        // take out events that were only partially formed ... todo: think if a way to make this faster than the iteration below
        List<Event> fullyFormedEvents = new ArrayList<>();
        for (Event event : possibleEvents) {
            if (minimumDataAvailable(event))
                fullyFormedEvents.add(event);
        }

        return fullyFormedEvents;
    }

    @Override
    public List<Event> getUpcomingEvents(User requestingUser) {
        // todo: at some point we will need this to be efficient ... for now, doing a very slow kludge, and going to avoid using the method

        List<Event> upcomingEvents = new ArrayList<>();

        for (Group group : requestingUser.getGroupsPartOf()) {
            upcomingEvents.addAll(getUpcomingEvents(group));
        }

        return upcomingEvents;
    }

    @Override
    public boolean hasUpcomingEvents(User requestingUser) {
        // likewise, if we start using this at outset of meetings menu, need to make it fast, possibly a query
        return getUpcomingEvents(requestingUser).size() != 0;
    }

    @Override
    public String[] populateNotificationFields(Event event) {
        return new String[]{
                event.getAppliesToGroup().getName(""),
                event.getCreatedByUser().nameToDisplay(),
                event.getName(),
                event.getDateTimeString(),
                event.getEventLocation()
        };
    }

    @Override
    public Map<String, String> getEventDescription(Event event) {
        Map<String, String> eventDescription = new HashMap<>();

        if (minimumDataAvailable(event)) {
            eventDescription.put("minimumData", "true");
            eventDescription.put("groupName", event.getAppliesToGroup().getName(""));
            eventDescription.put("creatingUser", event.getCreatedByUser().nameToDisplay());
            eventDescription.put("eventSubject", event.getName());
            eventDescription.put("createdDateTime", event.getCreatedDateTime().toString());
            eventDescription.put("dateTimeString", event.getDateTimeString());
            eventDescription.put("location", event.getEventLocation());
        } else {
            eventDescription.put("minimumData", "false");
        }

        return eventDescription;
    }

    @Override
    public Map<String, String> getEventDescription(Long eventId) {
        return getEventDescription(loadEvent(eventId));
    }

    @Override
    public int getNumberInvitees(Event event) {
        // may make this more sophisticated once we have message relays in place
        return event.getAppliesToGroup().getGroupMembers().size();
    }


    private Event saveandCheckChanges(EventDTO beforeEvent, Event changedEvent) {

        log.info("saveandCheckChanges...starting ... with before event .. " + beforeEvent.toString());
        log.info("saveandCheckChanges...changedEvent.id..." + changedEvent.getId());

        boolean priorEventComplete = minimumDataAvailable(beforeEvent);
        Event savedEvent = eventRepository.save(changedEvent);

        log.info("saveandCheckChanges...savedEvent..." + savedEvent.toString());

        /*
        Check if we need to send meeting notifications
         */

        if (!priorEventComplete && minimumDataAvailable(savedEvent) && !savedEvent.isCanceled()) {
            jmsTemplateProducerService.sendWithNoReply("event-added", new EventDTO(savedEvent));
            log.info("queued to event-added");
        }
        if (priorEventComplete && minimumDataAvailable(savedEvent) && !savedEvent.isCanceled()) {
            // let's send out a change notification if something changed in minimum required values

            if (!savedEvent.minimumEquals(beforeEvent)) {
                boolean startTimeChanged = false;
                if (!beforeEvent.getEventStartDateTime().equals(savedEvent.getEventStartDateTime()))
                    startTimeChanged = true;
                jmsTemplateProducerService.sendWithNoReply("event-changed", new EventChanged(new EventDTO(savedEvent), startTimeChanged));
                log.info("queued to event-changed event..." + savedEvent.getId() + "...version..." + savedEvent.getVersion());
            } else {
                log.info("NOT queued to event-changed as minimum required values did not change...");

            }
        }
        if (priorEventComplete && !beforeEvent.isCanceled() && savedEvent.isCanceled()) {
            // ok send out cancelation notifications
            jmsTemplateProducerService.sendWithNoReply("event-cancelled", new EventDTO(savedEvent));
            log.info("queued to event-cancelled");

        }

        return savedEvent;
    }

    private boolean minimumDataAvailable(Event event) {
        boolean minimum = true;
        log.finest("minimumDataAvailable..." + event.toString());
        if (event.getName() == null || event.getName().trim().equals("")) minimum = false;
        if (event.getEventLocation() == null || event.getEventLocation().trim().equals("")) minimum = false;
        if (event.getAppliesToGroup() == null) minimum = false;
        if (event.getCreatedByUser() == null) minimum = false;
        if (event.getDateTimeString() == null || event.getDateTimeString().trim().equals("")) minimum = false;
        if (event.getEventStartDateTime() == null) minimum = false;
        log.info("minimumDataAvailable...returning..." + minimum);

        return minimum;
    }

    private boolean minimumDataAvailable(EventDTO event) {
        boolean minimum = true;
        log.finest("minimumDataAvailable..." + event.toString());
        if (event.getName() == null || event.getName().trim().equals("")) minimum = false;
        if (event.getEventLocation() == null || event.getEventLocation().trim().equals("")) minimum = false;
        if (event.getAppliesToGroup() == null) minimum = false;
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

        /*
        We do not touch the two boolean fields, rsvpRequired and includeSubGroups, since those are set by
        default in the constructors, and if passedEvent has them set to something different to savedEvent,
         that means the user changed them, and hence they should be preserved. To test this.
         */

        return passedEvent;

    }

    /*
Method to take a partially filled out event, from the web application, and add in a series of fields at once
 */
    private Event applyChangesToEntity(Event passedEvent, Event savedEvent) {

        // todo throw a proper exception if the two events don't have matching IDs

        if (passedEvent.getId() != savedEvent.getId()) {
            return null;
        }

        if (passedEvent.getName() != null) savedEvent.setName(passedEvent.getName());

        if (passedEvent.getEventLocation() != null) savedEvent.setEventLocation(passedEvent.getEventLocation());

        //if (passedEvent.getAppliesToGroup() != null) savedEvent.setAppliesToGroup(passedEvent.getAppliesToGroup());

        //if (passedEvent.getCreatedByUser() != null) savedEvent.setCreatedByUser(passedEvent.getCreatedByUser());

        //if (passedEvent.getCreatedDateTime() != null) savedEvent.setCreatedDateTime(passedEvent.getCreatedDateTime());

        if (passedEvent.getEventStartDateTime() != null)
            savedEvent.setEventStartDateTime(passedEvent.getEventStartDateTime());

        //if (passedEvent.getEventType() != null) savedEvent.setEventType(passedEvent.getEventType());

        if (passedEvent.getDateTimeString() != null) savedEvent.setDateTimeString(passedEvent.getDateTimeString());

        /*
        We do not touch the two boolean fields, rsvpRequired and includeSubGroups, since those are set by
        default in the constructors, and if passedEvent has them set to something different to savedEvent,
         that means the user changed them, and hence they should be preserved. To test this.
         */

        return savedEvent;

    }

}
