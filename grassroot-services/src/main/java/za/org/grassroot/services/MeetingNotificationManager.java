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
        //TODO store the user prefered language as a locale string ie en_ZA, af_ZA etc...
        //     and pass it to the getMessage function as a Locale from the User entity
        map.put("meetinginvite",applicationContext.getMessage("meeting.invite", null, Locale.ENGLISH));
        map.put("meetingmeeting",applicationContext.getMessage("meeting.meeting", null, Locale.ENGLISH));
        map.put("meetingby",applicationContext.getMessage("meeting.by", null, Locale.ENGLISH));
        map.put("eventname",event.getName());
        map.put("username",(user.getDisplayName() == null) ? user.getPhoneNumber() : user.getDisplayName());
        return createMessageUsingVelocity("meeting-notification.vm",map);
    }
}
