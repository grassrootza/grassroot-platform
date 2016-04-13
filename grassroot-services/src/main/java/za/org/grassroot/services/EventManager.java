package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.async.GenericJmsTemplateProducerService;
import za.org.grassroot.services.util.CacheUtilService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;

/**
 * Created by aakilomar on 8/20/15.
 */
@Component
public class EventManager implements EventManagementService {

    private Logger log = LoggerFactory.getLogger(EventManager.class);

    //TODO aakil move this to the properties file as soon as you get the property injection to work
    private final int SITE_REMINDERMINUTES = 1440; // 24hours

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    GroupBroker groupBroker;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GenericJmsTemplateProducerService jmsTemplateProducerService;

    @Autowired
    EventLogManagementService eventLogManagementService;


    @Autowired
    CacheUtilService cacheUtilService;

    @Autowired
    MeetingNotificationService meetingNotificationService;

    @Autowired
    GroupLogRepository groupLogRepository;

    private final static double SMS_COST = 0.2; // might move to message services

    @Override
    public Event getMostRecentEvent(Group group) {
        return eventRepository.findTopByAppliesToGroupAndEventStartDateTimeNotNullOrderByEventStartDateTimeDesc(group);
    }

    @Override
    public Map<String, Integer> getMeetingRsvpTotals(Event meeting) {
        Map<String, Integer> results = new HashMap<>();
        ResponseTotalsDTO totalsDTO = eventLogManagementService.getResponseCountForEvent(meeting);

        results.put("yes", totalsDTO.getYes());
        results.put("no", totalsDTO.getNo());
        results.put("no_answer", totalsDTO.getNumberOfUsers() - totalsDTO.getYes() - totalsDTO.getNo());

        return results;
    }

    @Override
    public List<Meeting> getUpcomingMeetings(Group group) {
        return meetingRepository.findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceled(group, Instant.now(), false);
    }

    @Override
    public List<Vote> getUpcomingVotes(Group group) {
        return voteRepository.findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceled(group, Instant.now(), false);
    }

    @Override
    public List<Event> getUpcomingEvents(Group group) {
        return eventRepository.findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceled(group, Instant.now(), false);
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

        for (User user : event.resolveGroup().getMembers())
            rsvpResponses.put(user, EventRSVPResponse.NO_RESPONSE);

        for (User user : getListOfUsersThatRSVPYesForEvent(event))
            rsvpResponses.replace(user, EventRSVPResponse.YES);

        for (User user : getListOfUsersThatRSVPNoForEvent(event))
            rsvpResponses.replace(user, EventRSVPResponse.NO);

        return rsvpResponses;
    }

    @Override
    public int countUpcomingEvents(User user) {
        return eventRepository.countByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThan(user, Instant.now());
    }


    @Override
    public List<Event> getOutstandingVotesForUser(User user) {
        return getOutstandingResponseForUser(user, EventType.VOTE);
    }

    @Override
    public List<Event> getOutstandingRSVPForUser(User user) {
        return getOutstandingResponseForUser(user, EventType.MEETING);
    }

    private List<Event> getOutstandingResponseForUser(User user, EventType eventType) {
        log.info("getOutstandingResponseForUser..." + user.getPhoneNumber() + "...type..." + eventType.toString());

        List<Event> outstandingRSVPs = cacheUtilService.getOutstandingResponseForUser(user, eventType);

        if (outstandingRSVPs == null) {
            // fetch from the database
            Map<Long, Long> eventMap = new HashMap<>();
            outstandingRSVPs = new ArrayList<>();
            List<Group> groups = groupRepository.findByMembershipsUserAndActive(user, true);
            log.debug("getOutstandingResponseForUser...after...getGroupsPartOf...");

            // major todo: must be a more elegant way to do this, e.g., via a single query
            if (groups != null) {
                log.debug("getOutstandingResponseForUser...number of groups..." + groups.size());
                for (Group group : groups) {
                    log.debug("getOutstandingResponseForUser...before...getUpcomingEventsForGroupAndParentGroups..." + group.getId());
                    List<Event> upcomingEvents = getUpcomingEventsForGroupAndParentGroups(group);
                    log.debug("getOutstandingResponseForUser...after...getUpcomingEventsForGroupAndParentGroups..." + group.getId());

                    if (upcomingEvents != null) {
                        for (Event event : upcomingEvents) {
                            log.debug("getOutstandingResponseForUser...start...event check..." + event.getId());

                            if (event.isRsvpRequired() && event.getEventType() == eventType) {
                                if ((eventType == EventType.MEETING && event.getCreatedByUser().getId() != user.getId())
                                        || eventType != EventType.MEETING) {

                                    //N.B. remove this if statement if you want to allow votes for people that joined the group late
                                    if (eventType == EventType.VOTE) {
                                        Instant joined = groupLogRepository.getGroupJoinedDate(group.getId(), user.getId());
                                        if (joined != null && joined.isAfter(event.getCreatedDateTime())) {
                                            log.info(String.format("Excluding vote %s for %s as the user joined group %s after the vote was called", event.getName(), user.getPhoneNumber(), group.getId()));
                                            continue;
                                        }
                                    }
                                    if (!eventLogManagementService.userRsvpForEvent(event, user)) {
                                        //see if we added it already as the user can be in multiple groups in a group structure
                                        if (eventMap.get(event.getId()) == null) {
                                            outstandingRSVPs.add(event);
                                            eventMap.put(event.getId(), event.getId());
                                        }
                                        log.info("getOutstandingResponseForUser..." + eventType.toString() + " Required..." + user.getPhoneNumber() + "...event..." + event.getId());
                                    } else {
                                        log.info("getOutstandingResponseForUser..." + eventType.toString() + " NOT Required..." + user.getPhoneNumber() + "...event..." + event.getId());

                                    }
                                }
                            } else {
                                log.debug("getOutstandingResponseForUser...start...event check..." + event.getId() + "...NOT matching on eventtype..." + event.getEventType().toString() + "... or RSVP required..." + event.isRsvpRequired());

                            }

                            log.debug("getOutstandingResponseForUser...end...event check..." + event.getId());

                        }
                    }

                }
                cacheUtilService.putOutstandingResponseForUser(user, eventType, outstandingRSVPs);
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
        List<Group> parentGroups = groupBroker.parentChain(group.getUid());

        if (parentGroups != null) {
            for (Group parentGroup : parentGroups) {
                log.debug("parentGroup..." + parentGroup.getId());
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
    public List<Event> getUpcomingEvents(User requestingUser, EventType type) {
        // todo: at some point we will need this to be efficient ... for now, doing a very slow kludge, and going to avoid using the method
        if (type.equals(EventType.MEETING)) {
            return (List) meetingRepository.findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceled
                    (requestingUser, Instant.now(), false);
        } else {
            return (List) voteRepository.findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceled
                    (requestingUser, Instant.now(), false);
        }
    }

    @Override
    public List<Event> getPaginatedEventsCreatedByUser(User sessionUser, int pageNumber, int pageSize) {
        Page<Event> pageOfEvents = eventRepository.
                findByCreatedByUserAndEventStartDateTimeGreaterThanAndCanceled(sessionUser, Instant.now(), false, new PageRequest(pageNumber, pageSize));
        return pageOfEvents.getContent();
    }

    @Override
    public int userHasEventsToView(User user, EventType type) {
        // todo: this is three DB pings, less expensive than prior iterations over groups, but still expensive, replace with query
        log.info("Checking what events the user has to view ... ");
        if (!userHasEventsToView(user, type, false)) return -9;
        int returnFlag = 0;
        returnFlag -= userHasPastEventsToView(user, type) ? 1 : 0;
        returnFlag += userHasFutureEventsToView(user, type) ? 1 : 0;
        return returnFlag;

    }

    @Override
    public boolean userHasEventsToView(User user, EventType type, boolean upcomingOnly) {
        // todo: this may be _very_ expensive if Hibernate is looping through lists, replace with a query pronto
        log.info("Checking on the repository ... for event type: {}", type.toString());
        if (upcomingOnly) {
            return userHasFutureEventsToView(user, type);
        } else {
            if (type.equals(EventType.MEETING)) {
                return !meetingRepository.findByAppliesToGroupMembershipsUserAndCanceledOrderByEventStartDateTimeDesc(user, false).isEmpty();
            } else {
                log.info("Looking on vote repository, for this user: {}", user);
                return !voteRepository.findByAppliesToGroupMembershipsUserAndCanceledOrderByEventStartDateTimeDesc(user, false).isEmpty();
            }
        }
    }

    @Override
    public boolean userHasPastEventsToView(User user, EventType type) {
        // todo: in future performance tweaking, may turn this into a direct count query
        if (type.equals(EventType.MEETING)) {
            return !meetingRepository.
                    findByAppliesToGroupMembershipsUserAndEventStartDateTimeLessThanAndCanceled(user, Instant.now(), false).
                    isEmpty();
        } else {
            return !voteRepository.
                    findByAppliesToGroupMembershipsUserAndEventStartDateTimeLessThanAndCanceled(user, Instant.now(), false).
                    isEmpty();
        }
    }

    @Override
    public boolean userHasFutureEventsToView(User user, EventType type) {
        log.info("Checking if user has future events to view, of type: {}", type);
        if (type.equals(EventType.MEETING)) {
            List<Meeting> events = meetingRepository.findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceled(user, Instant.now(), false);
            log.info("List of events returned, with size={}, hence returning={}", events.size(), !events.isEmpty());
            return !events.isEmpty();
        } else {
            return !voteRepository.
                    findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceled(user, Instant.now(), false).
                    isEmpty();
        }
    }

    @Override
    public Page<Event> getEventsUserCanView(User user, EventType type, int pastPresentOrBoth, int pageNumber, int pageSize) {
        // todo: filter for permissions, maybe
        if (pastPresentOrBoth == -1) {
            if (type.equals(EventType.MEETING)) {
                return (Page) meetingRepository.findByAppliesToGroupMembershipsUserAndEventStartDateTimeLessThanAndCanceledOrderByEventStartDateTimeDesc(
                        user, Instant.now(), false, new PageRequest(pageNumber, pageSize));
            } else {
                return (Page) voteRepository.findByAppliesToGroupMembershipsUserAndEventStartDateTimeLessThanAndCanceledOrderByEventStartDateTimeDesc(
                        user, Instant.now(), false, new PageRequest(pageNumber, pageSize));
            }

        } else if (pastPresentOrBoth == 1) {
            if (type.equals(EventType.MEETING)) {
                return (Page) meetingRepository.findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledOrderByEventStartDateTimeDesc(
                        user, Instant.now(), false, new PageRequest(pageNumber, pageSize));
            } else {
                return (Page) voteRepository.findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledOrderByEventStartDateTimeDesc(
                        user, Instant.now(), false, new PageRequest(pageNumber, pageSize));
            }

        } else {
            // todo: think about setting a lower bound (e.g., one year ago)
            if (type.equals(EventType.MEETING)) {
                return (Page) meetingRepository.findByAppliesToGroupMembershipsUserAndCanceledOrderByEventStartDateTimeDesc(
                        user, false, new PageRequest(pageNumber, pageSize));
            } else {
                return (Page) voteRepository.findByAppliesToGroupMembershipsUserAndCanceledOrderByEventStartDateTimeDesc(
                        user, false, new PageRequest(pageNumber, pageSize));
            }
        }
    }

    @Override
    public int getNumberInvitees(Event event) {
        // may make this more sophisticated once we have message relays in place, also, switch to using parent
        return (!event.isIncludeSubGroups()) ? event.resolveGroup().getMembers().size() :
                userManagementService.fetchByGroup(event.resolveGroup().getUid(), true).size();
    }

    @Override
    public Long getNextOutstandingVote(User sessionUser) {
        // todo: rapid check that this will not return null (current use cases are safe, future might not be)
        return getOutstandingVotesForUser(sessionUser).get(0).getId();
    }

    @Override
    public ResponseTotalsDTO getVoteResultsDTO(Event vote) {
        return eventLogManagementService.getVoteResultsForEvent(vote);
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

    @Override
    public List<Event> getGroupEventsInPeriod(Group group, LocalDateTime periodStart, LocalDateTime periodEnd) {
        Sort sort = new Sort(Sort.Direction.ASC, "EventStartDateTime");
        Instant start = convertToSystemTime(periodStart, DateTimeUtil.getSAST());
        Instant end = convertToSystemTime(periodEnd, DateTimeUtil.getSAST());
        return eventRepository.findByAppliesToGroupAndEventStartDateTimeBetween(group, start, end, sort);
    }

    /**
     * SECTION:
     */


    @Override
    public List<Event> getEventsForGroupInTimePeriod(Group group, EventType eventType, LocalDateTime periodStart, LocalDateTime periodEnd) {
        Instant start = convertToSystemTime(periodStart, DateTimeUtil.getSAST());
        Instant end = convertToSystemTime(periodEnd, DateTimeUtil.getSAST());
        if (eventType.equals(EventType.MEETING)) {
            return (List) meetingRepository.findByAppliesToGroupAndEventStartDateTimeBetween(group, start, end);
        } else {
            return (List) voteRepository.findByAppliesToGroupAndEventStartDateTimeBetween(group, start, end);
        }
    }

    @Override
    public double getCostOfMessagesForEvent(Event event, double costPerMessage) {
        return eventLogManagementService.countNonRSVPEventLogsForEvent(event) * costPerMessage;
    }

    @Override
    public double getCostOfMessagesDefault(Event event) {
        return getCostOfMessagesForEvent(event, SMS_COST);
    }

    /*@Override
    public double getTotalCostGroupInPeriod(Group group, LocalDateTime periodStart, LocalDateTime periodEnd) {
        // todo: a repository method that doesn't bother with event type ...
        Sort sort = new Sort(Sort.Direction.ASC, "EventStartDateTime");
        List<Event> events = eventRepository.findByAppliesToGroupAndEventStartDateTimeBetween(group, Timestamp.valueOf(periodStart),
                Timestamp.valueOf(periodEnd), sort);
        double costCounter = 0;
        for (Event event : events)
            costCounter += getCostOfMessagesDefault(event);
        return costCounter;
    }*/

    @Override
    public int notifyUnableToProcessEventReply(User user) {
        jmsTemplateProducerService.sendWithNoReply("processing-failure", user);
        return 0;
    }

}
