package za.org.grassroot.core.domain;

import org.springframework.util.StringUtils;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;

/**
 * Created by paballo on 2016/07/12.
 */

@Entity
@Table(name = "address")
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Column(name="house_number")
    private String houseNumber;

    @Column(name ="street_name")
    private String streetName;

    @Column(name ="area")
    private String town;

    @Column(name = "created_date_time", nullable = false,insertable = true, updatable = false)
    private Instant createdDateTime;

    @ManyToOne()
    @JoinColumn(name = "resident_user_id", nullable = false, updatable = false)
    private User resident;

    private Address(){

    }

    public Address(User resident) {
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.resident = resident;
    }

    public Address(User resident, String houseNumber, String streetName, String town){
        this(resident);
        this.houseNumber = houseNumber;
        this.streetName = streetName;
        this.town = town;
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getResident() {
        return resident;
    }

    public void setResident(User resident) {
        this.resident = resident;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getHouseNumber() {
        return houseNumber;
    }

    public void setHouseNumber(String houseNumber) {
        this.houseNumber = houseNumber;
    }

    public String getStreetName() {
        return streetName;
    }

    public void setStreetName(String streetName) {
        this.streetName = streetName;
    }

    public String getTown() {
        return town;
    }

    public void setTown(String town) {
        this.town = town;
    }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Instant createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public boolean hasHouseAndStreet() {
        return !StringUtils.isEmpty(this.houseNumber) || !StringUtils.isEmpty(this.streetName);
    }

    @Override
    public String toString() {
        return "Address{" +
                "id=" + id +
                ", uid='" + uid + '\'' +
                ", houseNumber='" + houseNumber + '\'' +
                ", streetName='" + streetName + '\'' +
                ", town='" + town + '\'' +
                ", createdDateTime=" + createdDateTime +
                ", resident=" + resident +
                '}';
    }
}
