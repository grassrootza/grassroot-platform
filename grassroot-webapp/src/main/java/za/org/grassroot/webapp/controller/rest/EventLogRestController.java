package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.dto.RSVPTotalsPerGroupDTO;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.webapp.model.rest.EventDTO;

import java.sql.Timestamp;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by F5203783 on 2015/09/03.
 */
@RestController
@RequestMapping("/api/event")
public class EventLogRestController {

    Logger log = Logger.getLogger(getClass().getCanonicalName());

    @Autowired
    EventRepository eventRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    EventLogManagementService eventLogManagementService;

    @RequestMapping(value = "/rsvp/{eventId}/{userId}/{yesNo}", method = RequestMethod.POST)
    public EventDTO rsvpForPhoneNumber(@PathVariable("eventId") Long eventId,
                         @PathVariable("userId") Long userId,
                         @PathVariable("yesNo") String yesNo) {
        return new EventDTO(eventLogManagementService.rsvpForEvent(eventId,userId,yesNo));

    }

    @RequestMapping(value = "/rsvp/totals/{eventId}", method = RequestMethod.POST)
    public RSVPTotalsDTO rsvpTotals(@PathVariable("eventId") Long eventId) {
        return eventLogManagementService.getRSVPTotalsForEvent(eventId);

    }

    @RequestMapping(value = "/rsvp/totalspergroup/{groupId}/{eventId}", method = RequestMethod.POST)
    public List<RSVPTotalsPerGroupDTO> rsvpTotals(@PathVariable("groupId") Long groupId,
                                            @PathVariable("eventId") Long eventId) {
        return eventLogManagementService.getVoteTotalsPerGroup(groupId,eventId);

    }


}
