package za.org.grassroot.integration.location;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.UserRepository;

import java.util.concurrent.CompletableFuture;

/**
 * Created by luke on 2017/04/28.
 */
@Service @Slf4j
@ConditionalOnProperty(name = "grassroot.ussd.location.service", havingValue = "test_local", matchIfMissing = false)
public class StubLocationBrokerImpl implements UssdLocationServicesBroker {

    private final boolean returnValue = false;

    private final UserRepository userRepository;

    @Autowired
    public StubLocationBrokerImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean addUssdLocationLookupAllowed(String userUid, UserInterfaceType grantedThroughInterface) {
        User user = userRepository.findOneByUid(userUid);
        log.info("Add ussd location called, user name: {}, phone: {}", user.getName(), user.getPhoneNumber());
        return returnValue;
    }

    @Override
    public boolean removeUssdLocationLookup(String userUid, UserInterfaceType revokedThroughInterface) {
        log.info("Removing user ussd location, user name: {}, phone: {}");
        return returnValue;
    }

    @Override
    public boolean isUssdLocationLookupAllowed(String userUid) {
        return returnValue;
    }

    @Override
    public GeoLocation getUssdLocationForUser(String userUid) {
        double testLat = 31.2;
        double testLong = 31.2;
        return new GeoLocation(testLat, testLong);
    }

    @Override
    public CompletableFuture<GeoLocation> getUssdLocation(String userUid) {
        return null;
    }

    @Override
    public boolean hasUserGivenLocationPermission(String userUid) { return returnValue; }
}
