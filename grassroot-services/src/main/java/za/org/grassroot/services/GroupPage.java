package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.dto.GroupDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luke on 2016/03/27.
 * Simple class that embodies some of the features of the Spring JPA page, but which we can use on a list of
 * entities which we have filtered by permission.
 * todo: create a custom specification so that we can use jpa page instead
 */
public class GroupPage {

    private List<GroupDTO> groups;
    private boolean next;
    private boolean previous;
    private int totalElements;

    public GroupPage(List<GroupDTO> preSlicedGroups, int totalElements, boolean next, boolean previous) {
        this.groups = preSlicedGroups;
        this.totalElements = totalElements;
        this.next = next;
        this.previous = previous;
    }

    public GroupPage(List<GroupDTO> fullGroupList, int pageNumber, int pageSize) {
        int startIndex = Math.min(pageSize * pageNumber, fullGroupList.size());
        int endIndex = Math.min(pageSize * (pageNumber + 1), fullGroupList.size());
        this.groups = fullGroupList.subList(startIndex, endIndex);
        this.previous = pageNumber > 0;
        this.next = endIndex < fullGroupList.size();
        this.totalElements = fullGroupList.size();
    }

    // utility method for tests, and may come in handy in future
    public static GroupPage createFromGroups(List<Group> originalGroups, int pageNumber, int pageSize) {
        List<GroupDTO> groupDTOs = new ArrayList<>();
        for (Group g : originalGroups) groupDTOs.add(new GroupDTO(g));
        return new GroupPage(groupDTOs, pageNumber, pageSize);
    }

    public List<GroupDTO> getContent() { return groups; }

    public boolean hasNext() { return next; }

    public boolean hasPrevious() { return previous; }

    public int getTotalElements() { return totalElements; }

}
