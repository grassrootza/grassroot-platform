package za.org.grassroot.core.domain;

//TODO level so that we can block too many levels
/**
 * Created by luke on 2015/07/16.
 * Lots of to-dos, principally: check/validate the "created_by_user" relationship; do the hash code
 */

import lombok.EqualsAndHashCode;
import org.apache.commons.collections4.FactoryUtils;
import org.apache.commons.collections4.list.LazyList;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@Entity
@Table(name = "group_profile") // quoting table name in case "group" is a reserved keyword
@EqualsAndHashCode
//@ToString
public class Group implements Serializable {
    private String    groupName;
    private Long      id;
    private Timestamp createdDateTime;
    private User      createdByUser;

    private List<User> groupMembers = LazyList.lazyList(new ArrayList<>(), FactoryUtils.instantiateFactory(User.class));
    private Group       parent;

    private String      groupTokenCode;
    private Timestamp   tokenExpiryDateTime;
    private Integer version;


    @Basic
    @Column(name = "name", nullable = false, length = 50)
    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Basic
    @Column(name = "created_date_time", insertable = true, updatable = false)
    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Timestamp createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    @ManyToOne
    @JoinColumn(name = "created_by_user")
    public User getCreatedByUser() {
        return this.createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }

    @OneToMany(mappedBy = "group")
    private List<Event> eventsApplied;

    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinTable(name = "group_user_membership", joinColumns = @JoinColumn(name = "group_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    public List<User> getGroupMembers() {
        if (groupMembers == null) {
            groupMembers = new ArrayList<>();
        }
        return groupMembers;
    }

    public void setGroupMembers(List<User> groupMembers) {
        this.groupMembers = groupMembers;
    }

    @ManyToOne
    @JoinColumn(name = "parent")
    public Group getParent() {
        return parent;
    }

    public void setParent(Group parent) {
        this.parent = parent;
    }

    @Basic
    @Column(name = "group_token_code", nullable = true, insertable = true, updatable = true, unique = true)
    public String getGroupTokenCode() { return groupTokenCode; }

    public void setGroupTokenCode(String groupTokenCode) { this.groupTokenCode = groupTokenCode; }

    @Basic
    @Column(name = "token_code_expiry", nullable = true, insertable = true, updatable = true)
    public Timestamp getTokenExpiryDateTime() { return tokenExpiryDateTime; }

    public void setTokenExpiryDateTime(Timestamp tokenExpiryDateTime) { this.tokenExpiryDateTime = tokenExpiryDateTime; }

    @Version
    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
        }
    }

    /*
    Constructors
     */

    public Group() {
    }

    public Group(String groupName, User createdByUser) {
        this.groupName = groupName;
        this.createdByUser = createdByUser;
    }

    public Group(String groupName, User createdByUser, Group parent) {
        this.groupName = groupName;
        this.createdByUser = createdByUser;
        this.parent = parent;
    }

    /*
    Adding & removing members
     */

    public Group addMember(User newMember) {
        // might alternately put this check in service layer, but leaving it here for now, as sometimes we want to do
        // the check and add without calling the repository
        if (!this.groupMembers.contains(newMember))
            this.groupMembers.add(newMember);
        return this;
    }

    /*
     * Auxiliary methods for checking if blank name, coping with blank names, etc.
     */

    public boolean hasName() {
        return (groupName != null && groupName.trim().length() != 0);
    }

    public String getName(String unnamedPrefix) {
        if (hasName()) {
            return groupName;
        } else if (unnamedPrefix.trim().length() == 0) {
            return "Unnamed group (" + groupMembers.size() + " members)";
        } else {
            return unnamedPrefix;
        }
    }

    @Override
    public String toString() {
        return "Group{" +
                "groupName='" + groupName + '\'' +
                ", id=" + id +
                ", createdDateTime=" + createdDateTime +
                ", createdByUser=" + createdByUser +
                ", version=" + version +

//                ", groupMembers=" + groupMembers +
                ", parent=" + parent +
//                ", eventsApplied=" + eventsApplied +
                '}';
    }
}
