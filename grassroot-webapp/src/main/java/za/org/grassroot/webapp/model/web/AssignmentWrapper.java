package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.User;

/**
 * Created by luke on 2016/03/26.
 */
public class AssignmentWrapper {

    private String userUid;
    private String nameToDisplay;
    private boolean selected;

    public AssignmentWrapper() { }

    public AssignmentWrapper(User user, boolean selected) {
        this.userUid = user.getUid();
        this.nameToDisplay = user.nameToDisplay();
        this.selected = selected;
    }

    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    public String getNameToDisplay() {
        return nameToDisplay;
    }

    public void setNameToDisplay(String nameToDisplay) {
        this.nameToDisplay = nameToDisplay;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public String toString() {
        return "AssignmentWrapper{" +
                "nameToDisplay='" + nameToDisplay + '\'' +
                ", selected=" + selected +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AssignmentWrapper that = (AssignmentWrapper) o;

        return userUid != null ? userUid.equals(that.userUid) : that.userUid == null;
    }

    @Override
    public int hashCode() {
        return userUid != null ? userUid.hashCode() : 0;
    }
}
