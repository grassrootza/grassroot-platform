package za.org.grassroot.core.domain;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.validator.routines.EmailValidator;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.FormatUtil.removeUnwantedCharacters;
import static za.org.grassroot.core.util.PhoneNumberUtil.invertPhoneNumber;

@Entity @Getter
@Table(name = "user_profile")  //table name needs to be quoted in SQL because 'user' is a reserved keyword
public class User implements GrassrootEntity, UserDetails, Comparable<User> {
    private static final int DEFAULT_NOTIFICATION_PRIORITY = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Column(name = "phone_number", nullable = true, length = 20, unique = true)
    @Setter private String phoneNumber;

    @Column(name = "email_address", nullable = true, unique = true) // enforcing one user per email add.
    private String emailAddress;

    @Column(name = "first_name")
    @Setter private String firstName;

    @Column(name = "last_name")
    @Setter private String lastName;

    @Column(name = "display_name", nullable = true, length = 70) // allowing this to be nullable as might not be set
    private String displayName;

    @Column(name = "language_code", nullable = true, length = 10)
    @Setter private String languageCode;

    @Column(name = "created_date_time", updatable = false, nullable = false)
    @Getter private Instant createdDateTime;

    @Column(name = "user_name", length = 50, unique = true)
    @Setter private String username;

    @Column(name = "password")
    @Setter private String password;

    @Column(name = "notification_priority")
    @Setter private Integer notificationPriority;

    @Column(name = "web")
    @Setter private boolean hasWebProfile = false;

    @Column(name = "android")
    @Getter @Setter private boolean hasAndroidProfile = false;

    @Column(name = "whatsapp")
    @Getter @Setter private boolean whatsAppOptedIn = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_preference", nullable = false, length = 50)
    @Getter @Setter private DeliveryRoute messagingPreference;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_preference", length = 50)
    @Setter private AlertPreference alertPreference;

    @Enumerated(EnumType.STRING)
    @Column(name = "province", length = 50)
    @Setter private Province province;

    @Column(name = "enabled")
    @Setter private boolean enabled = true;

    // We use this to differentiate between users who have initiated a G/R session on their own, and those who have just
    // been added via being part of another group -- to us in our stats, plus for some use cases (e.g., asking for language)
    @Column(name = "initiated_session")
    @Setter @Getter private boolean hasInitiatedSession;

    @Column(name = "has_set_name")
    @Setter private boolean hasSetOwnName;

    @ManyToOne
    @JoinColumn(name = "safety_group_id")
    @Setter private Group safetyGroup;

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
    @Setter @Getter private boolean hasUsedFreeTrial;

    @Basic
    @Column(name = "livewire_contact")
    @Setter @Getter private boolean liveWireContact;

    // both of these could be done by looking up logs and image records, but this entity is already
    // quite encumbered, and booleans are light, so trade-off runs in favour of denormalizing here
    @Basic
    @Column(name = "has_image")
    @Getter @Setter private boolean hasImage;

    @Basic
    @Column(name = "contact_error")
    @Getter @Setter private boolean contactError;

    // note: keep an eye on this in profiling, make sure it is super lazy (i.e., join table not hit at all), else drop on this side
    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "administrators")
    private Set<Account> accountsAdministered = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @LazyCollection(LazyCollectionOption.EXTRA)
    private Set<CampaignLog> campaignLogs;

    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, mappedBy = "assignedMembers")
    private Set<Event> events = new HashSet<>();

    private User() {
        // for JPA
    }

    public User(String phoneNumber, String displayName, String emailAddress) {
        if (StringUtils.isEmpty(phoneNumber) && StringUtils.isEmpty(emailAddress)) {
            throw new IllegalArgumentException("Phone number and email address cannot both be null!");
        }
        if (!StringUtils.isEmpty(emailAddress) && !EmailValidator.getInstance().isValid(emailAddress)) {
            throw new IllegalArgumentException("Email address, if provided, must be valid");
        }
        this.uid = UIDGenerator.generateId();
        // next two lines prevent duplicates being thrown on empty string
        this.phoneNumber = StringUtils.isEmpty(phoneNumber) ? null : phoneNumber;
        this.emailAddress = StringUtils.isEmpty(emailAddress) ? null : emailAddress;
        this.username = StringUtils.isEmpty(phoneNumber) ? emailAddress : phoneNumber;
        this.displayName = removeUnwantedCharacters(displayName);
//        this.languageCode = "en";
        this.messagingPreference = !StringUtils.isEmpty(phoneNumber) ? DeliveryRoute.SMS : DeliveryRoute.EMAIL_GRASSROOT; // as default
        this.createdDateTime = Instant.now();
        this.alertPreference = AlertPreference.NOTIFY_NEW_AND_REMINDERS;
        this.hasUsedFreeTrial = false;
    }

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = Instant.now();
        }
    }

    /**
     * We use this static constructor because no-arg constructor should be only used by JPA
     *
     * @return user
     */
    public static User makeEmpty() {
        User user = new User();
        user.uid = UIDGenerator.generateId();
        return user;
    }

    public String getName() { return nameToDisplay(); }

    public String getNationalNumber() { return PhoneNumberUtil.formattedNumber(phoneNumber); }

    public String getDisplayName() {
        return removeUnwantedCharacters(displayName);
    }

    public void setDisplayName(String displayName) {
        this.displayName = removeUnwantedCharacters(displayName);
    }

    public Locale getLocale() {
        return (languageCode == null || languageCode.trim().isEmpty()) ? Locale.ENGLISH : new Locale(languageCode);
    }

    public boolean hasLanguage() {
        return !(languageCode == null || (languageCode.equals("en") && !hasInitiatedSession));
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public boolean hasPhoneNumber() {
        return !StringUtils.isEmpty(phoneNumber);
    }

    public boolean hasEmailAddress() {
        return !StringUtils.isEmpty(emailAddress);
    }

    public boolean hasPassword() { return !StringUtils.isEmpty(password); }

    public boolean isUsernameEmailAddress() {
        // since we are guaranteed that no phone number will ever validate as an email
        return EmailValidator.getInstance().isValid(username);
    }

    public boolean areNotificationsByEmail() {
        return DeliveryRoute.EMAIL_ROUTES.contains(this.messagingPreference) ||
                StringUtils.isEmpty(this.phoneNumber);
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

    public Membership getGroupMembership(String groupId) {
        return getMemberships().stream().filter(m -> m.getGroup().getUid().equalsIgnoreCase(groupId)).findFirst().orElse(null);
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

    public boolean hasSafetyGroup() {
        return safetyGroup != null;
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

    public void removeAllStdRoles() {
        this.standardRoles.clear();
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

    public int getNotificationPriority() {
        if (notificationPriority == null) {
            return DEFAULT_NOTIFICATION_PRIORITY;
        }
        return notificationPriority;
    }

    //~=================================================================================================================

    public boolean hasName() {
        return (displayName != null && displayName.trim().length() > 0);
    }

    public String getName(String unknownPrefix) {
        if (displayName != null && displayName.trim().length() > 0) {
            return displayName;
        } else if (unknownPrefix.trim().length() == 0) {
            return !StringUtils.isEmpty(phoneNumber) ? invertPhoneNumber(phoneNumber) : emailAddress;
        } else {
            return unknownPrefix + " (" + invertPhoneNumber(phoneNumber) + ")";
        }
    }

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
