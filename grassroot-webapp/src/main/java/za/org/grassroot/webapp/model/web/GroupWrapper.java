package za.org.grassroot.webapp.model.web;

import org.apache.commons.collections4.FactoryUtils;
import org.apache.commons.collections4.list.LazyList;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.*;

/**
 * Created by luke on 2015/09/13.
 * Wrapper class to get the group creation forms to work (hopefully) a lot better, since Thymeleaf / Spring MVC require
 * everything to be in the same class in order to work okay.
 */

public class GroupWrapper {

    private Group group;
    private String groupName;

    private boolean hasParent;
    private Group parentGroup;
    private Long parentId;

    private String parentName;

    private boolean discoverable;
    private boolean generateToken;
    private Integer tokenDaysValid;

    // private GroupPermissionTemplate template;

    private Set<MembershipInfo> addedMembers = new HashSet<>();

    // leaving out setters for group and parent as those are set at construction

    public GroupWrapper() {
        this.group = Group.makeEmpty();
        this.generateToken = false;
        this.discoverable = false;
        // this.template = GroupPermissionTemplate.DEFAULT_GROUP;
    }

    public GroupWrapper(Group parentGroup) {
        this();

        this.hasParent = true;
        this.parentGroup = Objects.requireNonNull(parentGroup);
        this.parentId = parentGroup.getId();
        this.parentName = parentGroup.getGroupName();
        this.addedMembers.addAll(convertUsersToMemberships(parentGroup.getMembers(), parentGroup));
        this.discoverable = parentGroup.isDiscoverable();
        // this.template = GroupPermissionTemplate.DEFAULT_GROUP; // todo: figure out if/how to store / inherit this
    }

    public Group getGroup() { return group; }

    public Group getParent() { return parentGroup; }

    public String getGroupName() { return groupName; }

    public void setGroupName(String groupName) { this.groupName = groupName; }

    public boolean getHasParent() { return hasParent; }

    public void setHasParent(boolean hasParent) { this.hasParent = hasParent; }

    public Long getParentId() { return parentId; }

    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public boolean getGenerateToken() { return generateToken; }

    public void setGenerateToken(boolean generateToken) { this.generateToken = generateToken; }

    public Integer getTokenDaysValid() { return tokenDaysValid; }

    public void setTokenDaysValid(Integer tokenDaysValid) { this.tokenDaysValid = tokenDaysValid; }

    public Set<MembershipInfo> getAddedMembers() { return addedMembers; }

    public boolean isDiscoverable() { return discoverable; }

    public void setDiscoverable(boolean discoverable) { this.discoverable = discoverable; }

    // public GroupPermissionTemplate getTemplate() { return template; }

    // public void setTemplate(GroupPermissionTemplate template) { this.template = template; }

    public void setAddedMembers(Set<MembershipInfo> addedMembers) {
        for (MembershipInfo memberToAdd : addedMembers) {
            this.addMember(memberToAdd);
        }
    }

    public void addMember(MembershipInfo newMember) {
        this.addedMembers.add(newMember);
    }

    /*
    Helper method for quickly populating one, for the group modification
     */

    public void populate(Group groupToModify) {

        // no need to do anything about the token -- handled separately

        this.group = groupToModify;
        this.groupName = group.getGroupName();
        this.parentGroup = group.getParent();
        this.discoverable = group.isDiscoverable();

        if (parentGroup != null) {
            this.hasParent = true;
            this.parentId = parentGroup.getId();
            this.parentName = parentGroup.getGroupName();
        }

        this.addedMembers.addAll(convertUsersToMemberships(groupToModify.getMembers(), groupToModify));

    }

    /*
    Helper method to convert users into membership info, temporary until group members converted to membership info
     */
    private Set<MembershipInfo> convertUsersToMemberships(Set<User> users, Group group) {
        Set<MembershipInfo> membershipInfos = new HashSet<>();
        for (User user : users) {
            // todo: extract the roleId for the group
            membershipInfos.add(new MembershipInfo(user.getPhoneNumber(), null, user.getDisplayName()));
        }
        return membershipInfos;
    }

}
