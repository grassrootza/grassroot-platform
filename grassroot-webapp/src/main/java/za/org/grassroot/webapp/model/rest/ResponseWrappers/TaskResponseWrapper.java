package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import za.org.grassroot.webapp.model.rest.TaskDTO;

import java.util.List;

/**
 * Created by paballo on 2016/03/02.
 */
public class TaskResponseWrapper {

    private Long id;
    private String groupName;
    private Long userId;
    private List<String> permissions;
    private List<TaskDTO> tasks;


    public Long getId() {
        return id;
    }
    public String getGroupName() {
        return groupName;
    }
    public Long getUserId() {
        return userId;
    }
    public List<String> getPermissions() {return permissions;}
    public List<TaskDTO> getTasks() {return tasks;}

}
