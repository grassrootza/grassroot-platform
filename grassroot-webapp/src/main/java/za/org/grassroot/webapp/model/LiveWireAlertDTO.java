package za.org.grassroot.webapp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.enums.LiveWireAlertDestType;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.util.PhoneNumberUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by luke on 2017/05/13.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiveWireAlertDTO {

    private String serverUid;
    private String headline;
    private String description;

    private Long creationTimeMillis;
    private LiveWireAlertType alertType;

    private String creatingUserName;
    private String contactUserName;
    private String contactUserPhone;

    private String meetingName;
    private Long meetingTimeMillis;
    private String meetingLocation;

    private String ancestorGroupName;
    private int groupSize;
    private String groupUid;

    private Long groupCreationMillis;
    private int groupTasks;

    private List<String> tags;
    private boolean reviewed;
    private boolean sent;

    private Double longitude;
    private Double latitude;

    private LiveWireAlertDestType destType;

    private List<String> mediaFileUids;

    // todo : make a 'public' version that hides some details
    public LiveWireAlertDTO(LiveWireAlert alert) {
        this.serverUid = alert.getUid();
        this.headline = alert.getHeadline();
        this.creationTimeMillis = alert.getCreationTime().toEpochMilli();
        this.creatingUserName = alert.getCreatingUser().getName();
        this.contactUserName = StringUtils.isEmpty(alert.getContactName()) ? alert.getContactName() :
                alert.getContactUser().getName();
        this.contactUserPhone = PhoneNumberUtil.invertPhoneNumber(alert.getContactUser().getPhoneNumber());
        this.description = alert.getDescription();
        this.alertType = alert.getType();
        this.destType = alert.getDestinationType();
        this.tags = alert.getTagList();

        Group group;
        if (LiveWireAlertType.MEETING.equals(this.alertType)) {
            this.meetingName = alert.getMeeting().getName();
            this.meetingTimeMillis = alert.getMeeting().getEventStartDateTime().toEpochMilli();
            this.meetingLocation = alert.getMeeting().getEventLocation();
            group = alert.getMeeting().getAncestorGroup();
        } else {
            group = alert.getGroup();
        }

        this.ancestorGroupName = group.getName();
        this.groupSize = group.getMemberships().size();
        this.groupCreationMillis = group.getCreatedDateTime().toEpochMilli();
        this.groupTasks = group.getDescendantEvents().size() + group.getDescendantTodos().size();
        this.groupUid = group.getUid();

        this.reviewed = alert.isReviewed();
        this.sent = alert.isSent();

        if (alert.getLocation() != null) {
            this.latitude = alert.getLocation().getLatitude();
            this.longitude = alert.getLocation().getLongitude();
        }

        if (alert.hasMediaFiles()) {
            this.mediaFileUids = alert.getMediaFiles().stream().map(MediaFileRecord::getUid).collect(Collectors.toList());
        }
    }

}
