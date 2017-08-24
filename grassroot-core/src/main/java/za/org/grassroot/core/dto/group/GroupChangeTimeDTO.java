package za.org.grassroot.core.dto.group;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import za.org.grassroot.core.util.InstantToMilliSerializer;

import java.time.Instant;

/*
Light weight class to store and send core group information, including the last time it was changed (_not_ including
the last time a task was called, etc)
 */
public class GroupChangeTimeDTO {

    private String groupUid;

    @JsonSerialize(using = InstantToMilliSerializer.class)
    private Instant lastGroupChange;

    public GroupChangeTimeDTO(String groupUid, Instant lastGroupChange) {
        this.groupUid = groupUid;
        this.lastGroupChange = lastGroupChange;
    }

    public String getGroupUid() {
        return groupUid;
    }

    public Instant getLastGroupChange() {
        return lastGroupChange;
    }
}