package za.org.grassroot.webapp.model.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.AssignedMembersContainer;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/03/26.
 * Utility class to be used through views, where need to select some set of members
 */
public class MemberPicker {

    private static final Logger log = LoggerFactory.getLogger(MemberPicker.class);

    // creating this as a list so we can sort it sensibly later
    private List<AssignmentWrapper> listOfMembers;

    public MemberPicker() {
        listOfMembers = new ArrayList<>();
    }

    public MemberPicker(AssignedMembersContainer membersContainer, boolean selectedByDefault) {
        listOfMembers = new ArrayList<>();
        for (User user : membersContainer.getAssignedMembers()) {
            listOfMembers.add(new AssignmentWrapper(user, selectedByDefault));
        }
    }

    public MemberPicker(Group group, boolean selectedByDefault) {
        listOfMembers = new ArrayList<>();
        for (User user : group.getMembers()) {
            listOfMembers.add(new AssignmentWrapper(user, selectedByDefault));
        }
    }

    public List<AssignmentWrapper> getListOfMembers() {
        return listOfMembers;
    }

    public void setListOfMembers(List<AssignmentWrapper> listOfMembers) {
        this.listOfMembers = listOfMembers;
    }

    public Set<String> getSelectedUids() {
        return listOfMembers.stream().filter(m -> m.isSelected()).map(m -> m.getUserUid()).collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MemberPicker{");
        sb.append("listOfMembers=").append(listOfMembers);
        sb.append("}");
        return sb.toString();
    }
}
