package za.org.grassroot.core.domain;

import org.hibernate.annotations.Type;
import org.hibernate.validator.constraints.Email;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;

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
    @Type(type = "za.org.grassroot.core.util.GenericArrayUserType")
    private String[] emailsForPushNotifications;

    // since we're using an array instead of yet one more table, storing the UIDs instead of entity
    @Column(name = "access_users")
    @Type(type = "za.org.grassroot.core.util.GenericArrayUserType")
    private String[] userUidsWithAccess;

    public DataSubscriber(User creatingUser, User administrator, String primaryEmail) {
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
        this.creatingUser = creatingUser;
        this.administrator = administrator;
        this.primaryEmail = primaryEmail;
    }

    public String getUid() {
        return uid;
    }

    public Instant getCreationTime() {
        return creationTime;
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

    public String[] getEmailsForPushNotifications() {
        if (emailsForPushNotifications == null) {
            emailsForPushNotifications = new String[0];
        }
        return emailsForPushNotifications;
    }

    public void setEmailsForPushNotifications(String[] emailsForPushNotifications) {
        this.emailsForPushNotifications = emailsForPushNotifications;
    }

    public void addEmailForPushNotification(String email) {
        // use getter in case the array is null at beginning (will make sure it no longer is
        ArrayList<String> modifiedList = new ArrayList<>(Arrays.asList(getEmailsForPushNotifications()));
        modifiedList.add(email);
        emailsForPushNotifications = listToArray(modifiedList);
    }

    public void removeEmailForPushNotification(String email) {
        ArrayList<String> modifiedList = new ArrayList<>(Arrays.asList(getEmailsForPushNotifications()));
        modifiedList.remove(email);
        emailsForPushNotifications = listToArray(modifiedList);
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

    public void addUserUidWithAccess(final String userUid) {
        ArrayList<String> list = new ArrayList<>(Arrays.asList(getUserUidsWithAccess()));
        list.add(userUid);
        userUidsWithAccess = listToArray(list);
    }

    public void removeUserUidWithAccess(final String userUid) {
        ArrayList<String> list = new ArrayList<>(Arrays.asList(getUserUidsWithAccess()));
        list.remove(userUid);
        userUidsWithAccess = listToArray(list);
    }

    private String[] listToArray(ArrayList<String> list) {
        String[] array = new String[list.size()];
        return list.toArray(array);
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