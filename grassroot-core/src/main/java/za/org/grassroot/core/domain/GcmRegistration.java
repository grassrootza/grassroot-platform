package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;

/**
 * Created by paballo on 2016/03/29.
 */
@Entity
@Table(name ="gcm_registration",
        uniqueConstraints = @UniqueConstraint(name = "uk_gcm_registration_uid", columnNames = "uid"))
public class GcmRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, length = 50)
    private String uid;

    @ManyToOne
    private User user;

    @Column(name = "registration_id",insertable = true, nullable = false, unique = true)
    private String registrationId;

    @Column(name = "creation_time", nullable = false)
    private Instant creationTime;

    public GcmRegistration(User user, String registrationId, Instant creationTime) {
        this.uid = UIDGenerator.generateId();
        this.user = user;
        this.registrationId = registrationId;
        this.creationTime = creationTime;
    }
    public  GcmRegistration(){}

    public User getUser() {
        return user;
    }

    public void setUser(String phoneNumber) {
        this.user = user;
    }

    public void setCreationTime(Instant creationTime){this.creationTime =creationTime;}

    public void setRegistrationId(String registrationId){
        this.registrationId =registrationId;
    }
    public String getRegistrationId(){
        return this.registrationId;
    }


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
        if (o == null || !(o instanceof GcmRegistration)) {
            return false;
        }

        GcmRegistration that = (GcmRegistration) o;

        if (getUid() != null ? !getUid().equals(that.getUid()) : that.getUid() != null) {
            return false;
        }

        return true;
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
        sb.append(", user=").append(user);
        sb.append('}');
        return sb.toString();
    }
}


