package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.services.group.GroupPermissionTemplate;

import java.util.*;

/**
 * Created by luke on 2015/09/13.
 * Wrapper class to get the group creation forms to work (hopefully) a lot better, since Thymeleaf / Spring MVC require
 * everything to be in the same class in order to work okay.
 */

public class GroupWrapper {

    private Group group;
    private String groupName;
    private String groupDescription;
    private GroupPermissionTemplate permissionTemplate;

    private boolean hasParent;
    private Group parentGroup;
    private Long parentId;

    private String parentName;

    private boolean canAddMembers;
    private boolean canRemoveMembers;
    private boolean canUpdateDetails;

    private int reminderMinutes;

    // need to use a list so that we can add and remove
    private List<MembershipInfo> listOfMembers = new ArrayList<>();

    // leaving out setters for group and parent as those are set at construction

    public GroupWrapper() {
        this.group = Group.makeEmpty();
        this.reminderMinutes = 24 * 60;
        this.permissionTemplate = GroupPermissionTemplate.DEFAULT_GROUP;
    }

    public GroupWrapper(Group parentGroup) {
        this();

        this.hasParent = true;
        this.parentGroup = Objects.requireNonNull(parentGroup);
        this.parentId = parentGroup.getId();
        this.parentName = parentGroup.getGroupName();
        this.listOfMembers.addAll(MembershipInfo.createFromMembers(parentGroup.getMemberships()));
    }

    public Group getGroup() { return group; }

    public Group getParent() { return parentGroup; }

    public String getGroupName() { return groupName; }

    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getGroupDescription() { return groupDescription; }

    public void setGroupDescription(String groupDescription) { this.groupDescription = groupDescription; }

    public boolean getHasParent() { return hasParent; }

    public Long getParentId() { return parentId; }

    public String getParentName() {
        return parentName;
    }

    public Set<MembershipInfo> getAddedMembers() { return new HashSet<>(listOfMembers); }

    public List<MembershipInfo> getListOfMembers() { return listOfMembers; }

    public void setListOfMembers(List<MembershipInfo> listOfMembers) { this.listOfMembers = new ArrayList<>(listOfMembers); }

    public void addMember(MembershipInfo newMember) {
        this.listOfMembers.add(newMember);
    }

    public boolean isCanAddMembers() {
        return canAddMembers;
    }

    public void setCanAddMembers(boolean canAddMembers) {
        this.canAddMembers = canAddMembers;
    }

    public boolean isCanRemoveMembers() {
        return canRemoveMembers;
    }

    public void setCanRemoveMembers(boolean canRemoveMembers) {
        this.canRemoveMembers = canRemoveMembers;
    }

    public boolean isCanUpdateDetails() {
        return canUpdateDetails;
    }

    public void setCanUpdateDetails(boolean canUpdateDetails) {
        this.canUpdateDetails = canUpdateDetails;
    }

    public void setReminderMinutes(int reminderMinutes) { this.reminderMinutes = reminderMinutes; }

    public int getReminderMinutes() { return reminderMinutes; }

    public GroupPermissionTemplate getPermissionTemplate() {
        return permissionTemplate;
    }

    public void setPermissionTemplate(GroupPermissionTemplate permissionTemplate) {
        this.permissionTemplate = permissionTemplate;
    }

    /*
    Helper method for quickly populating one, for the group modification
     */

    public void populate(Group groupToModify) {

        // no need to do anything about the token -- handled separately

        this.group = groupToModify;
        this.groupName = group.getGroupName();
        this.parentGroup = group.getParent();
        this.reminderMinutes = group.getReminderMinutes();

        if (parentGroup != null) {
            this.hasParent = true;
            this.parentId = parentGroup.getId();
            this.parentName = parentGroup.getGroupName();
        }

        this.listOfMembers.addAll(MembershipInfo.createFromMembers(groupToModify.getMemberships()));
        this.listOfMembers.sort(Comparator.reverseOrder());

    }

}
