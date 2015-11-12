package za.org.grassroot.services;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventDTO;

import java.util.Map;

/**
 * Created by aakilomar on 8/24/15.
 */
public interface MeetingNotificationService {

    public String createMeetingNotificationMessage(User user, EventDTO event);

    public String createChangeMeetingNotificationMessage(User user, EventDTO event);

    public String createCancelMeetingNotificationMessage(User user, EventDTO event);

    public String createMeetingReminderMessage(User user, EventDTO event);

    public String createVoteResultsMessage(User user, EventDTO event, double yes, double no, double abstain, double noReply);
}
