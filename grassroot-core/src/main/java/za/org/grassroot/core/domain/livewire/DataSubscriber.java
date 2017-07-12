package za.org.grassroot.core.domain.livewire;

import org.hibernate.annotations.Type;
import org.hibernate.validator.constraints.Email;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.DataSubscriberType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static za.org.grassroot.core.util.StringArrayUtil.listToArray;
import static za.org.grassroot.core.util.StringArrayUtil.listToArrayRemoveDuplicates;

/**
 * Created by luke on 2017/05/05.
 */
@Entity
@Table(name = "data_subscriber",
        uniqueConstraints = @UniqueConstraint(name = "uk_data_subscriber_uid", columnNames = {"uid"}))
public class DataSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Basic
    @Column(name = "uid", unique = true, nullable = false, updatable = false)
    private String uid;

    @Basic
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Basic
    @Column(name = "creation_time", nullable = false, updatable = false)
    private Instant creationTime;

    @ManyToOne
    @JoinColumn(name = "created_by_user", nullable = false, updatable = false)
    private User creatingUser;

    @ManyToOne
    @JoinColumn(name = "administrator", nullable = false)
    private User administrator;

    @Basic
    @Column(name = "active")
    private boolean active;

    @Email
    @Column(name = "primary_email", nullable = false)
    private String primaryEmail;

    // email addresses that get sent wire alerts (may not be attached to a GR account)
    @Column(name = "push_emails")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    private String[] emailsForPushNotifications;

    // since we're using an array instead of yet one more table, storing the UIDs instead of entity
    @Column(name = "access_users")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    private String[] userUidsWithAccess;

    // whether users of this subscriber can tag alerts
    @Basic
    @Column(name = "can_tag")
    private boolean canTag;

    @Basic
    @Column(name = "can_release")
    private boolean canRelease;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscriber_type", length = 50, nullable = false)
    private DataSubscriberType subscriberType;

    @Version
    private Integer version;

    private DataSubscriber() {
        // for JPA
    }

    public DataSubscriber(User creatingUser, User administrator, String displayName, String primaryEmail, boolean active, DataSubscriberType subscriberType) {
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
        this.creatingUser = creatingUser;
        this.administrator = administrator;
        this.primaryEmail = primaryEmail;
        this.displayName = displayName;
        this.active = active;
        this.emailsForPushNotifications = new String[0];
        this.userUidsWithAccess = new String[0];
        this.canTag = false;
        this.subscriberType = subscriberType;
    }

    public String getUid() {
        return uid;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public ZonedDateTime getCreationTimeAtSAST() {
        return DateTimeUtil.convertToUserTimeZone(creationTime, DateTimeUtil.getSAST());
    }

    public User getCreatingUser() {
        return creatingUser;
    }

    public User getAdministrator() {
        return administrator;
    }

    public void setAdministrator(User administrator) {
        this.administrator = administrator;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getPrimaryEmail() {
        return primaryEmail;
    }

    public void setPrimaryEmail(String primaryEmail) {
        this.primaryEmail = primaryEmail;
    }

    public boolean hasPushEmails() {
        return emailsForPushNotifications != null && emailsForPushNotifications.length > 0;
    }

    public List<String> getPushEmails() {
        return new ArrayList<>(Arrays.asList(getEmailsForPushNotifications()));
    }

    public String[] getEmailsForPushNotifications() {
        if (emailsForPushNotifications == null) {
            emailsForPushNotifications = new String[0];
        }
        return emailsForPushNotifications;
    }

    public void setEmailsForPushNotifications(String[] emailsForPushNotifications) {
        this.emailsForPushNotifications = emailsForPushNotifications;
    }

    public void addEmailsForPushNotification(List<String> emails) {
        // use getter in case the array is null at beginning (will make sure it no longer is
        ArrayList<String> modifiedList = new ArrayList<>(Arrays.asList(getEmailsForPushNotifications()));
        modifiedList.addAll(emails);
        emailsForPushNotifications = listToArray(modifiedList);
    }

    public void removeEmailsForPushNotification(List<String> emails) {
        ArrayList<String> modifiedList = new ArrayList<>(Arrays.asList(getEmailsForPushNotifications()));
        modifiedList.removeAll(emails);
        emailsForPushNotifications = listToArray(modifiedList);
    }

    public List<String> getUsersWithAccess() {
        return new ArrayList<>(Arrays.asList(getUserUidsWithAccess()));
    }

    public String[] getUserUidsWithAccess() {
        if (userUidsWithAccess == null) {
            userUidsWithAccess = new String[0];
        }
        return userUidsWithAccess;
    }

    public void setUserUidsWithAccess(String[] userUidsWithAccess) {
        this.userUidsWithAccess = userUidsWithAccess;
    }

    public void addUserUidsWithAccess(final Set<String> userUids) {
        ArrayList<String> list = new ArrayList<>(Arrays.asList(getUserUidsWithAccess()));
        userUids.stream()
                .filter(u -> !list.contains(u))
                .forEach(list::add);
        list.addAll(userUids);
        userUidsWithAccess = listToArrayRemoveDuplicates(list);
    }

    public boolean isCanTag() {
        return canTag;
    }

    public void setCanTag(boolean canTag) {
        this.canTag = canTag;
    }

    public boolean isCanRelease() {
        return canRelease;
    }

    public void setCanRelease(boolean canRelease) {
        this.canRelease = canRelease;
    }

    public void removeUserUidsWithAccess(final Set<String> userUids) {
        ArrayList<String> list = new ArrayList<>(Arrays.asList(getUserUidsWithAccess()));
        list.removeAll(userUids);
        userUidsWithAccess = listToArray(list);
    }

    public Integer getVersion() { return version; }

    public DataSubscriberType getSubscriberType() {
        return subscriberType;
    }

    public void setSubscriberType(DataSubscriberType subscriberType) {
        this.subscriberType = subscriberType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DataSubscriber that = (DataSubscriber) o;

        return uid.equals(that.uid);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }

    @Override
    public String toString() {
        return "DataSubscriber{" +
                "uid='" + uid + '\'' +
                ", creationTime=" + creationTime +
                ", administrator=" + administrator +
                ", active=" + active +
                ", primaryEmail='" + primaryEmail + '\'' +
                ", emailsForPushNotifications=" + Arrays.toString(emailsForPushNotifications) +
                ", userUidsWithAccess=" + Arrays.toString(userUidsWithAccess) +
                '}';
    }
}