package za.org.grassroot.core.dto.group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.InstantToMilliSerializer;

import java.time.Instant;
import java.time.ZonedDateTime;

public class GroupMinimalDTO extends GroupTimeChangedDTO {

    private final String name;
    private final String userRole;
    protected Long memberCount;

    @JsonSerialize(using = InstantToMilliSerializer.class)
    private Instant lastTaskOrChangeTime;

    public GroupMinimalDTO(Group group, Role role) {
        super(group.getUid(), group.getLastGroupChangeTime());
        this.name = group.getName();
        this.userRole = role.getName();
        this.lastTaskOrChangeTime = group.getLatestChangeOrTaskTime();
    }

    public String getName() {
        return name;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setMemberCount(Long memberCount) {
        this.memberCount = memberCount;
    }

    public Long getMemberCount() {
        return memberCount;
    }

    public Instant getLastTaskOrChangeTime() {
        return lastTaskOrChangeTime;
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
