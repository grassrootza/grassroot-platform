package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;

/**
 * Created by paballo on 2016/03/14.
 */
@Entity
@Table(name ="user_create_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_create_request_uid", columnNames = "uid"))
public class UserCreateRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, length = 50)
    private String uid;

    @Column(name = "phone_number",insertable = true, nullable = false, unique = true)
    private String phoneNumber;

    @Column(name = "user_password", nullable = false)
    private String password;

    @Column(name="display_name", insertable = true)
    private String displayName;

    @Column(name = "creation_time", nullable = false)
    private Instant creationTime;

    private UserCreateRequest() {
        // for JPA
    }

    public UserCreateRequest(String phoneNumber, String displayName, String password, Instant creationTime) {
        this.uid = UIDGenerator.generateId();
        this.phoneNumber = phoneNumber;
        this.displayName = displayName;
        this.password = password;
        this.creationTime = creationTime;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setCreationTime(Instant creationTime){this.creationTime =creationTime;}

    public Long getId() {
        return id;
    }

    public String getUid() { return uid; }

    public Instant getCreationTime() { return creationTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof UserCreateRequest)) {
            return false;
        }

        UserCreateRequest that = (UserCreateRequest) o;

        return getUid() != null ? getUid().equals(that.getUid()) : that.getUid() == null;

    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("UserCreateRequest{");
        sb.append("id=").append(id);
        sb.append(", uid='").append(uid).append('\'');
        sb.append(", phoneNumber=").append(phoneNumber);
        sb.append(", displayName=").append(displayName);
        sb.append('}');
        return sb.toString();
    }
}
