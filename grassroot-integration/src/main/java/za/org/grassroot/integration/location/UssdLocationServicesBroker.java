package za.org.grassroot.integration.location;

import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.util.concurrent.CompletableFuture;

/**
 * Created by luke on 2017/04/19.
 */
public interface UssdLocationServicesBroker {

    // the interface type is to record where/how permission given (e.g., in future might be able to
    // give it through Android)
    boolean addUssdLocationLookupAllowed(String userUid, UserInterfaceType grantedThroughInterface);

    boolean removeUssdLocationLookup(String userUid, UserInterfaceType revokedThroughInterface);

    boolean hasUserGivenLocationPermission(String userUid);

    boolean isUssdLocationLookupAllowed(String userUid);

    GeoLocation getUssdLocationForUser(String userUid);

    CompletableFuture<GeoLocation> getUssdLocation(String userUid);

}
