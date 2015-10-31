package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.webapp.model.rest.EventDTO;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by F5203783 on 2015/09/03.
 */
@RestController
@RequestMapping("/api/event")
public class EventRestController {

    Logger log = Logger.getLogger(getClass().getCanonicalName());

    @Autowired
    EventRepository eventRepository;

    @Autowired
    EventManagementService eventManagementService;



    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public List<Event> list() {
        return (List<Event>) eventRepository.findAll();
    }

    @RequestMapping(value = "/upcoming/meeting/{groupId}", method = RequestMethod.GET)
    public List<EventDTO> getUpcomingMeetings(@PathVariable("groupId") Long groupId) {
        List<Event> upcomingList = eventManagementService.getUpcomingMeetings(groupId);
        List<EventDTO> list = new ArrayList<>();
        for (Event event : upcomingList) {
            list.add(new EventDTO(event));
        }
        return list;

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
    public EventDTO add(@PathVariable("userId") Long userId,@PathVariable("groupId") Long groupId,
                     @PathVariable("name") String name) {
        return addAndSetSubGroups(userId,groupId,name,false);

    }

    @RequestMapping(value = "/add/{userId}/{groupId}/{name}/{includeSubGroups}", method = RequestMethod.POST)
    public EventDTO addAndSetSubGroups(@PathVariable("userId") Long userId,@PathVariable("groupId") Long groupId,
                        @PathVariable("name") String name, @PathVariable("includeSubGroups") boolean includeSubGroups) {
        return new EventDTO(eventManagementService.createEvent(name,userId,groupId,includeSubGroups));

    }

    @RequestMapping(value = "/setlocation/{eventId}/{location}", method = RequestMethod.POST)
    public EventDTO setLocation(@PathVariable("eventId") Long eventId,@PathVariable("location") String location) {
        return new EventDTO(eventManagementService.setLocation(eventId, location));

    }

    /* @RequestMapping(value = "/setday/{eventId}/{day}", method = RequestMethod.POST)
    public EventDTO setDay(@PathVariable("eventId") Long eventId,@PathVariable("day") String day) {
        return new EventDTO(eventManagementService.setDay(eventId, day));
    }*/

    @RequestMapping(value = "/settime/{eventId}/{time}", method = RequestMethod.POST)
    public EventDTO setTime(@PathVariable("eventId") Long eventId,@PathVariable("time") String time) {
        //TODO this is very inefficient and should be refactored, it is just how Luke implemented it currently for USSD
        eventManagementService.setEventTimestamp(eventId, Timestamp.valueOf(DateTimeUtil.parseDateTime(time)));
        return new EventDTO(eventManagementService.setDateTimeString(eventId,time));
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


}
