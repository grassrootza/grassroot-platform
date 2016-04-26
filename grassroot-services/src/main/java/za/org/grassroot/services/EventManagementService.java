package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author Lesetse Kimwaga
 * major todo: clean this up -- lots of redundant methods
 */
public interface EventManagementService {

    /*
    Methods to find and load events, first by group
     */

    List<Meeting> getUpcomingMeetings(Group group);

    List<Vote> getUpcomingVotes(Group group);

    /*
    Methods to get upcoming or prior events which user can view or manage
     */

    List<Event> getOutstandingVotesForUser(User user);

    List<Event> getOutstandingRSVPForUser(User user);

    // -1 : past events; 0: both directions; +1 : future events; -9 no events
    int userHasEventsToView(User user, EventType type);

    boolean userHasEventsToView(User user, EventType type, boolean upcomingOnly);

    boolean userHasPastEventsToView(User user, EventType type);

    boolean userHasFutureEventsToView(User user, EventType type);

    // third argument: past events = -1 ; future events = 1; both directions = 0;
    Page<Event> getEventsUserCanView(User user, EventType type, int pastPresentOrBoth, int pageNumber, int pageSize);

    public Event getMostRecentEvent(Group group);

    /*
    Methods to get important details about events, including users by RSVP response, and a descriptive hashmap of strings
     */

    Map<String, Integer> getMeetingRsvpTotals(Event meeting);

    List<User> getListOfUsersThatRSVPYesForEvent(Event event);

    List<User> getListOfUsersThatRSVPNoForEvent(Event event);

    Map<User, EventRSVPResponse> getRSVPResponses(Event event);

    int countUpcomingEvents(User user);

    int getNumberInvitees(Event event);

    /*
    Methods to retrieve information about votes
     */

    Long getNextOutstandingVote(User sessionUser);

    ResponseTotalsDTO getVoteResultsDTO(Event vote);

    /*
    Methods to retrive information used for account pages
    todo: handle sub-groups
     */

    List<Event> getGroupEventsInPeriod(Group group, LocalDateTime periodStart, LocalDateTime periodEnd);

    List<Event> getEventsForGroupInTimePeriod(Group group, EventType eventType, LocalDateTime periodStart, LocalDateTime periodEnd);

    int notifyUnableToProcessEventReply(User user);


}
