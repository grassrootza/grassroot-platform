package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.services.EventManagementService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 10/24/15.
 */
@RestController
@RequestMapping("/api/vote")
public class VoteRestController {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    EventRepository eventRepository;

    @RequestMapping(value = "/listallfuture", method = RequestMethod.GET)
    public List<EventDTO> listAllFuture() {
        List<EventDTO> list = new ArrayList<>();
        List<Event> eventList = eventRepository.findAllVotesAfterTimeStamp(new Date());
        for (Event event : eventList) {
            list.add(new EventDTO(event));
        }
        return list;
    }

    @RequestMapping(value = "/add/{userId}/{groupId}/{issue}", method = RequestMethod.POST)
    public za.org.grassroot.webapp.model.rest.EventDTO add
            (@PathVariable("userId") Long userId, @PathVariable("groupId") Long groupId,
             @PathVariable("issue") String issue) {
        return addAndSetSubGroups(userId, groupId, issue, false);

    }

    @RequestMapping(value = "/add/{userId}/{groupId}/{issue}/{includeSubGroups}", method = RequestMethod.POST)
    public za.org.grassroot.webapp.model.rest.EventDTO addAndSetSubGroups(@PathVariable("userId") Long userId,@PathVariable("groupId") Long groupId,
                                                                          @PathVariable("issue") String issue, @PathVariable("includeSubGroups") boolean includeSubGroups) {
        return new za.org.grassroot.webapp.model.rest.EventDTO(eventManagementService.createVote(issue, userId, groupId, includeSubGroups));

    }

}
