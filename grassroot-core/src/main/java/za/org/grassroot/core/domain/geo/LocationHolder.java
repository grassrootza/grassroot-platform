package za.org.grassroot.core.domain.geo;

import za.org.grassroot.core.enums.LocationSource;

/**
 * Created by luke on 2017/04/06.
 */
public interface LocationHolder {

    GeoLocation getLocation();

    boolean hasLocation();

    LocationSource getSource();

}
