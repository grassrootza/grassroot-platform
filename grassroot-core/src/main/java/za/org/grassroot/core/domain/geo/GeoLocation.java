package za.org.grassroot.core.domain.geo;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.security.InvalidParameterException;

@Embeddable
public class GeoLocation {
    private final static double MIN_LATITUDE = -90.00;
    private final static double MAX_LATITUDE = 90.00;
    private final static double MIN_LONGITUDE = -180.00;
    private final static double MAX_LONGITUDE = 180.00;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    private GeoLocation () {
        // for JPA
    }

    public GeoLocation (double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // strip down an object location for use in calculations
    public GeoLocation (ObjectLocation objectLocation) {
        this.latitude = objectLocation.getLatitude();
        this.longitude = objectLocation.getLongitude();
    }

    public double getLatitude () {
        return latitude;
    }

    public double getLongitude () {
        return longitude;
    }

    public int calculateDistanceInMetersFrom (GeoLocation location) {
        double earthRadius = 6371000; //meters
        double dLat = Math.toRadians(location.getLatitude() - this.getLatitude());
        double dLng = Math.toRadians(location.getLongitude() - this.getLongitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(this.getLatitude())) * Math.cos(
                Math.toRadians(location.getLatitude())) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) (earthRadius * c);
    }

    @Override
    public boolean equals (Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GeoLocation that = (GeoLocation) o;

        if (Double.compare(that.latitude, latitude) != 0) {
            return false;
        }
        return Double.compare(that.longitude, longitude) == 0;
    }

    @Override
    public int hashCode () {
        int result;
        long temp;
        temp = Double.doubleToLongBits(latitude);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(longitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString () {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("lat=").append(latitude);
        sb.append(", long=").append(longitude);
        sb.append('}');
        return sb.toString();
    }

    public boolean isValid () {
        if (this.latitude < MIN_LATITUDE || this.latitude > MAX_LATITUDE ||
                this.longitude < MIN_LONGITUDE || this.longitude > MAX_LONGITUDE) {
            return false;
        }
        return true;
    }
}
