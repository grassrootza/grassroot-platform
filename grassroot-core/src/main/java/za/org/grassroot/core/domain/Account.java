package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Created by luke on 2015/10/18.
 * note: For naming this entity, there could be confusion with a 'user account', but since we rarely use that terminology,
 * better that than 'institution', which seems like it would set us up for trouble (the term is loaded) down the road.
 */

@Entity
@Table(name="paid_account")
public class Account implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @ManyToOne
    @JoinColumn(name = "created_by_user", nullable = false, updatable = false)
    private User createdByUser;

    @Basic
    @Column(name="created_date_time", insertable = true, updatable = false)
    private Instant createdDateTime;

    /*
    Doing this as one-to-many from account to users, rather than the inverse, because we are (much) more likely to have
    an account with 2-3 administrators than to have a user administering two accounts. The latter is not a non-zero
    possibility, but until/unless we have (very) strong user demand, catering to it is not worth many-to-many overheads
     */
    @OneToMany(mappedBy = "accountAdministered")
    private Set<User> administrators = new HashSet<>();

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    private Set<PaidGroup> paidGroups = new HashSet<>();

    @Basic
    @Column(name = "account_name")
    private String accountName;

    @Basic
    @Column(name = "primary_email")
    private String primaryEmail;

    /*
    Now a set of flags for which features the account has enabled (all will set to 'true' at first)
     */

    @Basic
    @Column
    private boolean enabled; // for future, in case we want to toggle a non-paying account on/off

    @Basic
    @Column(name="free_form")
    private boolean freeFormMessages;

    @Basic
    @Column(name="relayable")
    private boolean relayableMessages;

    @Basic
    @Column(name="action_todo_extra")
    private boolean todoExtraMessages;

    @Version
    private Integer version;

    /*
    Constructors
     */

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = Instant.now();
        }
    }

    private Account() {
        // For JPA
    }

    public Account(User createdByUser, String accountName) {
        Objects.requireNonNull(createdByUser);
        Objects.requireNonNull(accountName);

        this.uid = UIDGenerator.generateId();

        this.accountName = accountName;
        this.createdByUser = createdByUser;

        this.enabled = true;
        this.freeFormMessages = true;
        this.relayableMessages = true;
        this.todoExtraMessages = true;

    }

    /*
    Getters and setters
     */

    public Long getId() {
        return id;
    }

    public String getUid() { return uid; }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public User getCreatedByUser() { return createdByUser; }

    public Set<User> getAdministrators() {
        return administrators;
    }

    public void setAdministrators(Set<User> administrators) {
        this.administrators = administrators;
    }

    public Set<PaidGroup> getPaidGroups() {
        return paidGroups;
    }

    public void setPaidGroups(Set<PaidGroup> paidGroups) {
        this.paidGroups = paidGroups;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getPrimaryEmail() {
        return primaryEmail;
    }

    public void setPrimaryEmail(String primaryEmail) {
        this.primaryEmail = primaryEmail;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFreeFormMessages() {
        return freeFormMessages;
    }

    public void setFreeFormMessages(boolean freeFormMessages) {
        this.freeFormMessages = freeFormMessages;
    }

    public boolean isRelayableMessages() {
        return relayableMessages;
    }

    public void setRelayableMessages(boolean relayableMessages) {
        this.relayableMessages = relayableMessages;
    }

    public boolean isTodoExtraMessages() { return todoExtraMessages; }

    public void setTodoExtraMessages(boolean todoExtraMessages) { this.todoExtraMessages = todoExtraMessages; }

    /*
    Helper methods for adding and removing administrators and groups
     */

    public void addAdministrator(User administrator) {
        administrators.add(administrator);
    }

    public void removeAdministrator(User administrator) {
        administrators.remove(administrator);
    }

    public void addPaidGroup(PaidGroup paidGroup) {
        paidGroups.add(paidGroup);
    }

    public void removePaidGroup(PaidGroup paidGroup) {
        paidGroups.remove(paidGroup);
    }

    public Integer getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Account)) {
            return false;
        }

        Account account = (Account) o;

        return getUid() != null ? getUid().equals(account.getUid()) : account.getUid() == null;
    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", createdDateTime=" + createdDateTime +
                ", accountName=" + accountName +
                ", primaryEmail=" + primaryEmail +
                ", enabled=" + enabled +
                ", free form messages='" + freeFormMessages + '\'' +
                ", relayable messages='" + relayableMessages + '\'' +
//                ", number administrators='" + administrators.size() + '\'' +
//                ", number groups paid for='" + paidGroups.size() + '\'' +
                '}';
    }

}
