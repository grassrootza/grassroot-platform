package za.org.grassroot.core.domain;

import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by luke on 2015/10/18.
 * note: For naming this entity, there could be confusion with a 'user account', but since we rarely use that terminology,
 * better that than 'institution', which seems like it would set us up for trouble (the term is loaded) down the road.
 */

@Entity
@Table(name="paid_account")
@EqualsAndHashCode
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Basic
    @Column(name="created_date_time", insertable = true, updatable = false)
    private Timestamp createdDateTime;

    /*
    Doing this as one-to-many from account to users, rather than the inverse, because we are (much) more likely to have
    an account with 2-3 administrators than to have a user administering two accounts. The latter is not a non-zero
    possibility, but until/unless we have (very) strong user demand, catering to it is not worth many-to-many overheads
     */
    @OneToMany(mappedBy = "accountAdministered")
    private List<User> administrators;

    @OneToMany(mappedBy = "payingAccount")
    private List<Group> groupsPaidFor;

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

    /*
    Constructors
     */

    public Account() {
    }

    public Account(String accountName, User administrator) {

        this.accountName = accountName;

        this.administrators = new ArrayList<>();
        this.administrators.add(administrator);

        this.enabled = true;
        this.freeFormMessages = true;
        this.relayableMessages = true;

    }

    public Account(String accountName, boolean enabled) {

        this.accountName = accountName;
        this.enabled = enabled;
        this.freeFormMessages = enabled;
        this.relayableMessages = enabled;

    }

    public Account(String accountName, String primaryEmail, User administrator, boolean enabled) {

        this.accountName = accountName;
        this.administrators = new ArrayList<>();
        this.administrators.add(administrator);
        this.primaryEmail = primaryEmail;

        this.enabled = enabled;
        this.freeFormMessages = freeFormMessages;
        this.relayableMessages = relayableMessages;

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

    public List<User> getAdministrators() {
        return administrators;
    }

    public void setAdministrators(List<User> administrators) {
        this.administrators = administrators;
    }

    public List<Group> getGroupsPaidFor() {
        return groupsPaidFor;
    }

    public void setGroupsPaidFor(List<Group> groupsPaidFor) {
        this.groupsPaidFor = groupsPaidFor;
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
                ", number administrators='" + administrators.size() + '\'' +
                ", number groups paid for='" + groupsPaidFor.size() + '\'' +
                '}';
    }

}
