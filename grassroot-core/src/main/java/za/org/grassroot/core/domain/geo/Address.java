package za.org.grassroot.core.domain.geo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;

/**
 * Created by paballo on 2016/07/12.
 */

@Entity
@Table(name = "address") @Getter
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

    @Column(name = "postal_code")
    @Setter private String postalCode;

    @Column(name = "town_or_city")
    @Setter private String townOrCity;

    @Column(name = "created_date_time", nullable = false, updatable = false)
    private Instant createdDateTime;

    @ManyToOne
    @JoinColumn(name = "resident_user_id", nullable = true, updatable = true)
    private User resident;

    @Basic
    @Column(name = "is_primary")
    private boolean primary;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="latitude", column = @Column(nullable = true)),
            @AttributeOverride(name="longitude", column = @Column(nullable = true))
    })
    private GeoLocation location;

    @Column(name = "location_source")
    @Enumerated(EnumType.STRING)
    private LocationSource locationSource;

    private Address(){
        // for JPA
    }

    public Address(User resident, boolean primary) {
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.resident = resident;
        this.primary = primary;
    }

    public Address(User resident, String houseNumber, String streetName, String town, boolean primary) {
        this(resident, primary);
        this.house = houseNumber;
        this.street = streetName;
        this.neighbourhood = town;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public void setHouse(String house) {
        this.house = house;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public void setNeighbourhood(String neighbourhood) {
        this.neighbourhood = neighbourhood;
    }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public void setLocation(GeoLocation location) { this.location = location; }

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

    public boolean hasStreetAndArea() {
        return !StringUtils.isEmpty(this.street) && !StringUtils.isEmpty(this.neighbourhood);
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
