package za.org.grassroot.core.dto.group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.InstantToMilliSerializer;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;

@Getter @Setter
public class GroupMinimalDTO extends GroupTimeChangedDTO {

    protected final String name;
    protected String description;
    private final String userRole;
    protected Long memberCount;
    private final Set<Permission> userPermissions;

    @JsonSerialize(using = InstantToMilliSerializer.class)
    private Instant lastTaskOrChangeTime;

    public GroupMinimalDTO(Group group, Role role) {
        super(group.getUid(), group.getLastGroupChangeTime());
        this.name = group.getName();
        this.description = group.getDescription();
        this.userRole = role.getName();
        this.lastTaskOrChangeTime = group.getLatestChangeOrTaskTime();
        this.userPermissions = role.getPermissions();
    }

    @JsonIgnore
    public ZonedDateTime getLatestActionTime() {
        return DateTimeUtil.convertToUserTimeZone(lastTaskOrChangeTime, DateTimeUtil.getSAST());
    }

    public GroupMinimalDTO addMemberCount(long memberCount) {
        this.setMemberCount(memberCount);
        return this;
    }
}
