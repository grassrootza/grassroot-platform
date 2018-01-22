package za.org.grassroot.core.dto.group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.enums.GroupViewPriority;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.InstantToMilliSerializer;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Getter @Setter
public class GroupMinimalDTO extends GroupTimeChangedDTO {

    protected String description;
    private final String userRole;
    protected Instant nextEventTime;
    protected TaskType nextEventType;
    private final Set<Permission> userPermissions;
    private boolean pinned;
    private boolean discoverable;
    private boolean hidden;

    private String profileImageUrl;

    @JsonSerialize(using = InstantToMilliSerializer.class)
    private Instant lastTaskOrChangeTime;

    public GroupMinimalDTO(Group group, Membership membership) {
        super(group, group.getLastGroupChangeTime());
        this.description = group.getDescription();
        this.userRole = membership.getRole().getName();
        this.lastTaskOrChangeTime = group.getLatestChangeOrTaskTime();
        this.userPermissions = membership.getRole().getPermissions();
        this.pinned = GroupViewPriority.PINNED.equals(membership.getViewPriority());
        this.discoverable = group.isDiscoverable();
        this.hidden = GroupViewPriority.HIDDEN.equals(membership.getViewPriority());
        this.profileImageUrl = group.getImageUrl();

        List<Event> events = new ArrayList<>(group.getDescendantEvents());
        Collections.sort(events, (o1, o2) -> (int) (o2.getDeadlineTime().toEpochMilli() - o1.getDeadlineTime().toEpochMilli()));
        for (Event event : events) {
            if (event.getDeadlineTime().isAfter(Instant.now())) {
                this.nextEventTime = event.getDeadlineTime();
                this.nextEventType = event.getTaskType();
            }
        }

        memberCount = group.getMemberships().size();
    }

    @JsonIgnore
    public ZonedDateTime getLatestActionTime() {
        return DateTimeUtil.convertToUserTimeZone(lastTaskOrChangeTime, DateTimeUtil.getSAST());
    }


}
