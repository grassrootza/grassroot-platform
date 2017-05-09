package za.org.grassroot.core.domain;

import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.LocationHolder;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;

/**
 * Created by paballo on 2016/07/12.
 */

@Entity
@Table(name = "address")
public class Address implements LocationHolder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true, length = 50)
    private String uid;

    @Column(name="house_number")
    private String house;

    @Column(name ="street_name")
    private String street;

    @Column(name = "area")
    private String neighbourhood;

    /*@Column(name = "city")
    private String city;*/

    @Column(name = "created_date_time", nullable = false, updatable = false)
    private Instant createdDateTime;

    // think about whether to make this many to many if multiple users at same address
    @ManyToOne
    @JoinColumn(name = "resident_user_id", nullable = true, updatable = true)
    private User resident;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="latitude", column = @Column(nullable = true)),
            @AttributeOverride(name="longitude", column = @Column(nullable = true))
    })
    private GeoLocation location;

    @Column(name = "location_source", nullable = true)
    private LocationSource locationSource;

    private Address(){
        // for JPA
    }

    public Address(User resident) {
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.resident = resident;
    }

    public Address(User resident, String houseNumber, String streetName, String town) {
        this(resident);
        this.house = houseNumber;
        this.street = streetName;
        this.neighbourhood = town;
        // this.city = city;
    }


    public Long getId() {
        return id;
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

    public String getHouse() {
        return house;
    }

    public void setHouse(String house) {
        this.house = house;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getNeighbourhood() {
        return neighbourhood;
    }

    public void setNeighbourhood(String neighbourhood) {
        this.neighbourhood = neighbourhood;
    }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    /*public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }*/

    @Override
    public GeoLocation getLocation() {
        return location;
    }

    @Override
    public boolean hasLocation() {
        return location != null;
    }

    @Override
    public LocationSource getSource() {
        return locationSource;
    }

    public void setLocationSource(LocationSource locationSource) {
        this.locationSource = locationSource;
    }

    public boolean hasHouseAndStreet() {
        return !StringUtils.isEmpty(this.house) || !StringUtils.isEmpty(this.street);
    }

    @Override
    public String toString() {
        return "Address{" +
                "house='" + house + '\'' +
                ", street='" + street + '\'' +
                ", neighbourhood='" + neighbourhood + '\'' +
                ", resident=" + resident +
                '}';
    }
}
