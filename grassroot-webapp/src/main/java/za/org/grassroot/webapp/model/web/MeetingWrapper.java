package za.org.grassroot.webapp.model.web;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.task.EventReminderType;
import za.org.grassroot.core.enums.MeetingImportance;
import za.org.grassroot.webapp.enums.EntityPublicOption;

/**
 * Created by luke on 2016/04/06.
 */
public class MeetingWrapper extends EventWrapper {

    private String location;
    private MeetingImportance importance;
    private EntityPublicOption publicOption;
    private Double latitude;
    private Double longitude;

    private MultipartFile meetingImage;

    private MeetingWrapper() {
    }

    public static MeetingWrapper makeEmpty(EventReminderType reminderType, int customReminderMinutes) {
        MeetingWrapper meetingWrapper = new MeetingWrapper();
        meetingWrapper.reminderType = reminderType;
        meetingWrapper.customReminderMinutes = customReminderMinutes;
        meetingWrapper.importance = MeetingImportance.ORDINARY;
        meetingWrapper.publicOption = EntityPublicOption.PRIVATE;
        return meetingWrapper;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public MeetingImportance getImportance() {
        return importance;
    }

    public void setImportance(MeetingImportance importance) {
        this.importance = importance;
    }

    public EntityPublicOption getPublicOption() {
        return publicOption;
    }

    public void setPublicOption(EntityPublicOption publicOption) {
        this.publicOption = publicOption;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public boolean hasLongLat() { return latitude != null && longitude != null; }

    public MultipartFile getMeetingImage() {
        return meetingImage;
    }

    public void setMeetingImage(MultipartFile meetingImage) {
        this.meetingImage = meetingImage;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MeetingWrapper{");
        sb.append("entityUid='").append(entityUid).append('\'');
        sb.append("parentUid='").append(parentUid).append('\'');
        sb.append("parentType='").append(parentEntityType).append('\'');
        sb.append("title='").append(title).append('\'');
        sb.append("importance='").append(importance).append('\'');
        sb.append("description='").append(description).append('\'');
        sb.append("datetime='").append(eventDateTime).append('\'');
        sb.append("location='").append(location).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
