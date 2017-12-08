package za.org.grassroot.core.dto.group;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.util.InstantToMilliSerializer;

import java.time.Instant;

/*
Light weight class to store and send core group information, including the last time it was changed (_not_ including
the last time a task was called, etc)
 */
public class GroupTimeChangedDTO extends GroupRefDTO {


    @JsonSerialize(using = InstantToMilliSerializer.class)
    private Instant lastGroupChange;

    public GroupTimeChangedDTO(Group group, Instant lastGroupChange) {
        super(group.getUid(), group.getGroupName(), group.getMemberships().size());
        this.lastGroupChange = lastGroupChange;
    }

    public Instant getLastGroupChange() {
        return lastGroupChange;
    }
}