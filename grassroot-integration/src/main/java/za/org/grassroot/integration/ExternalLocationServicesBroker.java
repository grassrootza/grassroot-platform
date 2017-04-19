package za.org.grassroot.integration;

import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.UserInterfaceType;

/**
 * Created by luke on 2017/04/19.
 */
public interface ExternalLocationServicesBroker {

    // the interface type is to record where/how permission given (e.g., in future might be able to
    // give it through Android)
    boolean addUssdLocationLookupAllowed(String userUid, UserInterfaceType grantedThroughInterface);

    boolean isUssdLocationLookupAllowed(String userUid);

    GeoLocation getUssdLocationForUser(String userUid);

}
