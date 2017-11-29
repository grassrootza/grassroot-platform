package za.org.grassroot.integration.location;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.UserLocationLog;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.UserLocationLogRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.integration.exception.LocationNotAvailableException;
import za.org.grassroot.integration.location.aatmodels.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.specifications.UserLogSpecifications.forUser;
import static za.org.grassroot.core.specifications.UserLogSpecifications.ofType;

/**
 * Created by luke on 2017/04/19.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "grassroot.ussd.location.service", havingValue = "aat_soap", matchIfMissing = false)
public class AatSoapLocationBrokerImpl implements UssdLocationServicesBroker {

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
        log.info("About to call adding user lookup, for user phone: {}", user.getPhoneNumber());
        // we already have the permission, so store that as a log, then save if successful
        userLogRepository.save(new UserLog(userUid, UserLogType.GAVE_LOCATION_PERMISSION, "", grantedThroughInterface));
        AddAllowedMsisdnResponse response = aatSoapClient.addAllowedMsisdn(user.getPhoneNumber(), 2);
        if (isAddRequestSuccessful(response)) {
            log.info("Response succeeded, with result: {}", response.toString());
            UserLog successLog = new UserLog(userUid, UserLogType.LOCATION_PERMISSION_ENABLED,
                    "message from server", grantedThroughInterface);
            userLogRepository.save(successLog);
            return true;
        } else {
            log.info("response failed! looks like: {}", response.toString());
            return false;
        }
    }

    @Override
    @Transactional
    public boolean removeUssdLocationLookup(String userUid, UserInterfaceType interfaceType) {
        User user = userRepository.findOneByUid(userUid);
        log.info("About to remove a user from LBS lookup, for phone: {}", user.getPhoneNumber());
        UserLogType logType = UserInterfaceType.SYSTEM.equals(interfaceType) ?
                UserLogType.ONCE_OFF_LBS_REVERSAL : UserLogType.REVOKED_LOCATION_PERMISSION;
        userLogRepository.save(new UserLog(userUid, logType, "", interfaceType));
        RemoveAllowedMsisdnResponse response = aatSoapClient.removeAllowedMsisdn(user.getPhoneNumber());
        if (isRemoveRequestSuccessful(response)) {
            userLogRepository.save(new UserLog(userUid, UserLogType.LOCATION_PERMISSION_REMOVED,
                    "message from server", interfaceType));
            return true;
        } else {
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserGivenLocationPermission(String userUid) {
        List<UserLog> permissionLogs = userLogRepository.findAll(Specifications
                .where(ofType(UserLogType.GAVE_LOCATION_PERMISSION))
                .or(ofType(UserLogType.REVOKED_LOCATION_PERMISSION)),
                new Sort(Sort.Direction.DESC, "creationTime"));
        return permissionLogs != null && !permissionLogs.isEmpty()
                && UserLogType.GAVE_LOCATION_PERMISSION.equals(permissionLogs.get(0).getUserLogType());
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
        DebugUtil.transactionRequired("inside storing user location log");
        User user = userRepository.findOneByUid(userUid);
        // todo : will want to use the accuracy score ...
        GetLocationResponse response = aatSoapClient.getLocationResponse(user.getPhoneNumber());
        GeoLocation location = getLocationFromResposne(response);
        Instant timeOfCoord = getLocationInstant(response);
        if (location.getLongitude() == 0 || location.getLatitude() == 0) {
            throw new LocationNotAvailableException();
        }
        log.info("saving user location log ...");
        userLocationLogRepository.save(new UserLocationLog(timeOfCoord, userUid, location,
                LocationSource.LOGGED_APPROX));
        return location;
    }

    @Async
    @Override
    public CompletableFuture<GeoLocation> getUssdLocation(String userUid) {
        User user = userRepository.findOneByUid(userUid);
        GetLocationResponse response = aatSoapClient.getLocationResponse(user.getPhoneNumber());
        GeoLocation location = getLocationFromResposne(response);
        log.info("okay, done on this thread");
        return CompletableFuture.completedFuture(location);
    }

    // here: deal with the awfulness of the AAT XML schema
    private boolean isAddRequestSuccessful(AddAllowedMsisdnResponse response) {
        AddAllowedMsisdnResponse.AddAllowedMsisdnResult result = response.getAddAllowedMsisdnResult();
        Integer resultCode = result.getAddAllowedMsisdn().getResult().getCode();
        String message = result.getAddAllowedMsisdn().getResult().getMessage();
        log.info("Content of result: code = {}, message = {}", resultCode, message);
        return resultCode == 100 || resultCode == 101;
    }

    // retaining this just in case need it again in future
    private boolean isAddRequestSuccessfulString(AddAllowedMsisdn2Response response) {
        log.info("Content of result: " + response.getAddAllowedMsisdn2Result());
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource src = new InputSource();
            src.setCharacterStream(new StringReader(response.getAddAllowedMsisdn2Result()));

            Document doc = builder.parse(src);
            doc.getDocumentElement().normalize();

            Element resultElement = (Element) doc.getElementsByTagName("result").item(0);
            int resultCode = Integer.parseInt(resultElement.getAttribute("code"));
            String resultDescription = resultElement.getAttribute("message");
            log.info("resultCode: {}, resultDescription: {}", resultCode, resultDescription);

            return resultCode == 100 || resultCode == 101;
        } catch (ParserConfigurationException|IOException|SAXException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isRemoveRequestSuccessful(RemoveAllowedMsisdnResponse response) {
        return response.getRemoveAllowedMsisdnResult().getContent().contains("completed");
    }

    private boolean isLbsLookupAllowed(QueryAllowedMsisdnResponse response) {
        return false;
    }

    private GeoLocation getLocationFromResposne(GetLocationResponse response) {
        GetLocationEntity entity = response.getGetLocationResult().getLocation();

        // todo: store accuracy?
        log.info("Got location back, with X: {}, Y: {}, accuracy: {}", entity.getResult().getX(),
                entity.getResult().getY(), entity.getResult().getAccuracy());
        return new GeoLocation(entity.getResult().getY(), entity.getResult().getX());
    }

    private Instant getLocationInstant(GetLocationResponse response) {
        return Instant.ofEpochMilli(response.getGetLocationResult().getLocation().getResult().getCoordDate());
    }


}
