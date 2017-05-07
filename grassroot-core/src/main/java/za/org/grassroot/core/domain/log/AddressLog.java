package za.org.grassroot.core.domain.log;

import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Address;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.LocationHolder;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2017/05/05.
 * note: intentionally not storing user in here
 */
@Entity
@Table(name = "address_log", uniqueConstraints =
        @UniqueConstraint(name = "uk_address_log_uid", columnNames = "uid"))
public class AddressLog implements ActionLog, LocationHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, length = 50)
    private String uid;

    @ManyToOne
    private Address address;

    @Column(name="creation_time", nullable = false, updatable = false)
    private Instant creationTime;

    @Basic
    @Column(name="description")
    private String description;

    private GeoLocation location;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 50, nullable = false)
    private LocationSource locationSource;

    private AddressLog() {
        // for JPA
    }

    public AddressLog(Address address, GeoLocation location, LocationSource source, String description) {
        this.address = Objects.requireNonNull(address);
        this.location = Objects.requireNonNull(location);
        this.locationSource = Objects.requireNonNull(source);
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
        this.description = description;
    }

    public String getUid() {
        return uid;
    }

    public Address getAddress() {
        return address;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public GeoLocation getLocation() {
        return location;
    }

    @Override
    public boolean hasLocation() {
        return true;
    }

    @Override
    public LocationSource getSource() {
        return locationSource;
    }

    public void setLocation(GeoLocation location) {
        this.location = location;
    }

    public LocationSource getLocationSource() {
        return locationSource;
    }

    public void setLocationSource(LocationSource locationSource) {
        this.locationSource = locationSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AddressLog that = (AddressLog) o;

        return uid.equals(that.uid);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }

    @Override
    public String toString() {
        return "AddressLog{" +
                "address=" + address +
                ", description='" + description + '\'' +
                ", location=" + location +
                ", locationSource=" + locationSource +
                '}';
    }
}
