package za.org.grassroot.core.domain;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import java.sql.Timestamp;
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
@EqualsAndHashCode
//@ToString
public class User implements UserDetails {

    private String      firstName;
    private String      lastName;
    private String      phoneNumber;
    private String      displayName;
    private String      languageCode;
    private Long        id;
    private Timestamp   createdDateTime;
    private List<Group> groupsPartOf;
    private String      username;
    private String      password;
    private boolean   webProfile = false;
    private boolean   enabled    = true;
    private String lastUssdMenu;
    private boolean hasInitiatedSession;
    private Set<Role> roles      = new HashSet<>();
    private Account accountAdministered;
    private Integer version;


    @Column(name = "first_name")
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @Column(name = "last_name")
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Basic
    @Column(name = "phone_number", nullable = false, length = 20, unique = true)
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Basic
    @Column(name = "display_name", nullable = true, length = 70) // allowing this to be nullable as might not be set
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Basic
    @Column(name = "language_code", nullable = true, length = 10)
    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
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

    @OneToMany(mappedBy = "user")
    private List<Group> groupsCreated;

    @OneToMany(mappedBy = "user")
    private List<Event> eventsCreated;

    @ManyToOne
    @JoinColumn(name = "account_administered")
    public Account getAccountAdministered() { return accountAdministered; }
    public void setAccountAdministered(Account accountAdministered) { this.accountAdministered = accountAdministered; }


    @ManyToMany(cascade = CascadeType.ALL, mappedBy = "groupMembers", fetch = FetchType.EAGER) // not sure which cascade type is going to be best
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

    @Column(name = "user_name", length = 50, unique = true)
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }


    @Column(name = "password")
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Column(name = "web")
    public boolean getWebProfile() { return webProfile; }

    public void setWebProfile(boolean webProfile) { this.webProfile = webProfile; }

    @Column(name = "enabled")
    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns        = {@JoinColumn(name = "user_id", referencedColumnName = "id", unique = false)},
            inverseJoinColumns = {@JoinColumn(name = "role_id", referencedColumnName = "id", unique = false)}
    )
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

    @Column(name = "lastUssdMenu")
    public String getLastUssdMenu() { return lastUssdMenu; }

    public void setLastUssdMenu(String lastUssdMenu) { this.lastUssdMenu = lastUssdMenu; }


    /*
    We use this to differentiate between users who have initiated a G/R session on their own, and those who have just
    been added via being part of another group -- to us in our stats, plus for some use cases (e.g., asking for language)
     */

    @Column(name = "initiated_session")
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

    @Version
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
        Timestamp minutesAgo = new Timestamp(System.currentTimeMillis() - (timeLimit * 60 * 1000));
        if (createdDateTime == null || createdDateTime.after(minutesAgo))
            return false;
        return true;
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

    public Group needsToRenameGroup() {
        // todo: for speed reasons, probably need a SQL query to do this, rather than this loop, but using a loop for now
        if (groupsCreated == null || groupsCreated.size() == 0) return null;
        for (Group groupCreated : groupsCreated) {
            if (!groupCreated.hasName()) return groupCreated;
        }
        return null;
    }

    /**
     * Inserting string functions to handle phone numbers here, for the moment
     * todo: remove these to use only the Util class
     */

    public static String invertPhoneNumber(String storedNumber, String joinString) {

        // todo: handle error if number has gotten into database in incorrect format
        // todo: make this much faster, e.g., use a simple regex / split function?

        List<String> numComponents = new ArrayList<>();
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

    /*
    Constructors
    */

    public User() {
    }

    public User(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.languageCode = "en";
    }

    public User(String phoneNumber, String displayName) {
        this.phoneNumber = phoneNumber;
        this.displayName = displayName;
        this.languageCode = "en";
    }

    @Override
    public String toString() {
        return "User{" +
                "firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", displayName='" + displayName + '\'' +
                ", languageCode='" + languageCode + '\'' +
                ", id=" + id +
                ", createdDateTime=" + createdDateTime +
//                ", groupsPartOf=" + groupsPartOf +
                ", username='" + username + '\'' +
//                ", password='" + password + '\'' +
                ", enabled=" + enabled +
//                ", roles=" + roles +
                ", version=" + version +
//                ", groupsCreated=" + groupsCreated +
//                ", eventsCreated=" + eventsCreated +
                '}';
    }

    /**
     * Override equals and hashCode methods, may need in future (since Lombok ones require everything equal, vs just
     * the unique fields).

    @Override
    public int hashCode() {
        int result = phoneNumber != null ? phoneNumber.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        // result = 31 * result + (createdDateTime != null ? createdDateTime.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        final User user = (User) o;

        if (id != null ? !id.equals(user.id) : user.id != null) { return false; }
        if (phoneNumber != null ? !phoneNumber.equals(user.phoneNumber) : user.phoneNumber != null) { return false; }

        return true;

    }*/
}
