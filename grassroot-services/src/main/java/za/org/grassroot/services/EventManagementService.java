package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Lesetse Kimwaga
 * major todo: clean this up -- lots of redundant methods
 */
public interface EventManagementService {

    /*
    Methods to create events, in various different forms
     */

    Event createEvent(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups);

    Event createEvent(String name, Long createdByUserId, Long appliesToGroupId, boolean includeSubGroups);

    Event createMeeting(User createdByUser);

    Event createMeeting(String inputNumber, Long groupId);

    Event createMeeting(User createdByUser, Long groupId);

    Event createVote(String issue, User createdByUser);

    Event createVote(User createdByUser, Long groupId);

    Event createVote(String issue, Long userId, Long groupId, boolean includeSubGroups);

    // method to call we have a fully formed vote entity
    Event createVote(Event vote);

    /*
    Methods to find and load events, first by group
     */

    public Event loadEvent(Long eventId);

    List<Event> findByAppliesToGroup(Group appliesToGroup);

    List<Event> findByAppliesToGroupAndStartingAfter(Group group, Date date);

    List<Event> findUpcomingMeetingsForGroup(Group group, Date date);

    List<Event> findUpcomingVotesForGroup(Group group, Date date);

    List<Event> getUpcomingMeetings(Long groupId);

    List<Event> getUpcomingMeetings(Group group);

    List<Event> getUpcomingVotes(Long groupId);

    List<Event> getUpcomingVotes(Group group);

    List<Event> getUpcomingEvents(Group group);

    /*
    Methods to get upcoming or prior events which user can view or manage
     */

    List<Event> getOutstandingRSVPForUser(Long userId);

    List<Event> getOutstandingVotesForUser(Long userId);

    List<Event> getOutstandingVotesForUser(User user);

    List<Event> getOutstandingRSVPForUser(User user);

    List<Event> getUpcomingEventsForGroupAndParentGroups(Group group);

    List<Event> getUpcomingEventsUserCreated(User requestingUser);

    List<Event> getUpcomingEvents(User requestingUser, EventType type);

    List<Event> getPaginatedEventsCreatedByUser(User sessionUser, int pageNumber, int pageSize);

    // -1 : past events; 0: both directions; +1 : future events; -9 no events
    int userHasEventsToView(User user, EventType type);

    boolean userHasEventsToView(User user, EventType type, boolean upcomingOnly);

    boolean userHasPastEventsToView(User user, EventType type);

    boolean userHasFutureEventsToView(User user, EventType type);

    // third argument: past events = -1 ; future events = 1; both directions = 0;
    Page<Event> getEventsUserCanView(User user, EventType type, int pastPresentOrBoth, int pageNumber, int pageSize);

    /*
    Methods to set and update events
     */

    public Event setSubject(Long eventId, String subject);

    public Event setGroup(Long eventId, Long groupId);

    public Event setLocation(Long eventId, String location);

    public Event setEventTimestamp(Long eventId, Timestamp eventDateTime);

    public Event setEventReminderMinutes(Long eventId, Integer minutes);

    public Event setEventNoReminder(Long eventId);

    public Event updateEvent(Event eventToUpdate);

    public Event changeMeetingDate(Long eventId, String newDate);

    public Event changeMeetingTime(Long eventId, String newTime);

    public Event cancelEvent(Long eventId);

    public Event setSendBlock(Long eventId);

    public Event removeSendBlock(Long eventId);

    public Event getMostRecentEvent(Group group);


    /*
    Methods to get important details about events, including users by RSVP response, and a descriptive hashmap of strings
     */

    Map<String, Integer> getMeetingRsvpTotals(Event meeting);

    List<User> getListOfUsersThatRSVPYesForEvent(Event event);

    List<User> getListOfUsersThatRSVPNoForEvent(Event event);

    Map<User, EventRSVPResponse> getRSVPResponses(Event event);

    int countUpcomingEvents(Long userId);

    boolean hasUpcomingEvents(Long userId);

    String[] populateNotificationFields(Event event);

    Map<String, String> getEventDescription(Event event);

    Map<String, String> getEventDescription(Long eventId);

    int getNumberInvitees(Event event);

    String getGroupName(Event event);

    /*
    Methods to retrieve information about votes
     */

    Long getNextOutstandingVote(User sessionUser);

    Map<String, Integer> getVoteResults(Event vote);

    RSVPTotalsDTO getVoteResultsDTO(Event vote);

    /*
        If message is blank then the reminder generated by the template will be used
         */
    boolean sendManualReminder(Event event, String message);

    String getReminderMessageForConfirmation(String locale, User user, Event event);

    String getDefaultLocaleReminderMessage(User user, Event event);

    /*
    Methods to retrive information used for account pages
    todo: handle sub-groups
     */



    List<Event> getGroupEventsInPeriod(Group group, LocalDateTime periodStart, LocalDateTime periodEnd);

    List<Event> getEventsForGroupInTimePeriod(Group group, EventType eventType, LocalDateTime periodStart, LocalDateTime periodEnd);

    int countEventsForGroupInTimePeriod(Group group, EventType eventType, LocalDateTime periodStart, LocalDateTime periodEnd);

    int countNumberMessagesForGroupInTimePeriod(Group group, EventType eventType, LocalDateTime periodStart, LocalDateTime periodEnd);

    double getCostOfMessagesForEvent(Event event, double costPerMessage);

    double getCostOfMessagesDefault(Event event);

    double getCostOfMessagesForGroupInPeriod(Group group, EventType eventType, LocalDateTime periodStart, LocalDateTime periodEnd);

    double getTotalCostGroupInPeriod(Group group, LocalDateTime periodStart, LocalDateTime periodEnd);

    int notifyUnableToProcessEventReply(User user);


}
