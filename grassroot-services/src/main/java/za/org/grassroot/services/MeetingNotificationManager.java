package za.org.grassroot.services;

import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.ui.velocity.VelocityEngineUtils;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 8/24/15.
 */
@Component
public class MeetingNotificationManager implements MeetingNotificationService {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());


    @Autowired
    VelocityEngine velocityEngine;

    @Autowired
    ApplicationContext applicationContext;

    @Override
    public String createMessageUsingVelocity(String template, Map<String, Object> model) {
        return  VelocityEngineUtils.mergeTemplateIntoString(this.velocityEngine,
                template, "UTF-8", model);
    }

    @Override
    public String createMeetingNotificationMessage(User user, Event event) {
        Map<String,Object> map = new HashMap<>();
        //TODO rework this so that different language templates can be used
        //     and pass it to the getMessage function as a Locale from the User entity
        Locale locale = getUserLocale(user);
        map.put("meetinginvite", applicationContext.getMessage("meeting.invite", null, locale));
        map.put("meetingmeeting",applicationContext.getMessage("meeting.meeting", null, locale));
        map.put("meetingby",applicationContext.getMessage("meeting.by", null, locale));
        map.put("meetingat",applicationContext.getMessage("meeting.at", null,  locale));
        map.put("eventname",event.getName());
        map.put("location",event.getEventLocation());
        map.put("username", (event.getCreatedByUser().getDisplayName() == null) ? event.getCreatedByUser().getPhoneNumber() : event.getCreatedByUser().getDisplayName());
        return createMessageUsingVelocity("meeting-notification.vm",map);
    }

    private Locale getUserLocale(User user) {
        if (user.getLanguageCode() == null || user.getLanguageCode().trim().equals("")) {
            return Locale.ENGLISH;
        } else {
            return new Locale(user.getLanguageCode());
        }

    }
}
