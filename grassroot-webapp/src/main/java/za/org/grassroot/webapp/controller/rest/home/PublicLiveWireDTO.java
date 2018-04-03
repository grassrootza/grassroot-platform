package za.org.grassroot.webapp.controller.rest.home;

import lombok.Getter;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.LiveWireAlertType;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class PublicLiveWireDTO {
    private String serverUid;
    private String headline;
    private long creationTimeMillis;
    private String description;

    private String contactName;
    private LiveWireAlertType alertType;
    private String entityName;
    private int entitySize;
    private int activityCount;
    private List<String> imageKeys;


    public PublicLiveWireDTO(LiveWireAlert alert, boolean includeFullDetails) {
        this.headline = alert.getHeadline();
        this.creationTimeMillis = alert.getCreationTime().toEpochMilli();
        this.description = alert.getDescription();

        if (includeFullDetails) {
            this.imageKeys = alert.getMediaFiles().stream().map(MediaFileRecord::getKey).collect(Collectors.toList());
            this.contactName = alert.getContactNameNullSafe();
            this.alertType = alert.getType();

            if (LiveWireAlertType.INSTANT.equals(alert.getType())) {
                Group group = alert.getGroup();
                this.entityName = group.getName();
                this.entitySize = group.getMembers().size();
                this.activityCount = group.getDescendantEvents().size() + group.getDescendantTodos().size();
            } else {
                Meeting meeting = alert.getMeeting();
                Group group = alert.getMeeting().getAncestorGroup();
                this.entityName = meeting.getName();
                this.entitySize = meeting.isAllGroupMembersAssigned() || meeting.getAllMembers() == null ?
                        meeting.getAncestorGroup().getMemberships().size() : meeting.getAllMembers().size();
                this.activityCount = group.getDescendantEvents().size() + group.getDescendantTodos().size();
            }
        }
    }

}
