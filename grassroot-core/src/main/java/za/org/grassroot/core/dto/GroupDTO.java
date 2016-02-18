package za.org.grassroot.core.dto;

import java.sql.Timestamp;

/**
 * Created by paballo on 2016/02/15.
 */
public class GroupDTO {

    private Long id;
    private boolean active;
    private Timestamp created_date_time;
    private String name;
    private int group_size;


    public GroupDTO(Object[] objArray) {
        id = Long.parseLong(objArray[0].toString());
        created_date_time = (Timestamp) objArray[1];
        name = objArray[2].toString();
        active = (Boolean) objArray[3];
        group_size = (Integer) objArray[4];
    }

    public boolean isActive() {
        return active;
    }

    public Timestamp getCreatedDateTime() {
        return created_date_time;
    }

    public String getName() {
        return name;
    }

    public int getGroupMembers() {
        return group_size;
    }

    public Long getId() {return id;}

    @Override
    public String toString() {
        return "GroupDTO{" +
                "id=" + id +
                ", created_date_time=" + created_date_time +
                ", name='" + name + '\'' +
                '}';
    }

}
