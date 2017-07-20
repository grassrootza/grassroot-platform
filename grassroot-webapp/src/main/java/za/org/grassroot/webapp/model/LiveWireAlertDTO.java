package za.org.grassroot.webapp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.enums.LiveWireAlertDestType;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.util.PhoneNumberUtil;

import java.util.List;

/**
 * Created by luke on 2017/05/13.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiveWireAlertDTO {

    private String uid;
    private Long creationTimeMillis;
    private String creatingUserName;
    private String creatingUserPhone;
    private String contactUserName;
    private String contactUserPhone;
    private String description;

    private LiveWireAlertType type;

    private String meetingName;
    private Long meetingTimeMillis;
    private String meetingLocation;

    private String groupName;
    private int groupSize;
    private Long groupCreationMillis;
    private int groupTasks;

    private List<String> tags;
    private boolean reviewed;
    private boolean sent;

    private LiveWireAlertDestType destType;

    public LiveWireAlertDTO(LiveWireAlert alert) {
        this.uid = alert.getUid();
        this.creationTimeMillis = alert.getCreationTime().toEpochMilli();
        this.creatingUserName = alert.getCreatingUser().getName();
        this.creatingUserPhone = PhoneNumberUtil.invertPhoneNumber(alert.getCreatingUser().getPhoneNumber());
        this.contactUserName = StringUtils.isEmpty(alert.getContactName()) ? alert.getContactName() :
                alert.getContactUser().getName();
        this.contactUserPhone = PhoneNumberUtil.invertPhoneNumber(alert.getContactUser().getPhoneNumber());
        this.description = alert.getDescription();
        this.type = alert.getType();
        this.destType = alert.getDestinationType();
        this.tags = alert.getTagList();

        Group group;
        if (LiveWireAlertType.MEETING.equals(this.type)) {
            this.meetingName = alert.getMeeting().getName();
            this.meetingTimeMillis = alert.getMeeting().getEventStartDateTime().toEpochMilli();
            this.meetingLocation = alert.getMeeting().getEventLocation();
            group = alert.getMeeting().getAncestorGroup();
        } else {
            group = alert.getGroup();
        }

        this.groupName = group.getName();
        this.groupSize = group.getMemberships().size();
        this.groupCreationMillis = group.getCreatedDateTime().toEpochMilli();
        this.groupTasks = group.getDescendantEvents().size() + group.getDescendantTodos().size();

        this.reviewed = alert.isReviewed();
        this.sent = alert.isSent();
    }

    public String getUid() { return uid; }

    public String getCreatingUserName() {
        return creatingUserName;
    }

    public String getCreatingUserPhone() {
        return creatingUserPhone;
    }

    public String getContactUserName() {
        return contactUserName;
    }

    public String getContactUserPhone() {
        return contactUserPhone;
    }

    public String getDescription() {
        return description;
    }

    public LiveWireAlertType getType() {
        return type;
    }

    public LiveWireAlertDestType getDestType() { return destType; }

    public String getMeetingName() {
        return meetingName;
    }

    public String getGroupName() {
        return groupName;
    }

    public int getGroupSize() {
        return groupSize;
    }

    public int getGroupTasks() {
        return groupTasks;
    }

    public List<String> getTags() {
        return tags;
    }

    public Long getCreationTimeMillis() {
        return creationTimeMillis;
    }

    public Long getMeetingTimeMillis() {
        return meetingTimeMillis;
    }

    public String getMeetingLocation() { return meetingLocation; }

    public Long getGroupCreationMillis() {
        return groupCreationMillis;
    }

    public boolean isReviewed() { return reviewed; }

    public boolean isSent() { return sent; }
}
