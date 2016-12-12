package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.EventReminderType;

/**
 * Created by luke on 2016/04/06.
 */
public class MeetingWrapper extends EventWrapper {

    private String location;

    private MeetingWrapper() {
    }

    public static MeetingWrapper makeEmpty(EventReminderType reminderType, int customReminderMinutes, boolean rsvpRequired) {
        MeetingWrapper meetingWrapper = new MeetingWrapper();
        meetingWrapper.reminderType = reminderType;
        meetingWrapper.customReminderMinutes = customReminderMinutes;
        return meetingWrapper;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MeetingWrapper{");
        sb.append("entityUid='").append(entityUid).append('\'');
        sb.append("parentUid='").append(parentUid).append('\'');
        sb.append("parentType='").append(parentEntityType).append('\'');
        sb.append("title='").append(title).append('\'');
        sb.append("description='").append(description).append('\'');
        sb.append("datetime='").append(eventDateTime).append('\'');
        sb.append("location='").append(location).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
