package za.org.grassroot.core.domain;

//TODO level so that we can block too many levels

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.*;

@Entity
@Table(name = "group_profile") // quoting table name in case "group" is a reserved keyword
public class Group implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Column(name = "name", nullable = false, length = 50)
    private String groupName;

    @Column(name = "created_date_time", insertable = true, updatable = false)
    private Timestamp createdDateTime;

    @ManyToOne
    @JoinColumn(name = "created_by_user")
    private User createdByUser;

    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinTable(name = "group_user_membership", joinColumns = @JoinColumn(name = "group_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private List<User> groupMembers = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "parent")
    private Group parent;

    @Column(name = "paid_for")
    private boolean paidFor;

    @Column(name = "group_token_code", nullable = true, insertable = true, updatable = true, unique = true)
    private String groupTokenCode;

    @Column(name = "token_code_expiry", nullable = true, insertable = true, updatable = true)
    private Timestamp tokenExpiryDateTime;

    @Version
    private Integer version;

    /*
     used to calculate when a reminder must be sent, before the eventStartTime
     when the event is created and if appliestoGroup is set it will default to a value in group
     if group = null or group.reminderminutes = 0, then it will use the site value in properties file
      */
    @Column(name = "reminderminutes")
    private int reminderMinutes;

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "group_roles",
            joinColumns = {@JoinColumn(name = "group_id", referencedColumnName = "id")},
            inverseJoinColumns = {@JoinColumn(name = "role_id", referencedColumnName = "id")}
    )
    private Set<Role> groupRoles = new HashSet<>();

    /*
    Setting a group 'language', not used for messages etc., but as a default if new user enters system through this
    group. Advanced feature only for web access (in effect, for paid account groups which have many many members)
     */
    @Column(name = "default_language", nullable = true)
    private String defaultLanguage;

    /*
    Adding group inactive field, for when we want to deactivate a group (e.g., after a user consolidates)
     */
    @Column(name = "active")
    private boolean active;

    /*
    Adding a 'discoverable' field, so group owners can mark if they want others to be able to find them
     */
    @Column(name = "discoverable")
    private boolean discoverable;

    private Group() {
        // for JPA
    }

    public Group(String groupName, User createdByUser) {
        this(groupName, createdByUser, null);
    }

    public Group(String groupName, User createdByUser, Group parent) {
        this.uid = UIDGenerator.generateId();
        this.groupName = groupName;
        this.createdByUser = createdByUser;
        this.active = true;
        this.discoverable = false;
        this.parent = parent;
    }

    /**
     * We use this static constructor because no-arg constructor should be only used by JPA
     * @return group
     */
    public static Group makeEmpty() {
        Group group = new Group();
        group.uid = UIDGenerator.generateId();
        group.active = true;
        return group;
    }

    public String getUid() {
        return uid;
    }


    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Timestamp createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public User getCreatedByUser() {
        return this.createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }

    public List<User> getGroupMembers() {
        if (groupMembers == null) {
            groupMembers = new ArrayList<>();
        }
        return groupMembers;
    }

    public void setGroupMembers(List<User> groupMembers) {
        this.groupMembers = groupMembers;
    }

    public Group getParent() {
        return parent;
    }

    public void setParent(Group parent) {
        this.parent = parent;
    }

    public boolean isPaidFor() { return paidFor; }

    public void setPaidFor(boolean paidFor) { this.paidFor = paidFor; }

    public String getGroupTokenCode() { return groupTokenCode; }

    public void setGroupTokenCode(String groupTokenCode) { this.groupTokenCode = groupTokenCode; }

    public Timestamp getTokenExpiryDateTime() { return tokenExpiryDateTime; }

    public void setTokenExpiryDateTime(Timestamp tokenExpiryDateTime) { this.tokenExpiryDateTime = tokenExpiryDateTime; }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public int getReminderMinutes() {
        return reminderMinutes;
    }

    public void setReminderMinutes(int reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
    }

    public String getDefaultLanguage() { return defaultLanguage; }

    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }

    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }

    public boolean isDiscoverable() { return discoverable; }

    public void setDiscoverable(boolean discoverable) { this.discoverable = discoverable;}

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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Group group = (Group) o;

        if (uid != null ? !uid.equals(group.uid) : group.uid != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return uid != null ? uid.hashCode() : 0;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Group{");
        sb.append("id=").append(id);
        sb.append(", uid='").append(uid).append('\'');
        sb.append(", groupName='").append(groupName).append('\'');
        sb.append(", createdDateTime=").append(createdDateTime);
        sb.append(", active=").append(active);
        sb.append(", discoverable=").append(discoverable);
        sb.append(", version=").append(version);
        sb.append(", reminderMinutes=").append(reminderMinutes);
        sb.append('}');
        return sb.toString();
    }
}
