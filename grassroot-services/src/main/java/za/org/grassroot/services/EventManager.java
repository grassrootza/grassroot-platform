package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.EventRepository;

/**
 * Created by aakilomar on 8/20/15.
 */
@Component
public class EventManager implements EventManagementService {

    @Autowired
    EventRepository  eventRepository;

    /*
    This createEvent method is used primarily by the USSD interface, where we do not have all the information yet.
    At this stage we would have created the user, group and asked the Name of the Event
     */
    @Override
    public Event createEvent(String name, User createdByUser, Group appliesToGroup) {
        return eventRepository.save(new Event(name,createdByUser,appliesToGroup));
    }
}
