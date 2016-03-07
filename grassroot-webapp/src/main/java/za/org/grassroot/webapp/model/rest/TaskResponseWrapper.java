package za.org.grassroot.webapp.model.rest;

import java.util.List;

/**
 * Created by paballo on 2016/03/02.
 */
public class TaskResponseWrapper {

    private Long id;
    private String groupName;
    private Long userId;
    private List<String> permissions;
    private List<TaskDTO> eventLogBook;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public List<TaskDTO> getEventLogBook() {
        return eventLogBook;
    }

    public void setEventLogBook(List<TaskDTO> eventLogBook) {
        this.eventLogBook = eventLogBook;
    }
}
