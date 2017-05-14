package za.org.grassroot.core.domain;

import org.hibernate.validator.constraints.Email;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.PhoneNumberUtil.invertPhoneNumber;

@Entity
@Table(name = "user_profile")  //table name needs to be quoted in SQL because 'user' is a reserved keyword
public class User implements GrassrootEntity, UserDetails, Comparable<User> {
    private static final int DEFAULT_NOTIFICATION_PRIORITY = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Column(name = "phone_number", nullable = false, length = 20, unique = true)
    private String phoneNumber;

    @Email
    @Column(name = "email_address")
    private String emailAddress;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "display_name", nullable = true, length = 70) // allowing this to be nullable as might not be set
    private String displayName;

    @Column(name = "language_code", nullable = true, length = 10)
    private String languageCode;

    @Column(name = "created_date_time", updatable = false, nullable = false)
    private Instant createdDateTime;

    @Column(name = "user_name", length = 50, unique = true)
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "notification_priority")
    private Integer notificationPriority;

    @Column(name = "web")
    private boolean hasWebProfile = false;

    @Column(name = "android")
    private boolean hasAndroidProfile = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_preference", nullable = false, length = 50)
    private UserMessagingPreference messagingPreference;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_preference", length = 50)
    private AlertPreference alertPreference;

    @Column(name = "enabled")
    private boolean enabled = true;

    @Column(name = "initiated_session")
    private boolean hasInitiatedSession;

    @Column(name = "has_set_name")
    private boolean hasSetOwnName;

    @ManyToOne
    @JoinColumn(name = "safety_group_id")
    private Group safetyGroup;

    @Version
    private Integer version;

    @OneToMany
    @JoinTable(name = "user_roles",
            joinColumns = {@JoinColumn(name = "user_id", referencedColumnName = "id", unique = false)},
            inverseJoinColumns = {@JoinColumn(name = "role_id", referencedColumnName = "id", unique = false)})
    private Set<Role> standardRoles = new HashSet<>();

    @OneToMany(mappedBy = "user")
    private Set<Membership> memberships = new HashSet<>();

    // this is the Grassroot Extra account that the user themselves runs
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_account")
    private Account primaryAccount;

    @Column(name = "free_trial_used")
    private boolean hasUsedFreeTrial;

    @Basic
    @Column(name = "livewire_contact")
    private boolean liveWireContact;

    // note: keep an eye on this in profiling, make sure it is super lazy (i.e., join table not hit at all), else drop on this side
    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "administrators")
    private Set<Account> accountsAdministered = new HashSet<>();

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
        this.createdDateTime = Instant.now();
        this.alertPreference = AlertPreference.NOTIFY_NEW_AND_REMINDERS;
        this.hasUsedFreeTrial = false;
    }

    /**
     * We use this static constructor because no-arg constructor should be only used by JPA
     *
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

    public String getUid() {
        return uid;
    }

    public String getName() { return nameToDisplay(); }

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

    public String getNationalNumber() { return PhoneNumberUtil.formattedNumber(phoneNumber); }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isHasSetOwnName() { return hasSetOwnName; }

    public void setHasSetOwnName(boolean hasSetOwnName) { this.hasSetOwnName = hasSetOwnName; }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public boolean hasEmailAddress() {
        return !StringUtils.isEmpty(emailAddress);
    }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public Account getPrimaryAccount() {
        return primaryAccount;
    }

    public void setPrimaryAccount(Account primaryAccount) {
        this.primaryAccount = primaryAccount;
        if (primaryAccount != null) { // since can reset to not having a primary account, but adding a null account causes an error
            addAccountAdministered(primaryAccount);
            primaryAccount.addAdministrator(this);
        }
    }

    public Set<Membership> getMemberships() {
        if (memberships == null) {
            memberships = new HashSet<>();
        }
        return new HashSet<>(memberships);
    }

    public Set<Group> getGroups() {
        return getMemberships().stream()
                .map(Membership::getGroup)
                .collect(Collectors.toSet());
    }

    /**
     * This is just used to manually set inverse side of many-to-many relationship when it  is still not saved in db.
     * Afterwards Hibernate takes care to set both sides.
     *
     * @param membership membership
     */
    public void addMappedByMembership(Membership membership) {
        this.memberships.add(membership);
    }

    /**
     * This is just used to manually set inverse side of many-to-many relationship when it  is still not saved in db.
     * Afterwards Hibernate takes care to set both sides.
     *
     * @param membership membership
     */
    public void removeMappedByMembership(Membership membership) {
        this.memberships.remove(membership);
    }

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = Instant.now();
        }
    }

    public Group getSafetyGroup() {
        return safetyGroup;
    }

    public void setSafetyGroup(Group safetyGroup) {
        this.safetyGroup = safetyGroup;
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

    public boolean isHasWebProfile() {
        return hasWebProfile;
    }

    public boolean hasAndroidProfile() {
        return hasAndroidProfile;
    }

    public boolean hasSafetyGroup() {
        return safetyGroup != null;
    }

    public UserMessagingPreference getMessagingPreference() {
        return messagingPreference;
    }

    public void setHasAndroidProfile(boolean hasAndroidProfile) {
        this.hasAndroidProfile = hasAndroidProfile;
    }

    public void setHasWebProfile(boolean hasWebProfile) {
        this.hasWebProfile = hasWebProfile;
    }

    public void setMessagingPreference(UserMessagingPreference messagingPreference) {
        this.messagingPreference = messagingPreference;
    }

    public AlertPreference getAlertPreference() {
        return alertPreference;
    }

    public void setAlertPreference(AlertPreference alertPreference) {
        this.alertPreference = alertPreference;
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

    public void addStandardRole(Role role) {
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
    Whether the user can be contacted by LiveWire subscribers
     */

    public boolean isLiveWireContact() {
        return liveWireContact;
    }

    public void setLiveWireContact(boolean liveWireContact) {
        this.liveWireContact = liveWireContact;
    }

    /*
        We use the next set to handle Grassroot Extra accounts
         */
    public Set<Account> getAccountsAdministered() {
        if (accountsAdministered == null) {
            accountsAdministered = new HashSet<>();
        }
        return new HashSet<>(accountsAdministered);
    }

    public void setAccountsAdministered(Set<Account> accountsAdministered) {
        Objects.requireNonNull(accountsAdministered);
        this.accountsAdministered = accountsAdministered;
    }

    public void addAccountAdministered(Account account) {
        Objects.requireNonNull(account);
        if (accountsAdministered == null) {
            accountsAdministered = new HashSet<>();
        }
        this.accountsAdministered.add(account);
    }

    public void removeAccountAdministered(Account account) {
        Objects.requireNonNull(account);
        if (accountsAdministered == null) {
            throw new IllegalArgumentException("Cannot remove an account from admin set when set is empty");
        }
        this.accountsAdministered.remove(account);
        // as above
        account.removeAdministrator(this);
    }

    public boolean hasMultipleAccounts() {
        return accountsAdministered != null && accountsAdministered.size() > 1;
    }

    public boolean isHasUsedFreeTrial() {
        return hasUsedFreeTrial;
    }

    public void setHasUsedFreeTrial(boolean hasUsedFreeTrial) {
        this.hasUsedFreeTrial = hasUsedFreeTrial;
    }

    /*
    We use this to differentiate between users who have initiated a G/R session on their own, and those who have just
    been added via being part of another group -- to us in our stats, plus for some use cases (e.g., asking for language)
     */

    public boolean isHasInitiatedSession() {
        return hasInitiatedSession;
    }

    public void setHasInitiatedSession(boolean hasInitiatedSession) {
        this.hasInitiatedSession = hasInitiatedSession;
    }

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
        return this.enabled;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public int getNotificationPriority() {
        if (notificationPriority == null) {
            return DEFAULT_NOTIFICATION_PRIORITY;
        }
        return notificationPriority;
    }

    public void setNotificationPriority(Integer notificationPriority) {
        this.notificationPriority = notificationPriority;
    }

    //~=================================================================================================================

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
    public String nameToDisplay() {
        return getName("");
    }

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
        sb.append(", username='").append(username).append('\'');
        sb.append(", enabled=").append(enabled);
        sb.append(", emailAddress=").append(emailAddress);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int compareTo(User user) {
        if (uid.equals(user.getUid())) {
            return 0;
        } else {
            if (displayName != null && user.getDisplayName() != null) {
                return displayName.compareTo(user.getDisplayName());
            } else {
                // note : this may be strange depending on Ascii values
                return phoneNumber.compareTo(user.getPhoneNumber());
            }
        }
    }
}
