package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.util.List;

/**
 * Created by luke on 2016/11/19.
 * to handle Thymeleaf / Javascript unpleasantness with client side dynamic form (change_multiple)
 */
public class MemberWrapperList {

    private String groupUid;
    private List<MemberWrapper> memberList;

    public MemberWrapperList(Group group, User user) {
        this.memberList = MemberWrapper.generateListFromGroup(group, user);
        this.groupUid = group.getUid();
    }

    public MemberWrapperList() {
        // for form binding etc
    }

    public List<MemberWrapper> getMemberList() {
        return memberList;
    }

    public void setMemberList(List<MemberWrapper> memberList) {
        this.memberList = memberList;
    }

    public String getGroupUid() {
        return groupUid;
    }

    public void setGroupUid(String groupUid) {
        this.groupUid = groupUid;
    }
}