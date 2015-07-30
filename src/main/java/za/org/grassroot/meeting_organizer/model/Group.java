package za.org.grassroot.meeting_organizer.model;

/**
 * Created by luke on 2015/07/16.
 * Lots of to-dos, principally: check/validate the "created_by_user" relationship; do the hash code
 */

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.sql.Timestamp;
import java.util.List;
import javax.persistence.*;

@Entity
@Table(name="\"group\"") // quoting table name in case "group" is a reserved keyword
@EqualsAndHashCode
@ToString
public class Group {
    private String groupName;
    private Integer id;
    private Timestamp createdDateTime;
    private User createdByUser;

    private List<User> groupMembers;

    @Basic
    @Column(name = "name", nullable = false, length = 50)
    public String getGroupName() { return groupName; }

    public void setGroupName(String groupName) { this.groupName = groupName; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    public Integer getId() { return id; }

    public void setId(Integer id) { this.id = id; }

    @Basic
    @Column(name="created_date_time", insertable = false, updatable = false)
    public Timestamp getCreatedDateTime() { return createdDateTime; }

    public void setCreatedDateTime(Timestamp createdDateTime) { this.createdDateTime = createdDateTime; }

    @ManyToOne
    @JoinColumn(name="created_by_user")
    public User getCreatedByUser() { return this.createdByUser; }

    public void setCreatedByUser(User createdByUser) { this.createdByUser = createdByUser; }

    @OneToMany(mappedBy = "group")
    private List<Event> eventsApplied;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name="group_user_membership", joinColumns=@JoinColumn(name="group_id"), inverseJoinColumns=@JoinColumn(name="user_id"))
    public List<User> getGroupMembers() { return groupMembers; }
    public void setGroupMembers(List<User> groupMembers) { this.groupMembers = groupMembers; }

    /**
     * Adding some auxiliary methods for coping with blank names, etc.
     */

    public String getName(String unnamedPrefix) {
        if (groupName != null && groupName.trim().length() != 0) {
            return groupName;
        } else if (unnamedPrefix.trim().length() == 0) {
            return "Unnamed group (" + groupMembers.size() + " members)";
        } else {
            return unnamedPrefix;
        }
    }
}
