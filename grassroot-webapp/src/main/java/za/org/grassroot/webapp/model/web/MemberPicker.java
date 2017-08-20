package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.task.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/03/26.
 * Utility class to be used through views, where need to select some set of members
 */
public class MemberPicker {

    // creating this as a list so we can sort it sensibly later
    private List<AssignmentWrapper> listOfMembers;

    public static MemberPicker create(UidIdentifiable parent, JpaEntityType type, boolean selectedByDefault) {
        Objects.requireNonNull(type);
        if (type.equals(JpaEntityType.GROUP)) {
            return new MemberPicker((Group) parent, selectedByDefault);
        } else if (type.equals(JpaEntityType.MEETING) || type.equals(JpaEntityType.VOTE) || type.equals(JpaEntityType.TODO)) {
            return new MemberPicker((Task) parent, selectedByDefault);
        } else {
            throw new IllegalArgumentException("Unsupported entity type passed as parent");
        }
    }

    public MemberPicker() {
        // needed for entity binding
    }


    public MemberPicker(Task<?> task, boolean selectedByDefault) {
        listOfMembers = new ArrayList<>();
        task.getAssignedMembers().forEach(m -> listOfMembers.add(new AssignmentWrapper(m, selectedByDefault)));
    }

    public MemberPicker(Group group, boolean selectedByDefault) {
        listOfMembers = new ArrayList<>();
        if (group != null) {
            group.getMembers().forEach(m -> listOfMembers.add(new AssignmentWrapper(m, selectedByDefault)));
        }
    }

    // member picker for members assigned to task from group
    public static MemberPicker taskAssigned(Task<?> task) {
        if (task.isAllGroupMembersAssigned()) {
            return new MemberPicker(task.getAncestorGroup(), true);
        } else {
            MemberPicker memberPicker = new MemberPicker(task.getAncestorGroup(), false);
            Set<String> taskAssignedUids = task.getAssignedMembers().stream()
                    .map(User::getUid).collect(Collectors.toSet());
            memberPicker.listOfMembers.stream()
                    .filter(m -> taskAssignedUids.contains(m.getUserUid()))
                    .forEach(m -> m.setSelected(true));
            return memberPicker;
        }
    }

    public List<AssignmentWrapper> getListOfMembers() {
        return listOfMembers;
    }

    public void setListOfMembers(List<AssignmentWrapper> listOfMembers) {
        this.listOfMembers = listOfMembers;
    }

    public void removeMember(String memberUid) {
        Objects.requireNonNull(memberUid);

        listOfMembers = listOfMembers.stream()
                .filter(u -> !u.getUserUid().equals(memberUid))
                .collect(Collectors.toList());
    }

    public Set<String> getSelectedUids() {
        return listOfMembers.stream()
                .filter(AssignmentWrapper::isSelected)
                .map(AssignmentWrapper::getUserUid)
                .collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MemberPicker{");
        sb.append("listOfMembers=").append(listOfMembers);
        sb.append("}");
        return sb.toString();
    }
}
