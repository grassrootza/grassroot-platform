package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.EventDTO;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by F5203783 on 2015/09/03.
 */
@RestController
@RequestMapping("/api/event")
public class EventRestController {


    @Autowired
    EventRepository eventRepository;

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    EventLogManagementService eventLogManagementService;


    @RequestMapping(value = "/upcoming/meeting/{groupId}", method = RequestMethod.GET)
    public List<EventDTO> getUpcomingMeetings(@PathVariable("groupId") Long groupId) {
        List<Event> upcomingList = eventManagementService.getUpcomingMeetings(groupId);
        List<EventDTO> list = new ArrayList<>();
        for (Event event : upcomingList) {
            list.add(new EventDTO(event));
        }
        return list;

    }

    @RequestMapping(value = "/vote/do/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> castVote(@PathVariable("phoneNumber") String phoneNumber,
                                                    @PathVariable("code") String code, @PathVariable("id") String eventId,
                                                    @RequestParam(value = "response", required = true) String response) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Event event = eventManagementService.loadEvent(Long.parseLong(eventId));
        String trimmedResponse = response.toLowerCase().trim();
        boolean hasVoted = eventLogManagementService.userRsvpForEvent(event, user);
        ResponseWrapper responseWrapper;
        if (!hasVoted && isOpen(event)) {
            eventLogManagementService.rsvpForEvent(event, user, EventRSVPResponse.fromString(trimmedResponse));
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.VOTE_SENT, RestStatus.SUCCESS);
        } else if (hasVoted) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.USER_HAS_ALREADY_VOTED, RestStatus.FAILURE);
        } else {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.VOTE_CLOSED, RestStatus.FAILURE);
        }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }

    @RequestMapping(value = "/meeting/rsvp/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> rsvp(@PathVariable("phoneNumber") String phoneNumber,
                                                    @PathVariable("code") String code, @PathVariable("id") String eventId,
                                                    @RequestParam(value = "response", required = true) String response) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Event event = eventManagementService.loadEvent(Long.parseLong(eventId));
        String trimmedResponse = response.toLowerCase().trim();
        ResponseWrapper responseWrapper;
        if (isOpen(event)) {
            eventLogManagementService.rsvpForEvent(event, user, EventRSVPResponse.fromString(trimmedResponse));
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.RSVP_SENT, RestStatus.SUCCESS);
        } else {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.PAST_DUE, RestStatus.FAILURE);
         }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }


    @RequestMapping(value = "/upcoming/vote/{groupId}", method = RequestMethod.GET)
    public List<EventDTO> getUpcomingVotes(@PathVariable("groupId") Long groupId) {
        List<Event> upcomingList = eventManagementService.getUpcomingVotes(groupId);
        List<EventDTO> list = new ArrayList<>();
        for (Event event : upcomingList) {
            list.add(new EventDTO(event));
        }
        return list;

    }

    @RequestMapping(value = "/add/{userId}/{groupId}/{name}", method = RequestMethod.POST)
    public EventDTO add(@PathVariable("userId") Long userId, @PathVariable("groupId") Long groupId,
                        @PathVariable("name") String name) {
        return addAndSetSubGroups(userId, groupId, name, false);

    }

    @RequestMapping(value = "/add/{userId}/{groupId}/{name}/{includeSubGroups}", method = RequestMethod.POST)
    public EventDTO addAndSetSubGroups(@PathVariable("userId") Long userId, @PathVariable("groupId") Long groupId,
                                       @PathVariable("name") String name, @PathVariable("includeSubGroups") boolean includeSubGroups) {
        return new EventDTO(eventManagementService.createEvent(name, userId, groupId, includeSubGroups));

    }

    @RequestMapping(value = "/setlocation/{eventId}/{location}", method = RequestMethod.POST)
    public EventDTO setLocation(@PathVariable("eventId") Long eventId, @PathVariable("location") String location) {
        return new EventDTO(eventManagementService.setLocation(eventId, location));

    }

    /* @RequestMapping(value = "/setday/{eventId}/{day}", method = RequestMethod.POST)
    public EventDTO setDay(@PathVariable("eventId") Long eventId,@PathVariable("day") String day) {
        return new EventDTO(eventManagementService.setDay(eventId, day));
    }*/

    @RequestMapping(value = "/settime/{eventId}/{time}", method = RequestMethod.POST)
    public EventDTO setTime(@PathVariable("eventId") Long eventId, @PathVariable("time") String time) {
        //TODO this is very inefficient and should be refactored, it is just how Luke implemented it currently for USSD
        eventManagementService.setEventTimestamp(eventId, Timestamp.valueOf(DateTimeUtil.parseDateTime(time)));
        return new EventDTO(eventManagementService.setDateTimeString(eventId, time));
    }

    @RequestMapping(value = "/cancel/{eventId}", method = RequestMethod.POST)
    public EventDTO cancel(@PathVariable("eventId") Long eventId) {
        return new EventDTO(eventManagementService.cancelEvent(eventId));
    }

    @RequestMapping(value = "rsvprequired/{userId}", method = RequestMethod.GET)
    public List<EventDTO> getRsvpRequired(@PathVariable("userId") Long userId) {
        List<EventDTO> rsvpRequired = new ArrayList<EventDTO>();
        List<Event> events = eventManagementService.getOutstandingRSVPForUser(userId);
        if (events != null) {
            for (Event event : events) {
                rsvpRequired.add(new EventDTO(event));
            }
        }

        return rsvpRequired;

    }

    @RequestMapping(value = "voterequired/{userId}", method = RequestMethod.GET)
    public List<EventDTO> getVoteRequired(@PathVariable("userId") Long userId) {
        List<EventDTO> rsvpRequired = new ArrayList<EventDTO>();
        List<Event> events = eventManagementService.getOutstandingVotesForUser(userId);
        if (events != null) {
            for (Event event : events) {
                rsvpRequired.add(new EventDTO(event));
            }
        }

        return rsvpRequired;


    }

    private boolean isOpen(Event event) {
        return event.getEventStartDateTime().after(Timestamp.from(Instant.now()));
    }


}
