package za.org.grassroot.webapp.model.web;

import org.apache.commons.collections4.FactoryUtils;
import org.apache.commons.collections4.list.LazyList;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luke on 2015/09/13.
 * Wrapper class to get the group creation forms to work (hopefully) a lot better, since Thymeleaf / Spring MVC require
 * everything to be in the same class in order to work okay.
 */

public class GroupCreator {

    Group group;
    String groupName;

    boolean hasParent;
    Group parentGroup;
    Long parentId;

    String parentName;

    boolean generateToken;
    Integer tokenDaysValid;

    List<User> addedMembers = LazyList.lazyList(new ArrayList<>(), FactoryUtils.instantiateFactory(User.class));

    // leaving out setters for group and parent as those are set at construction

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

    public List<User> getAddedMembers() { return addedMembers; }

    public void setAddedMembers(List<User> addedMembers) { this.addedMembers = addedMembers; }
    public void addMember(User newMember) { this.addedMembers.add(newMember); }

    /* Constructors
    One for a group without a parent, one for a group with a parent
     */

    public GroupCreator() {
        this.group = new Group();
        this.generateToken = false;
    }

    public GroupCreator(Group parentGroup) {
        this.group = new Group();

        // todo: make sure we never pass a null parent

        this.hasParent = true;
        this.parentGroup = parentGroup;
        this.parentId = parentGroup.getId();
        this.parentName = parentGroup.getGroupName();
        this.addedMembers.addAll(parentGroup.getGroupMembers());
    }

}
