package za.org.grassroot.core.domain;

//TODO level so that we can block too many levels

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

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

    @ManyToOne()
    @JoinColumn(name = "created_by_user", nullable = false, updatable = false)
    private User createdByUser;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "group")
    private Set<Membership> memberships = new HashSet<>();

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

    @OneToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "group_roles",
            joinColumns = @JoinColumn(name = "group_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id")
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
    @Column(name = "active", nullable = false)
    private boolean active;

    /*
    Adding a 'discoverable' field, so group owners can mark if they want others to be able to find them
     */
    @Column(name = "discoverable", nullable = false)
    private boolean discoverable;

    private Group() {
        // for JPA
    }

    public Group(String groupName, User createdByUser) {
        this(groupName, createdByUser, null);
    }

    public Group(String groupName, User createdByUser, Group parent) {
        this.uid = UIDGenerator.generateId();
        this.groupName = Objects.requireNonNull(groupName);
        this.createdByUser = Objects.requireNonNull(createdByUser);
        this.active = true;
        this.discoverable = false;
        this.parent = parent;

        // automatically add 3 default roles
        addRole(BaseRoles.ROLE_GROUP_ORGANIZER);
        addRole(BaseRoles.ROLE_COMMITTEE_MEMBER);
        addRole(BaseRoles.ROLE_ORDINARY_MEMBER);
    }

    private void addRole(String roleName) {
        Objects.requireNonNull(roleName);
        for (Role role : groupRoles) {
            if (role.getName().equals(roleName)) {
                throw new IllegalArgumentException("Role with name " + roleName + " already exists in group: " + this);
            }
        }
        this.groupRoles.add(new Role(roleName, uid));
    }

    /**
     * We use this static constructor because no-arg constructor should be only used by JPA
     *
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

    void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }

    public Set<Membership> getMemberships() {
        if (memberships == null) {
            memberships = new HashSet<>();
        }
        return new HashSet<>(memberships);
    }

    public Set<User> getMembers() {
        return getMemberships().stream()
                .map(Membership::getUser)
                .collect(Collectors.toSet());
    }

    public Set<Membership> addMembers(Collection<User> newMembers) {
        return addMembers(newMembers, BaseRoles.ROLE_ORDINARY_MEMBER);
    }

    public Set<Membership> addMembers(Collection<User> newMembers, String roleName) {
        Objects.requireNonNull(roleName);

        Role role = getRole(roleName)
                .orElseThrow(() -> new IllegalArgumentException("No role with name " + roleName + " within group " + this));
        return addMembers(newMembers, role);
    }

    public Set<Membership> addMembers(Collection<User> newMembers, Role role) {
        Objects.requireNonNull(newMembers);

        Set<Membership> memberships = new HashSet<>();
        for (User newMember : newMembers) {
            Membership membership = addMember(newMember, role);
            if (membership != null) {
                memberships.add(membership);
            }
        }
        return memberships;
    }

    public Membership addMember(User newMember) {
        return addMember(newMember, BaseRoles.ROLE_ORDINARY_MEMBER);
    }

    public Membership addMember(User newMember, String roleName) {
        Objects.requireNonNull(roleName);
        Role role = getRole(roleName)
                .orElseThrow(() -> new IllegalArgumentException("No role with name " + roleName + " within group " + this));
        return addMember(newMember, role);
    }

    public Membership addMember(User newMember, Role role) {
        Objects.requireNonNull(newMember);
        Objects.requireNonNull(role);

        if (!getGroupRoles().contains(role)) {
            throw new IllegalArgumentException("Role " + role + " is not one of roles belonging to group: " + this);
        }
        Membership membership = new Membership(this, newMember, role);
        boolean added = this.memberships.add(membership);
        if (added) {
            newMember.addMappedByMembership(membership);
            return membership;
        }
        return null;
    }

    public Membership removeMember(User member) {
        Objects.requireNonNull(member);
        Membership membership = getMembership(member);
        if (membership == null) {
            return null;
        }
        this.memberships.remove(membership);
        return membership;
    }

    public Membership getMembership(User user) {
        Objects.requireNonNull(user);

        for (Membership membership : memberships) {
            if (membership.getUser().equals(user)) {
                return membership;
            }
        }
        return null;
    }

    public Optional<Role> getRole(String roleName) {
        Objects.requireNonNull(roleName);
        return groupRoles.stream()
                .filter(role -> role.getName().equals(roleName))
                .findFirst();
    }

    public boolean hasMember(User user) {
        Objects.requireNonNull(user);
        Membership membership = getMembership(user);
        return membership != null;
    }

    public Group getParent() {
        return parent;
    }

    public void setParent(Group parent) {
        this.parent = parent;
    }

    public boolean isPaidFor() {
        return paidFor;
    }

    public void setPaidFor(boolean paidFor) {
        this.paidFor = paidFor;
    }

    public String getGroupTokenCode() {
        return groupTokenCode;
    }

    public void setGroupTokenCode(String groupTokenCode) {
        this.groupTokenCode = groupTokenCode;
    }

    public Timestamp getTokenExpiryDateTime() {
        return tokenExpiryDateTime;
    }

    public void setTokenExpiryDateTime(Timestamp tokenExpiryDateTime) {
        this.tokenExpiryDateTime = tokenExpiryDateTime;
    }

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

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDiscoverable() {
        return discoverable;
    }

    public void setDiscoverable(boolean discoverable) {
        this.discoverable = discoverable;
    }

    public Set<Role> getGroupRoles() {
        if (groupRoles == null) {
            groupRoles = new HashSet<>();
        }
        return new HashSet<>(groupRoles);
    }

    // todo: remove later after refactor
    public void setGroupRoles(Set<Role> groupRoles) {
        this.groupRoles.clear();
        this.groupRoles.addAll(groupRoles);
    }

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
        }
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
            return "Unnamed group (" + memberships.size() + " members)";
        } else {
            return unnamedPrefix;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof Group)) {
            return false;
        }

        Group group = (Group) o;

        if (getUid() != null ? !getUid().equals(group.getUid()) : group.getUid() != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
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
