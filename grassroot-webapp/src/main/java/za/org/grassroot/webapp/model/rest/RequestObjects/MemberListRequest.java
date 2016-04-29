package za.org.grassroot.webapp.model.rest.RequestObjects;

import za.org.grassroot.core.domain.JpaEntityType;

/**
 * Created by luke on 2016/04/28.
 */
public class MemberListRequest {

    private String userUid;
    private String parentUid;
    private JpaEntityType parentEntityType;
    private boolean selectedByDefault;

    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    public String getParentUid() {
        return parentUid;
    }

    public void setParentUid(String parentUid) {
        this.parentUid = parentUid;
    }

    public JpaEntityType getParentEntityType() {
        return parentEntityType;
    }

    public void setParentEntityType(JpaEntityType parentEntityType) {
        this.parentEntityType = parentEntityType;
    }

    public boolean isSelectedByDefault() {
        return selectedByDefault;
    }

    public void setSelectedByDefault(boolean selectedByDefault) {
        this.selectedByDefault = selectedByDefault;
    }

    @Override
    public String toString() {
        return "MemberListRequest{" +
                "userUid='" + userUid + '\'' +
                ", parentUid='" + parentUid + '\'' +
                ", parentEntityType=" + parentEntityType +
                ", selectedByDefault=" + selectedByDefault +
                '}';
    }
}
