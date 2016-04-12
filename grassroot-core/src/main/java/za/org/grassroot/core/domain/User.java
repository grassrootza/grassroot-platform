package za.org.grassroot.core.domain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.PhoneNumberUtil.invertPhoneNumber;

//todo: reconsider if language should be nullable, or not null and set to "en" by default (when set not nullable, broke tests)

//todo: add validation to all model classes
//todo: use java 8 date and time types and a JPA converter instead of Timestamp type
//todo: createdDateTime should be read-only -  the database should insert this automatically
//todo: use field annotations rather than getter annotations because then all the annotations will be closer together
//todo: make these classes immutable - all args constructor and no setters
//todo: id and createdDateTime fields should not be insertable or updatable
//todo: extract base class for all entities that has id and createdDateTime

@Entity
@Table(name = "user_profile")  //table name needs to be quoted in SQL because 'user' is a reserved keyword
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Column(name = "phone_number", nullable = false, length = 20, unique = true)
    private String phoneNumber;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "display_name", nullable = true, length = 70) // allowing this to be nullable as might not be set
    private String displayName;

    @Column(name = "language_code", nullable = true, length = 10)
    private String languageCode;

    @Column(name = "created_date_time", insertable = true, updatable = false)
    private Timestamp createdDateTime;

    @Column(name = "user_name", length = 50, unique = true)
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "web")
    private boolean hasWebProfile = false;

    @Column(name ="android")
    private boolean hasAndroidProfile = false;

    @Column(name = "message_preference", nullable = false)
    private UserMessagingPreference messagingPreference;

    @Column(name = "enabled")
    private boolean enabled = true;

    @Column(name = "initiated_session")
    private boolean hasInitiatedSession;

    @Version
    private Integer version;

    @OneToMany
    @JoinTable(name = "user_roles",
            joinColumns        = {@JoinColumn(name = "user_id", referencedColumnName = "id", unique = false)},
            inverseJoinColumns = {@JoinColumn(name = "role_id", referencedColumnName = "id", unique = false)}
    )
    private Set<Role> standardRoles = new HashSet<>();

    @OneToMany(mappedBy = "user")
    private Set<Membership> memberships = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "account_administered")
    private Account accountAdministered;

    private User() {
        // for JPA
    }

    public User(String phoneNumber) {
        this(phoneNumber, null);
    }

    public User(String phoneNumber, String displayName) {
        this.uid = UIDGenerator.generateId();
        this.phoneNumber = Objects.requireNonNull(phoneNumber);
        this.username = phoneNumber;
        this.displayName = displayName;
        this.languageCode = "en";
        this.messagingPreference = UserMessagingPreference.SMS; // as default
        this.createdDateTime = Timestamp.from(Instant.now());
    }

    /**
     * We use this static constructor because no-arg constructor should be only used by JPA
     * @return user
     */
    public static User makeEmpty() {
        return makeEmpty(UIDGenerator.generateId());
    }

    public static User makeEmpty(String uid) {
        User user = new User();
        user.uid = uid;
        return user;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUid() {
        return uid;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public Account getAccountAdministered() { return accountAdministered; }

    public void setAccountAdministered(Account accountAdministered) { this.accountAdministered = accountAdministered; }

    public Set<Membership> getMemberships() {
        if (memberships == null) {
            memberships = new HashSet<>();
        }
        return new HashSet<>(memberships);
    }

    /**
     * This is just used to manually set inverse side of many-to-many relationship when it  is still not saved in db.
     * Afterwards Hibernate takes care to set both sides.
     * @param membership membership
     */
    public void addMappedByMembership(Membership membership) {
        this.memberships.add(membership);
    }

    /**
     * This is just used to manually set inverse side of many-to-many relationship when it  is still not saved in db.
     * Afterwards Hibernate takes care to set both sides.
     * @param membership membership
     */
    public void removeMappedByMembership(Membership membership) {
        this.memberships.remove(membership);
    }

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isHasWebProfile() { return hasWebProfile; }

    public boolean hasAndroidProfile(){
        return hasAndroidProfile;
    }

    public UserMessagingPreference getMessagingPreference() { return messagingPreference; }

    public void setHasAndroidProfile(boolean hasAndroidProfile){
        this.hasAndroidProfile =hasAndroidProfile;
    }

    public void setHasWebProfile(boolean hasWebProfile) { this.hasWebProfile = hasWebProfile; }

    public void setMessagingPreference(UserMessagingPreference messagingPreference) { this.messagingPreference = messagingPreference; }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<Role> getStandardRoles() {
        if (standardRoles == null) {
            standardRoles = new HashSet<>();
        }
        return new HashSet<>(standardRoles);
    }

    public  void addStandardRole(Role role) {
        Objects.requireNonNull(role);
        if (!role.getRoleType().equals(Role.RoleType.STANDARD)) {
            throw new IllegalArgumentException("Cannot add role directly to user that is not of standard type: " + role);
        }
        this.standardRoles.add(role);
    }

    public void removeStandardRole(Role role) {
        Objects.requireNonNull(role);
        this.standardRoles.remove(role);
    }

    /*
    We use this to differentiate between users who have initiated a G/R session on their own, and those who have just
    been added via being part of another group -- to us in our stats, plus for some use cases (e.g., asking for language)
     */

    public boolean isHasInitiatedSession() { return hasInitiatedSession; }

    public void setHasInitiatedSession(boolean hasInitiatedSession) { this.hasInitiatedSession = hasInitiatedSession; }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();
        Set<Role> roles = getStandardAndGroupRoles();
        authorities.addAll(roles);

        Set<Permission> permissions = roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toSet());
        authorities.addAll(permissions);

        return authorities;
    }

    private Set<Role> getStandardAndGroupRoles() {
        Set<Role> roles = getStandardRoles();
        for (Membership membership : getMemberships()) {
            roles.add(membership.getRole());
        }
        return roles;
    }

    @Override
    public boolean isAccountNonExpired() {
        //return true = account is valid / not expired
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        //return true = account is not locked
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        //return true = password is valid / not expired
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.getEnabled();
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

//~=================================================================================================================

    /**
     * Inserting some methods to deal with users not having names -- might want to move to service layer...
     * May want to switch from a time-based logic on needing to rename to a login count (2nd or 3rd time accessing system)
     */

    public boolean needsToRenameSelf(Integer timeLimit) {
        if (hasName()) return false;
        Timestamp minutesAgo = Timestamp.valueOf(LocalDateTime.now().minusMinutes(timeLimit));
        return (createdDateTime != null && createdDateTime.before(minutesAgo));
    }

    public boolean hasName() {
        return (displayName != null && displayName.trim().length() > 0);
    }

    public String getName(String unknownPrefix) {
        if (displayName != null && displayName.trim().length() > 0) {
            return displayName;
        } else if (unknownPrefix.trim().length() == 0) {
            return invertPhoneNumber(phoneNumber);
        } else {
            return unknownPrefix + " (" + invertPhoneNumber(phoneNumber) + ")";
        }
    }

    // can't call this the more natural getName, or any variant, or Spring's getter handling throws a fit
    // refactoring to avoid confusion with property displayName -- point is this returns name, or phone number if no name
    public String nameToDisplay() { return getName(""); }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof User)) {
            return false;
        }

        User user = (User) o;

        return getUid() != null ? getUid().equals(user.getUid()) : user.getUid() == null;

    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("User{");
        sb.append("id=").append(id);
        sb.append(", uid='").append(uid).append('\'');
        sb.append(", phoneNumber='").append(phoneNumber).append('\'');
        sb.append(", firstName='").append(firstName).append('\'');
        sb.append(", lastName='").append(lastName).append('\'');
        sb.append(", username='").append(username).append('\'');
        sb.append(", enabled=").append(enabled);
        sb.append(", createdDateTime=").append(createdDateTime);
        sb.append('}');
        return sb.toString();
    }

}
