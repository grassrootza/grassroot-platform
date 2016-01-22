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
import java.util.*;

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
    private Group parent;
    private boolean paidFor;

    private String    groupTokenCode;
    private Timestamp tokenExpiryDateTime;
    private Integer   version;
    /*
     used to calculate when a reminder must be sent, before the eventStartTime
     when the event is created and if appliestoGroup is set it will default to a value in group
     if group = null or group.reminderminutes = 0, then it will use the site value in properties file
      */
    private int       reminderMinutes;
    private Set<Role> groupRoles = new HashSet<>();

    /*
    Setting a group 'language', not used for messages etc., but as a default if new user enters system through this
    group. Advanced feature only for web access (in effect, for paid account groups which have many many members)
     */
    private String defaultLanguage;

    /*
    Adding group inactive field, for when we want to deactivate a group (e.g., after a user consolidates)
     */
    private boolean active;

    /*
    Adding a 'discoverable' field, so group owners can mark if they want others to be able to find them
     */
    private boolean discoverable;

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
    @Column(name = "paid_for")
    public boolean isPaidFor() { return paidFor; }

    public void setPaidFor(boolean paidFor) { this.paidFor = paidFor; }

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

    @Column(name = "reminderminutes")
    public int getReminderMinutes() {
        return reminderMinutes;
    }

    public void setReminderMinutes(int reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
    }

    @Column(name = "default_language", nullable = true)
    public String getDefaultLanguage() { return defaultLanguage; }

    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }

    @Column(name = "active")
    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }

    @Column(name = "discoverable")
    public boolean isDiscoverable() { return discoverable; }

    public void setDiscoverable(boolean discoverable) { this.discoverable = discoverable;}


    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "group_roles",
            joinColumns        = {@JoinColumn(name = "group_id", referencedColumnName = "id")},
            inverseJoinColumns = {@JoinColumn(name = "role_id", referencedColumnName = "id")}
    )
    public Set<Role> getGroupRoles() {
        return groupRoles;
    }

    public void setGroupRoles(Set<Role> groupRoles) {
        this.groupRoles = groupRoles;
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
        this.active = true; // else this constructor causes null pointers in some calls
    }

    public Group(String groupName, User createdByUser) {
        this.groupName = groupName;
        this.createdByUser = createdByUser;
        this.active = true;
        this.discoverable = false;
    }

    public Group(String groupName, User createdByUser, Group parent) {
        this.groupName = groupName;
        this.createdByUser = createdByUser;
        this.parent = parent;
        this.active = true;
        this.discoverable = false;
    }

    public Group(String groupName, User createdByUser, boolean paidFor) {
        this.groupName = groupName;
        this.createdByUser = createdByUser;
        this.paidFor = paidFor;
        this.active = true;
        this.discoverable = false;
    }

    public Group(String groupName, User createdByUser, Group parent, boolean paidFor) {
        this.groupName = groupName;
        this.createdByUser = createdByUser;
        this.parent = parent;
        this.paidFor = paidFor;
        this.active = true;
        this.discoverable = false;
    }

    /*
    Adding & removing members and roles
     */

    public Group addMember(User newMember) {
        // might alternately put this check in service layer, but leaving it here for now, as sometimes we want to do
        // the check and add without calling the repository
        if (!this.groupMembers.contains(newMember))
            this.groupMembers.add(newMember);
        return this;
    }

    public Group addRole(Role role) {
        this.groupRoles.add(role);
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
                ", reminderMinutes=" + reminderMinutes +
                ", active=" + active +
                ", discoverable=" + discoverable +
                ", version=" + version +
//                ", groupMembers=" + groupMembers +
                ", parent=" + parent +
//                ", eventsApplied=" + eventsApplied +
                '}';
    }
}
