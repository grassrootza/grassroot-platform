package za.org.grassroot.services;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventChanged;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;
import za.org.grassroot.services.util.CacheUtilService;

import javax.persistence.EntityManager;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
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

    @Autowired
    CacheUtilService cacheUtilService;

    @Autowired
    MeetingNotificationService meetingNotificationService;

    private final static double SMS_COST = 0.2; // might move to message services

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
                groupManager.getGroupById(appliesToGroupId), includeSubGroups);
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
        return createNewEvent(createdByUser, EventType.Meeting, true, name, null, false, 0);
    }

    @Override
    public Event createMeeting(User createdByUser) {
        return createNewEvent(createdByUser, EventType.Meeting, true, "", null, false, 0);
    }

    @Override
    public Event createVote(User createdByUser) {
        return createVote("", createdByUser);
    }

    @Override
    public Event createVote(String issue, User createdByUser) {
        return createNewEvent(createdByUser, EventType.Vote, true, issue, null, false, 0);
    }

    @Override
    public Event createVote(User createdByUser, Long groupId) {
        return createNewEvent(createdByUser, EventType.Vote, true, "", groupManager.loadGroup(groupId), false, -1);
    }


    @Override
    public Event createVote(String issue, Long userId, Long groupId, boolean includeSubGroups) {
        return createNewEvent(userManagementService.getUserById(userId), EventType.Vote, true, issue, groupManager.loadGroup(groupId), false, 0);
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

        Event createdEvent = eventRepository.save(event);
        log.fine("createNewEvent...created..." + createdEvent.toString());
        return createdEvent;
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
    public Event setGroup(Event event, Long groupId) {
        // helper method for web application, as this is most natural signature for that call -- to do as above
        return setGroup(event.getId(), groupId);
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
        log.info("Setting event timestamp, passed: " + eventDateTime.toString());
        Event eventToUpdate = eventRepository.findOne(eventId);
        Event beforeEvent = SerializationUtils.clone(eventToUpdate);
        eventToUpdate.setEventStartDateTime(eventDateTime);
        return saveandCheckChanges(new EventDTO(beforeEvent), eventToUpdate);
    }

    @Override
    public Event setEventReminderMinutes(Long eventId, Integer minutes) {
        Event event = loadEvent(eventId);
        event.setReminderMinutes(minutes);
        return eventRepository.save(event); // note: not doing save and check changes, because this shouldn't trigger an update or anything
    }

    @Override
    public Event setEventNoReminder(Long eventId) {
        Event event = loadEvent(eventId);
        event.setReminderMinutes(-1);
        return eventRepository.save(event); // as above, not using saveandCheckChanges for this, at least for now
    }

    @Override
    public Event updateEvent(Event eventToUpdate) {
        // generic update for use from the web, where we get a bunch of changes applied at once

        log.info("Inside updateEvent method, and the event that we are passed: " + eventToUpdate);
        Event savedEvent = loadEvent(eventToUpdate.getId());
        EventDTO beforeDTO = new EventDTO(savedEvent);
        log.info("Inside updateEvent method, event that comes back from server: " + beforeDTO);
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
    public List<Event> findUpcomingMeetingsForGroup(Group group, Date date) {
        return eventRepository.findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceledAndEventType(group, date, false, EventType.Meeting);
    }

    @Override
    public List<Event> findUpcomingVotesForGroup(Group group, Date date) {
        return eventRepository.findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceledAndEventType(group, date, false, EventType.Vote);
    }

    @Override
    public List<Event> getUpcomingMeetings(Long groupId) {

        return getUpcomingMeetings(groupManager.loadGroup(groupId));
    }

    @Override
    public List<Event> getUpcomingMeetings(Group group) {

        return findUpcomingMeetingsForGroup(group, new Date());
    }

    @Override
    public List<Event> getUpcomingVotes(Long groupId) {

        return getUpcomingVotes(groupManager.loadGroup(groupId));
    }

    @Override
    public List<Event> getUpcomingVotes(Group group) {

        return findUpcomingVotesForGroup(group, new Date());
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
    public List<Event> getOutstandingVotesForUser(Long userId) {
        return getOutstandingVotesForUser(userRepository.findOne(userId));
    }
    @Override
    public List<Event> getOutstandingVotesForUser(User user) {
        return getOutstandingResponseForUser(user, EventType.Vote);
    }


    @Override
    public List<Event> getOutstandingRSVPForUser(Long userId) {
        return getOutstandingRSVPForUser(userRepository.findOne(userId));
    }

    /*
    todo - aakil - figure out why the annotation is not working
    @Cacheable(value="userRSVP", key="#user.id", cacheManager = "coreCacheManager")
     */

    @Override
    public List<Event> getOutstandingRSVPForUser(User user) {
        return getOutstandingResponseForUser(user, EventType.Meeting);
    }

    private List<Event> getOutstandingResponseForUser(User user, EventType eventType) {
        log.info("getOutstandingResponseForUser..." + user.getPhoneNumber() + "...type..." + eventType.toString());

        List<Event> outstandingRSVPs = cacheUtilService.getOutstandingResponseForUser(user,eventType);

        if (outstandingRSVPs == null) {
            // fetch from the database
            Map eventMap = new HashedMap<Long,Long>();
            outstandingRSVPs = new ArrayList<Event>();
            List<Group> groups = groupManager.getGroupsPartOf(user);
            log.fine("getOutstandingResponseForUser...after...getGroupsPartOf...");
            if (groups != null) {
                log.fine("getOutstandingResponseForUser...number of groups..." + groups.size());

                for (Group group : groups) {
                    log.fine("getOutstandingResponseForUser...before...getUpcomingEventsForGroupAndParentGroups..." + group.getId());
                    List<Event> upcomingEvents = getUpcomingEventsForGroupAndParentGroups(group);
                    log.fine("getOutstandingResponseForUser...after...getUpcomingEventsForGroupAndParentGroups..." + group.getId());

                    if (upcomingEvents != null) {
                        for (Event event : upcomingEvents) {
                            log.fine("getOutstandingResponseForUser...start...event check..." + event.getId());

                            if (event.isRsvpRequired()  && event.getEventType() == eventType) {
                                //rsvp
                                if ((eventType == EventType.Meeting && event.getCreatedByUser().getId() != user.getId())
                                        || eventType != EventType.Meeting) {
                                    if (!eventLogManagementService.userRsvpForEvent(event, user)) {
                                        //see if we added it already as the user can be in multiple groups in a group structure
                                        if (eventMap.get(event.getId()) == null) {
                                            outstandingRSVPs.add(event);
                                            eventMap.put(event.getId(),event.getId());
                                        }
                                        log.info("getOutstandingResponseForUser..." + eventType.toString() + " Required..." + user.getPhoneNumber() + "...event..." + event.getId());
                                    } else {
                                        log.info("getOutstandingResponseForUser..." + eventType.toString() + " NOT Required..." + user.getPhoneNumber() + "...event..." + event.getId());

                                    }
                                }
                            } else {
                                log.fine("getOutstandingResponseForUser...start...event check..." + event.getId() + "...NOT matching on eventtype..." + event.getEventType().toString() + "... or RSVP required..." + event.isRsvpRequired());

                            }

                            log.fine("getOutstandingResponseForUser...end...event check..." + event.getId());

                        }
                    }

                }
                cacheUtilService.putOutstandingResponseForUser(user,eventType,outstandingRSVPs);
            }

        }
        log.info("getOutstandingResponseForUser..." + user.getPhoneNumber() + "...type..." + eventType.toString() + "...returning..." + outstandingRSVPs.size());

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
                log.fine("parentGroup..." + parentGroup.getId());
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
    public List<Event> getPaginatedEventsCreatedByUser(User sessionUser, int pageNumber, int pageSize) {
        Page<Event> pageOfEvents =
                eventRepository.findByCreatedByUserAndEventStartDateTimeGreaterThanAndCanceled(sessionUser, new Date(), false, new PageRequest(pageNumber, pageSize));
        return pageOfEvents.getContent();
    }

    @Override
    public boolean hasUpcomingEvents(User requestingUser) {
        // likewise, if we start using this at outset of meetings menu, need to make it fast, possibly a query
        return getUpcomingEvents(requestingUser).size() != 0;
    }

    @Override
    public String[] populateNotificationFields(Event event) {
        Map<String, String> description = getEventDescription(event);

        return new String[]{
                description.get("groupName"),
                description.get("creatingUser"),
                description.get("eventSubject"),
                description.get("dateTimeString"),
                description.get("location")
        };
    }

    @Override
    public Map<String, String> getEventDescription(Event event) {
        Map<String, String> eventDescription = new HashMap<>();

        if (minimumDataAvailable(event)) {

            SimpleDateFormat sdf = new SimpleDateFormat("EEE d MMM, h:mm a");
            String dateTimeString = (event.getDateTimeString() != null) ? event.getDateTimeString() :
                    sdf.format(event.getEventStartDateTime().getTime());;

            eventDescription.put("minimumData", "true");
            eventDescription.put("groupName", event.getAppliesToGroup().getName(""));
            eventDescription.put("creatingUser", event.getCreatedByUser().nameToDisplay());
            eventDescription.put("eventSubject", event.getName());
            eventDescription.put("location", event.getEventLocation());
            eventDescription.put("createdDateTime", event.getCreatedDateTime().toString());
            eventDescription.put("dateTimeString", dateTimeString);
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
        return (!event.isIncludeSubGroups()) ? event.getAppliesToGroup().getGroupMembers().size() :
                groupManager.getAllUsersInGroupAndSubGroups(event.getAppliesToGroup()).size();
    }

    @Override
    public double getCostOfMessages(Event event, double costPerMessage) {
        // this will also get more sophisticated, and important, as message sending methods multiply, accounts become real, etc
        return getNumberInvitees(event) * costPerMessage;
    }

    @Override
    public double getCostOfMessages(Event event) {
        return getCostOfMessages(event, SMS_COST);
    }

    @Override
    public Long getNextOutstandingVote(User sessionUser) {
        // todo: rapid check that this will not return null (current use cases are safe, future might not be)
        return getOutstandingVotesForUser(sessionUser).get(0).getId();
    }

    @Override
    public Map<String, Integer> getVoteResults(Event vote) {

        Map<String, Integer> results = new HashMap<>();
        RSVPTotalsDTO totalsDTO = eventLogManagementService.getVoteResultsForEvent(vote);

        results.put("yes", totalsDTO.getYes());
        results.put("no", totalsDTO.getNo());
        results.put("abstained", totalsDTO.getNumberOfUsers() - totalsDTO.getYes() - totalsDTO.getNo());
        results.put("possible", totalsDTO.getNumberOfUsers());

        return results;
    }

    @Override
    public Integer getNumberYesVotes(Event vote) {
        return getVoteResults(vote).get("yes");
    }

    @Override
    public Integer getNumberNoVotes(Event vote) {
        return getVoteResults(vote).get("no");
    }

    @Override
    public Integer getNumberAbstained(Event vote) {
        return getVoteResults(vote).get("abstained");
    }

    @Override
    public Integer getNumberTotalVotes(Event vote) {
        return getNumberYesVotes(vote) + getNumberNoVotes(vote);
    }

    @Override
    public Integer getTotalPossibleVotes(Event vote) {
        return getVoteResults(vote).get("possible");
    }

    /*
    If message is blank then the reminder generated by the template will be used
     */
    @Override
    public boolean sendManualReminder(Event event, String message) {

        EventDTO eventDTO = new EventDTO(event);
        eventDTO.setMessage(message);
        jmsTemplateProducerService.sendWithNoReply("manual-reminder", eventDTO);
        log.info("sendManualReminder...queued..." + eventDTO.toString());
        return true;
    }

    @Override
    public String getReminderMessageForConfirmation(String locale, User user, Event event) {
        log.info("Composing reminder message ... with locale ... " + locale);
        EventDTO eventDTO = new EventDTO(event);
        return meetingNotificationService.createMeetingReminderMessage(locale, user, eventDTO);
    }

    @Override
    public String getDefaultLocaleReminderMessage(User user, Event event) {
        return getReminderMessageForConfirmation("en", user, event);
    }

    private Event saveandCheckChanges(EventDTO beforeEvent, Event changedEvent) {

        log.info("saveandCheckChanges...starting ... with before event .. " + beforeEvent.toString());
        log.info("saveandCheckChanges...starting ... with changed event ..." + changedEvent.toString());
        log.info("saveandCheckChanges...changedEvent.id..." + changedEvent.getId());

        boolean priorEventComplete = minimumDataAvailable(beforeEvent);
        Event savedEvent = eventRepository.save(changedEvent);

        log.info("saveandCheckChanges...savedEvent..." + savedEvent.toString());

        log.info("saveandCheckChanges ... values of some key paramaters ... priorEventComplete=" + priorEventComplete
                + "; savedEventComplete=" + minimumDataAvailable(savedEvent) + "; cancelled flages (beforeEvent / savedEvent: "
                + beforeEvent.isCanceled() + " / " + savedEvent.isCanceled());

        /*
        Check if we need to send meeting notifications
         */

        if (!priorEventComplete && minimumDataAvailable(savedEvent) && !savedEvent.isCanceled()) {
            jmsTemplateProducerService.sendWithNoReply("event-added", new EventDTO(savedEvent));
            log.info("queued to event-added..." + savedEvent.getId() + "...version..." + savedEvent.getVersion());
            //todo do the same for changes???
            //clear the users cache so that they can pickup the new event
            cacheUtilService.clearRsvpCacheForUser(savedEvent.getCreatedByUser(),savedEvent.getEventType());
            jmsTemplateProducerService.sendWithNoReply("clear-groupcache",new EventDTO(savedEvent));

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
        if (event.getAppliesToGroup() == null) minimum = false;
        if (event.getCreatedByUser() == null) minimum = false;
        if (event.getEventStartDateTime() == null) minimum = false;
        if (event.getEventType() != EventType.Vote) {
            if (event.getEventLocation() == null || event.getEventLocation().trim().equals("")) minimum = false;
        }
        log.info("minimumDataAvailable...returning..." + minimum);

        return minimum;
    }

    private boolean minimumDataAvailable(EventDTO event) {
        boolean minimum = true;
        log.finest("minimumDataAvailable..." + event.toString());
        if (event.getName() == null || event.getName().trim().equals("")) minimum = false;
        if (event.getAppliesToGroup() == null) minimum = false;
        if (event.getCreatedByUser() == null) minimum = false;
        if (event.getEventStartDateTime() == null) minimum = false;

        if (event.getEventType() != EventType.Vote) {
            if (event.getEventLocation() == null || event.getEventLocation().trim().equals("")) minimum = false;

        }
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

        if (passedEvent.getReminderMinutes() == 0 && savedEvent.getReminderMinutes() != 0)
            passedEvent.setReminderMinutes(savedEvent.getReminderMinutes());

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

        log.info("Inside applyChangesToEntity, with passed event: " + passedEvent);

        if (passedEvent.getId() != savedEvent.getId()) {
            return null;
        }

        if (passedEvent.getName() != null) savedEvent.setName(passedEvent.getName());

        if (passedEvent.getEventLocation() != null) savedEvent.setEventLocation(passedEvent.getEventLocation());

        if (passedEvent.getEventStartDateTime() != null)
            savedEvent.setEventStartDateTime(passedEvent.getEventStartDateTime());

        if (passedEvent.getDateTimeString() != null) savedEvent.setDateTimeString(passedEvent.getDateTimeString());

        if (passedEvent.isRsvpRequired() != savedEvent.isRsvpRequired())
            savedEvent.setRsvpRequired(passedEvent.isRsvpRequired());

        if (passedEvent.isIncludeSubGroups() != savedEvent.isIncludeSubGroups())
            savedEvent.setIncludeSubGroups(passedEvent.isIncludeSubGroups());

        // have to do a prior check for group default number of minutes, else caught between null pointer exception and always eval true

        int groupReminderMinutes = (savedEvent.getAppliesToGroup() != null) ? savedEvent.getAppliesToGroup().getReminderMinutes() : SITE_REMINDERMINUTES;
        if (passedEvent.getReminderMinutes() != SITE_REMINDERMINUTES && (passedEvent.getReminderMinutes() != groupReminderMinutes))
            savedEvent.setReminderMinutes(passedEvent.getReminderMinutes());

        //if (passedEvent.getAppliesToGroup() != null) savedEvent.setAppliesToGroup(passedEvent.getAppliesToGroup());
        //if (passedEvent.getCreatedByUser() != null) savedEvent.setCreatedByUser(passedEvent.getCreatedByUser());
        //if (passedEvent.getCreatedDateTime() != null) savedEvent.setCreatedDateTime(passedEvent.getCreatedDateTime());
        //if (passedEvent.getEventType() != null) savedEvent.setEventType(passedEvent.getEventType());

        /*
        We do not touch the two boolean fields, rsvpRequired and includeSubGroups, since those are set by
        default in the constructors, and if passedEvent has them set to something different to savedEvent,
         that means the user changed them, and hence they should be preserved. To test this.
         */

        log.info("Exiting applyChangesToEntity, with saved event: " + savedEvent);

        return savedEvent;

    }

}
