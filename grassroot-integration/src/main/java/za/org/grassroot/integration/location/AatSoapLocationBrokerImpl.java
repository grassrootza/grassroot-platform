package za.org.grassroot.integration.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.UserLocationLog;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.UserLocationLogRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.exception.LocationNotAvailableException;
import za.org.grassroot.integration.location.aatmodels.AddAllowedMsisdnResponse;
import za.org.grassroot.integration.location.aatmodels.GetLocationResponse;
import za.org.grassroot.integration.location.aatmodels.QueryAllowedMsisdnResponse;

import java.time.Instant;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.specifications.UserLogSpecifications.forUser;
import static za.org.grassroot.core.specifications.UserLogSpecifications.ofType;

/**
 * Created by luke on 2017/04/19.
 */
@Service("AAT_SOAP_LBS")
@ConditionalOnProperty(name = "ussd.location.service", havingValue = "aat_soap", matchIfMissing = false)
public class AatSoapLocationBrokerImpl implements UssdLocationServicesBroker {

    private static final Logger logger = LoggerFactory.getLogger(AatSoapLocationBrokerImpl.class);

    private final AatSoapClient aatSoapClient;
    private final UserRepository userRepository;
    private final UserLogRepository userLogRepository;
    private final UserLocationLogRepository userLocationLogRepository;

    @Autowired
    public AatSoapLocationBrokerImpl(AatSoapClient aatSoapClient, UserRepository userRepository, UserLogRepository userLogRepository, UserLocationLogRepository userLocationLogRepository) {
        this.aatSoapClient = aatSoapClient;
        this.userRepository = userRepository;
        this.userLogRepository = userLogRepository;
        this.userLocationLogRepository = userLocationLogRepository;
    }

    @Override
    @Transactional
    public boolean addUssdLocationLookupAllowed(String userUid, UserInterfaceType grantedThroughInterface) {
        User user = userRepository.findOneByUid(userUid);
        AddAllowedMsisdnResponse response = aatSoapClient.addAllowedMsisdn(user.getPhoneNumber(), 0);
        if (isAddRequestSuccessful(response)) {
            UserLog successLog = new UserLog(userUid, UserLogType.LOCATION_PERMISSION_ENABLED,
                    "message from server", grantedThroughInterface);
            userLogRepository.save(successLog);
            return true;
        } else {
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUssdLocationLookupAllowed(String userUid) {
        boolean hasUserLog = userLogRepository.count(where(forUser(userUid))
                .and(ofType(UserLogType.LOCATION_PERMISSION_ENABLED))) > 0;
        if (!hasUserLog) {
            return false;
        } else {
            User user = userRepository.findOneByUid(userUid);
            QueryAllowedMsisdnResponse response = aatSoapClient.queryAllowedMsisdnResponse(user.getPhoneNumber());
            return isLbsLookupAllowed(response);
        }
    }

    @Override
    @Transactional
    public GeoLocation getUssdLocationForUser(String userUid) {
        User user = userRepository.findOneByUid(userUid);
        try {
            // todo : will need to use the accuracy score ...
            GetLocationResponse response = aatSoapClient.getLocationResponse(user.getPhoneNumber());
            GeoLocation location = getLocationFromResposne(response);
            Instant timeOfCoord = getLocationInstant(response);
            userLocationLogRepository.save(new UserLocationLog(timeOfCoord, userUid, location));
            return location;
        } catch (Exception e) {
            throw new LocationNotAvailableException();
        }
    }

    // here: deal with the awfulness of the AAT XML schema
    private boolean isAddRequestSuccessful(AddAllowedMsisdnResponse response) {
        return response.getAddAllowedMsisdnResult().getContent().contains("approved");
    }

    private boolean isLbsLookupAllowed(QueryAllowedMsisdnResponse response) {
        return false;
    }

    private GeoLocation getLocationFromResposne(GetLocationResponse response) {
        return null;
    }

    private Instant getLocationInstant(GetLocationResponse response) {
        return null;
    }


}
