package za.org.grassroot.services.group;

import za.org.grassroot.core.domain.group.Group;

/**
 * Created by luke on 2016/11/23.
 */
public class GroupSearchResultDTO {

    private final String name;
    private final String uid;
    private final GroupResultType type;

    public GroupSearchResultDTO(Group group, GroupResultType type) {
        this.name = group.getName();
        this.uid = group.getUid();
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getUid() {
        return uid;
    }

    public GroupResultType getType() {
        return type;
    }
}
