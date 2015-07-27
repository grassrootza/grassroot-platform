package za.org.grassroot.meeting_organizer.model;

import java.sql.Timestamp;
import java.util.List;
import javax.persistence.*;

//todo: add validation to all model classes
//todo: use java 8 date and time types and a JPA converter instead of Timestamp type
//todo: createdDateTime should be read-only -  the database should insert this automatically
//todo: use Lombok to generate ToString, equals and hashcode implementations
//todo: use field annotations rather than getter annotations because then all the annotations will be closer together
//todo: make these classes immutable - all args constructor and no setters
//todo: id and createdDateTime fields should not be insertable or updatable
//todo: extract base class for all entities that has id and createdDateTime

@Entity
@Table(name = "\"user\"")  //table name needs to be quoted in SQL because 'user' is a reserved keyword
public class User {
    private String phoneNumber;
    private String displayName;
    private Integer id;
    private Timestamp createdDateTime;

    private List<Group> groupsPartOf;

    @Basic
    @Column(name = "phone_number", nullable = false, length = 20)
    public String getPhoneNumber() { return phoneNumber; }

    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    @Basic
    @Column(name = "display_name", nullable = true, length = 70) // allowing this to be nullable as might not be set
    public String getDisplayName() { return displayName; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Basic
    @Column(name = "created_date_time", insertable = false, updatable = false)
    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Timestamp createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    @OneToMany(mappedBy = "user")
    private List<Group> groupsCreated;

    @OneToMany(mappedBy = "user")
    private List<Event> eventsCreated;

    @ManyToMany(cascade = CascadeType.ALL, mappedBy = "groupMembers") // not sure which cascade type is going to be best
    public List<Group> getGroupsPartOf() { return groupsPartOf; }
    public void setGroupsPartOf(List<Group> groupsPartOf) { this.groupsPartOf = groupsPartOf; }

    @Override
    public int hashCode() {
        int result = phoneNumber != null ? phoneNumber.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (createdDateTime != null ? createdDateTime.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        final User user = (User) o;

        if (createdDateTime != null ? !createdDateTime.equals(user.createdDateTime) : user.createdDateTime != null) { return false; }
        if (id != null ? !id.equals(user.id) : user.id != null) { return false; }
        if (phoneNumber != null ? !phoneNumber.equals(user.phoneNumber) : user.phoneNumber != null) { return false; }

        return true;
    }
}
