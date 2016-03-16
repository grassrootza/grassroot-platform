package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;

/**
 * Created by paballo on 2016/03/16.
 */
public class GroupSearchWrapper {
    private String id;
    private String groupName;
    private String description;
    private String groupCreator;
    private Integer count;

    public GroupSearchWrapper(Group group, Event event){
        this.id =group.getUid();
        this.groupName = group.getGroupName();
        this.description = event.getName();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.count = group.getMemberships().size();

    }

    public String getId() {
        return id;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getDescription() {
        return description;
    }

    public String getGroupCreator() {
        return groupCreator;
    }

    public Integer getCount() {
        return count;
    }
}
