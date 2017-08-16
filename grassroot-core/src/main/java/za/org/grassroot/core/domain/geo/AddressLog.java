package za.org.grassroot.core.domain.geo;

import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AddressLogType;
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

    @Column(name="creation_time", nullable = false, updatable = false)
    private Instant creationTime;

    @ManyToOne
    private Address address;

    @ManyToOne
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50, nullable = false)
    private AddressLogType type;

    @Basic
    @Column(name="description")
    private String description;

    private GeoLocation location;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 50, nullable = false)
    private LocationSource locationSource;

    public static class Builder {
        private Address address;
        private User user;
        private AddressLogType type;
        private GeoLocation location;
        private LocationSource source;
        private String description;

        public Builder address(Address address) {
            this.address = address;
            return this;
        }

        public Builder user(User user) {
            this.user = user;
            return this;
        }

        public Builder type(AddressLogType type) {
            this.type = type;
            return this;
        }

        public Builder location(GeoLocation location) {
            this.location = location;
            return this;
        }

        public Builder source(LocationSource source) {
            this.source = source;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public AddressLog build() {
            Objects.requireNonNull(address);
            Objects.requireNonNull(location);
            Objects.requireNonNull(source);
            Objects.requireNonNull(type);
            AddressLog addressLog = new AddressLog(address, location, source, type);
            addressLog.setUser(user);
            addressLog.setDescription(description);
            return addressLog;
        }
    }

    private AddressLog() {
        // for JPA
    }

    private AddressLog(Address address, GeoLocation location, LocationSource source, AddressLogType type) {
        this.address = Objects.requireNonNull(address);
        this.location = Objects.requireNonNull(location);
        this.locationSource = Objects.requireNonNull(source);
        this.type = Objects.requireNonNull(type);
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
    }

    public String getUid() {
        return uid;
    }

    public Address getAddress() {
        return address;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public AddressLogType getType() {
        return type;
    }

    public void setType(AddressLogType type) {
        this.type = type;
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
