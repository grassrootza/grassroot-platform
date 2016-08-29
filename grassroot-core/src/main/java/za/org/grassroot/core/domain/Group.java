package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Entity
@Table(name = "group_profile") // quoting table name in case "group" is a reserved keyword
public class Group implements TodoContainer, VoteContainer, MeetingContainer, Serializable, Comparable<Group> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Column(name = "name", nullable = false, length = 50)
    private String groupName;

    @Column(name = "created_date_time", insertable = true, updatable = false)
    private Instant createdDateTime;

    @ManyToOne()
    @JoinColumn(name = "created_by_user", nullable = false, updatable = false)
    private User createdByUser;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "group", orphanRemoval = true)
    private Set<Membership> memberships = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "parent")
    private Group parent;

    @Column(name = "paid_for")
    private boolean paidFor;

    @Column(name = "group_token_code", nullable = true, insertable = true, updatable = true, unique = true)
    private String groupTokenCode;

    @Column(name = "token_code_expiry", nullable = true, insertable = true, updatable = true)
    private Instant tokenExpiryDateTime;

    @Version
    private Integer version;

    /*
     used to calculate when a reminder must be sent, before the eventStartTime
     when the event is created and if parentGroup is set it will default to a value in group
     if group = null or group.reminderminutes = 0, then it will use the site value in properties file
      */
    @Column(name = "reminderminutes")
    private int reminderMinutes;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "group_roles",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
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
    Adding a 'discoverable' field, so group owners can mark if they want others to be able to find them, and a link
    to the UID of the user who will authorize this (UID so we can set it blank and leave nullable)
     */
    @Column(name = "discoverable", nullable = false)
    private boolean discoverable;

    @ManyToOne(optional = true)
    @JoinColumn(name= "join_approver_id", nullable = true)
    private User joinApprover;

    /* Adding an optional field that allows longer descriptions than just the group name, which has to be kept short */
    @Column(name = "description", nullable = false)
    private String description;

    @OneToMany(mappedBy = "parentGroup")
    private Set<Todo> todos = new HashSet<>();

	/**
     * These are all descendant logbooks contained maybe in other non-group entities beneath this group.
     * This does not include logbooks under subgroups!!!
     */
    @OneToMany(mappedBy = "ancestorGroup")
    private Set<Todo> descendantTodos = new HashSet<>();

    @OneToMany(mappedBy = "parentGroup")
    private Set<Event> events = new HashSet<>();

    /**
     * These are all descendant events contained maybe in other non-group entities beneath this group.
     * This does not include events under subgroups!!!
     */
    @OneToMany(mappedBy = "ancestorGroup")
    private Set<Event> descendantEvents = new HashSet<>();

	/**
     * Children groups are not managed using this collections (use 'parent' field for that),
     * just using it for reading
     */
    @OneToMany(mappedBy = "parent")
    private Set<Group> children = new HashSet<>();

    @Column(name="avatar")
    private byte[] image;

    @Column(name="avatar_format")
    private String imageUrl;

    @Column(name="default_image")
    @Enumerated(EnumType.STRING)
    private GroupDefaultImage defaultImage;

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
        this.createdDateTime = Instant.now();
        this.active = true;
        this.discoverable = true; // make groups discoverable by default
        this.joinApprover = createdByUser; // discoverable groups need a join approver, defaulting to creating user
        this.parent = parent;
        this.reminderMinutes = 24 * 60; // defaults to a day
        this.description = ""; // at some point may want to add to the constructor
        this.defaultImage = GroupDefaultImage.SOCIAL_MOVEMENT;

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

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public ZonedDateTime getCreatedDateTimeAtSAST() { // used in Thymeleaf
        return DateTimeUtil.convertToUserTimeZone(createdDateTime, DateTimeUtil.getSAST());
    }

    public void setCreatedDateTime(Instant createdDateTime) {
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

    public Set<User> getMembersWithChildrenIncluded() {
        Set<User> users = new HashSet<>();
        collectGroupMembers(users, Group::isActive);
        return users;
    }

	/**
     * Adding group members to given set of users, and repeating it recursively for each child.
     */
    private void collectGroupMembers(Set<User> users, Predicate<Group> childFilter) {
        users.addAll(getMembers());
        for (Group child : children) {
            if (childFilter.test(child)) {
                child.collectGroupMembers(users, childFilter);
            }
        }
    }

    public Set<Membership> addMembers(Collection<User> newMembers) {
        return addMembers(newMembers, BaseRoles.ROLE_ORDINARY_MEMBER);
    }

    public Set<Membership> addMembers(Collection<User> newMembers, String roleName) {
        Objects.requireNonNull(roleName);

        Role role = getRole(roleName);
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
        Role role = getRole(roleName);
        return addMember(newMember, role);
    }

    public Membership addMember(User newMember, Role role) {
        Objects.requireNonNull(newMember);
        Objects.requireNonNull(role);

        if (!getGroupRoles().contains(role)) {
            throw new IllegalArgumentException("Role " + role + " is not one of roles belonging to group: " + this);
        }
        Membership membership = new Membership(this, newMember, role, Instant.now());
        boolean added = this.memberships.add(membership);
        if (added) {
            newMember.addMappedByMembership(membership);
            return membership;
        }
        return null;
    }

    public Membership removeMember(User member) {
        Membership membership = getMembership(member);
        if (membership == null) {
            return null;
        }
        removeMembership(membership);
        return membership;
    }

    public boolean removeMembership(Membership membership) {
        Objects.requireNonNull(membership);
        boolean removed = this.memberships.remove(membership);
        if (removed) {
            membership.getUser().removeMappedByMembership(membership);
        }
        return removed;
    }

    public void removeMemberships(Set<String> phoneNumbers) {
        Objects.requireNonNull(phoneNumbers);
        Set<Membership> memberships = this.memberships.stream()
                .filter(membership -> phoneNumbers.contains(membership.getUser().getPhoneNumber()))
                .collect(Collectors.toSet());

        this.memberships.removeAll(memberships);
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

    public Role getRole(String roleName) {
        Objects.requireNonNull(roleName);
        return groupRoles.stream()
                .filter(role -> role.getName().equals(roleName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No role under name " + roleName + " found in group " + this));
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

    public Instant getTokenExpiryDateTime() {
        return tokenExpiryDateTime;
    }

    public void setTokenExpiryDateTime(Instant tokenExpiryDateTime) {
        this.tokenExpiryDateTime = tokenExpiryDateTime;
    }

    public boolean hasValidGroupTokenCode() {
        return (groupTokenCode != null && groupTokenCode.trim() != "") &&
                (tokenExpiryDateTime != null && tokenExpiryDateTime.isAfter(Instant.now()));
    }

    public Set<Event> getEvents() {
        if (events == null) {
            events = new HashSet<>();
        }
        return new HashSet<>(events);
    }

    public Set<Event> getUpcomingEventsIncludingParents(Predicate<Event> filter) {
        Set<Event> events = new HashSet<>();

        Instant time = Instant.now();
        Group group = this;
        do {
            boolean parentGroup = !group.equals(this);
            events.addAll(group.getUpcomingEventsInternal(filter, time, parentGroup));
            group = group.getParent();
        } while (group != null);

        return events;
    }

    public Set<Event> getUpcomingEvents(Predicate<Event> filter) {
        Instant time = Instant.now();
        return getUpcomingEventsInternal(filter, time, false);
    }

    private Set<Event> getUpcomingEventsInternal(Predicate<Event> filter, Instant time, boolean onlyIncludingSubgroups) {
        return getEvents().stream()
                .filter(event ->
                        filter.test(event) &&
                        !event.isCanceled() &&
                        event.getEventStartDateTime().isAfter(time) &&
                        (!onlyIncludingSubgroups || event.isIncludeSubGroups()))
                .collect(Collectors.toSet());
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

    public User getJoinApprover() { return joinApprover; }

    public void setJoinApprover(User joinApprover) { this.joinApprover = joinApprover; }

    // inserting this in the getter, to avoid having to reset group description,
    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public GroupDefaultImage getDefaultImage() { return defaultImage; }

    public void setDefaultImage(GroupDefaultImage defaultImage) { this.defaultImage = defaultImage; }


    public Set<Role> getGroupRoles() {
        if (groupRoles == null) {
            groupRoles = new HashSet<>();
        }
        return new HashSet<>(groupRoles);
    }

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = Instant.now();
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
    public String getName() { return getName(""); }

    @Override
    public JpaEntityType getJpaEntityType() {
        return JpaEntityType.GROUP;
    }

    public Set<Todo> getTodos() {
        if (todos == null) {
            todos = new HashSet<>();
        }
        return todos;
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

        return getUid() != null ? getUid().equals(group.getUid()) : group.getUid() == null;

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

    @Override
    public int compareTo(Group group) {
        if (uid.equals(group.getUid())) {
            return 0;
        } else {
            Instant otherCreatedDateTime = group.getCreatedDateTime();
            return createdDateTime.compareTo(otherCreatedDateTime);
        }
    }



}
