package za.org.grassroot.core.domain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

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

    @Column(name = "app_id", nullable = false, unique = true)
    private String appId;

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

    @ManyToMany(cascade = CascadeType.ALL, mappedBy = "groupMembers", fetch = FetchType.LAZY) // not sure which cascade type is going to be best
    private List<Group> groupsPartOf;

    @Column(name = "user_name", length = 50, unique = true)
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "web")
    private boolean hasWebProfile = false;

    @Column(name = "enabled")
    private boolean enabled = true;

    @Column(name = "lastUssdMenu")
    private String lastUssdMenu;

    @Column(name = "initiated_session")
    private boolean hasInitiatedSession;

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns        = {@JoinColumn(name = "user_id", referencedColumnName = "id", unique = false)},
            inverseJoinColumns = {@JoinColumn(name = "role_id", referencedColumnName = "id", unique = false)}
    )
    private Set<Role> roles = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "account_administered")
    private Account accountAdministered;

    @Version
    private Integer version;

    public User() {
        // for JPA
    }

    public User(String appId) {
        this(appId, null);
    }

    public User(String appId, String phoneNumber) {
        this(appId, phoneNumber, null);
    }

    public User(String appId, String phoneNumber, String displayName) {
        this.appId = Objects.requireNonNull(appId);
        this.phoneNumber = phoneNumber;
        this.username = phoneNumber;
        this.displayName = displayName;
        this.languageCode = "en";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAppId() {
        return appId;
    }

    void setAppId(String appId) {
        this.appId = appId;
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

    public void setCreatedDateTime(Timestamp createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public Account getAccountAdministered() { return accountAdministered; }
    public void setAccountAdministered(Account accountAdministered) { this.accountAdministered = accountAdministered; }

    public List<Group> getGroupsPartOf() {
        return groupsPartOf;
    }

    public void setGroupsPartOf(List<Group> groupsPartOf) {
        this.groupsPartOf = groupsPartOf;
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

    public void setHasWebProfile(boolean hasWebProfile) { this.hasWebProfile = hasWebProfile; }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public  void addRole(Role role) { this.roles.add(role); }

    public void removeRole(Role role) { this.roles.remove(role); }

    public  void addRole(Set<Role> roles)
    {
        this.roles.addAll(roles);
    }

    public String getLastUssdMenu() { return lastUssdMenu; }

    public void setLastUssdMenu(String lastUssdMenu) { this.lastUssdMenu = lastUssdMenu; }


    /*
    We use this to differentiate between users who have initiated a G/R session on their own, and those who have just
    been added via being part of another group -- to us in our stats, plus for some use cases (e.g., asking for language)
     */

    public boolean isHasInitiatedSession() { return hasInitiatedSession; }

    public void setHasInitiatedSession(boolean hasInitiatedSession) { this.hasInitiatedSession = hasInitiatedSession; }

    @Transient
    public Set<Permission> getPermissions() {
        Set<Permission> perms = new HashSet<Permission>();
        for (Role role : roles) {
            perms.addAll(role.getPermissions());
        }
        return perms;
    }

    @Override
    @Transient
    public Collection<GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>();
        authorities.addAll(getRoles());
        authorities.addAll(getPermissions());
        return authorities;
    }

    @Transient
    @Override
    public boolean isAccountNonExpired() {
        //return true = account is valid / not expired
        return true;
    }

    @Transient
    @Override
    public boolean isAccountNonLocked() {
        //return true = account is not locked
        return true;
    }

    @Transient
    @Override
    public boolean isCredentialsNonExpired() {
        //return true = password is valid / not expired
        return true;
    }

    @Transient
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

    /**
     * Inserting string functions to handle phone numbers here, for the moment
     * todo: remove these to use only the Util class
     */

    public static String invertPhoneNumber(String storedNumber, String joinString) {

        // todo: handle error if number has gotten into database in incorrect format
        // todo: make this much faster, e.g., use a simple regex / split function?
        String prefix = String.join("", Arrays.asList("0", storedNumber.substring(2, 4)));
        String midnumbers, finalnumbers;

        try {
            midnumbers = storedNumber.substring(4, 7);
            finalnumbers = storedNumber.substring(7, 11);
        } catch (Exception e) { // in case the string doesn't have enough digits ...
            midnumbers = storedNumber.substring(4);
            finalnumbers = "";
        }

        return String.join(joinString, Arrays.asList(prefix, midnumbers, finalnumbers));

    }

    public static String invertPhoneNumber(String storedNumber) {
        return invertPhoneNumber(storedNumber, " ");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        User user = (User) o;

        if (appId != null ? !appId.equals(user.appId) : user.appId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return appId != null ? appId.hashCode() : 0;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("User{");
        sb.append("id=").append(id);
        sb.append(", appId='").append(appId).append('\'');
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
