package za.org.grassroot.services.geo;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.Address;
import za.org.grassroot.core.domain.geo.AddressLog;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.AddressLogRepository;
import za.org.grassroot.core.repository.AddressRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.async.AsyncUserLogger;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static za.org.grassroot.core.specifications.AddressSpecifications.forUser;
import static za.org.grassroot.core.specifications.AddressSpecifications.matchesStreetArea;

/**
 * Created by paballo on 2016/07/14.
 */
@Service
public class AddressBrokerImpl implements AddressBroker {

    private static final Logger log = LoggerFactory.getLogger(AddressBrokerImpl.class);

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final AsyncUserLogger asyncUserLogger;
    private final AddressLogRepository addressLogRepository;

    private final EntityManager entityManager;
    private final RestTemplate restTemplate;

    @Value("${grassroot.geocoding.api.url:http://nominatim.openstreetmap.org/reverse}")
    private String geocodingApiUrl;

    @Value("${grassroot.places.lambda.url:http://localhost:3000}")
    private String placeLookupLambda;

    @Autowired
    public AddressBrokerImpl(UserRepository userRepository, AddressRepository addressRepository, AsyncUserLogger asyncUserLogger, AddressLogRepository addressLogRepository, EntityManager entityManager, RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.asyncUserLogger = asyncUserLogger;
        this.addressLogRepository = addressLogRepository;
        this.entityManager = entityManager;
        this.restTemplate = restTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Address getUserAddress(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        return addressRepository.findTopByResidentAndPrimaryTrueOrderByCreatedDateTimeDesc(user);
    }

    @Override
    @Transactional
    public void updateUserAddress(String userUid, String houseNumber, String street, String town) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        Address address = addressRepository.findTopByResidentAndPrimaryTrueOrderByCreatedDateTimeDesc(user);

        if (address == null) {
            address = createAddress(user);
        }

        if (!StringUtils.isEmpty(houseNumber))
            address.setHouse(houseNumber);
        if (!StringUtils.isEmpty(street))
            address.setStreet(street);
        if (!StringUtils.isEmpty(town))
            address.setNeighbourhood(town);;

        log.info("updated user address");
    }

    private Address createAddress(User resident) {
        Address address = new Address(resident, true);
        addressRepository.save(address);
        asyncUserLogger.recordUserLog(resident.getUid(), UserLogType.ADDED_ADDRESS, "user added address");
        return address;
    }

    @Override
    @Transactional
    public void removeAddress(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        Address address = addressRepository.findTopByResidentAndPrimaryTrueOrderByCreatedDateTimeDesc(user);
        if (address != null) {
            addressRepository.delete(address);
            asyncUserLogger.recordUserLog(user.getUid(), UserLogType.REMOVED_ADDRESS, "user deleted address");
            log.info("deleting user address from db");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAddress(String userUid) {
        final User user = userRepository.findOneByUid(userUid);
        final Address address = addressRepository.findTopByResidentAndPrimaryTrueOrderByCreatedDateTimeDesc(user);
        return address != null && address.hasHouseAndStreet();
    }


    @Override
    @Transactional
    public Address fetchNearestAddress(String userUid, GeoLocation location, int radiusKm, boolean storeForUser) {
        Address returnAddress;
        User user = userRepository.findOneByUid(userUid);
        Address storedPrimary = addressRepository.findTopByResidentAndPrimaryTrueOrderByCreatedDateTimeDesc(user);
        if (storedPrimary != null) {
            returnAddress = storedPrimary;
        } else {
            TypedQuery<Address> addressQuery = entityManager.createQuery("" +
                    "select a from Address a where "
                    + GeoLocationUtils.locationFilterSuffix("a.location") + " " +
                    "order by a.createdDateTime desc", Address.class);
            GeoLocationUtils.addLocationParamsToQuery(addressQuery, location, radiusKm);
            List<Address> potentialAddresses = addressQuery.getResultList();
            log.info("How many addresses?" + potentialAddresses);
            returnAddress = potentialAddresses != null && !potentialAddresses.isEmpty() && potentialAddresses.get(0).hasStreetAndArea()
                    ? potentialAddresses.get(0) : reverseGeoCodeLocation(location, user, UserInterfaceType.SYSTEM, false);
            if (returnAddress != null && storeForUser) {
                storeAddressRawAfterDuplicateCheck(returnAddress);
            }
        }
        return returnAddress;
    }

    @Override
    @Transactional
    public Address getAndStoreAddressFromLocation(String userUid, GeoLocation location, UserInterfaceType userInterfaceType, boolean primary) {
        User user = userRepository.findOneByUid(userUid);
        Address address = reverseGeoCodeLocation(location, user, userInterfaceType, primary);
        if (address != null) {
            Address storedAddress = storeAddressRawAfterDuplicateCheck(address);
            if (primary) {
                address.setPrimary(true);
            }
            return storedAddress;
        } else {
            return null;
        }
    }

    private Address reverseGeoCodeLocation(GeoLocation location, User user, UserInterfaceType interfaceType, boolean primary) {
        try {
            InvertGeoCodeResult result = restTemplate.getForObject(invertGeoCodeRequestURI(location).build(), InvertGeoCodeResult.class);
            return result.getAddress() == null ? null :
                    GeoLocationUtils.convertGeoCodeToAddress(result.getAddress(), user, location, interfaceType, primary);
        } catch (URISyntaxException|HttpClientErrorException e) {
            log.error("Error!", e);
            return null;
        }
    }

    private Address storeAddressRawAfterDuplicateCheck(Address address) {
        address.setPrimary(false); // to make sure we never accidentally override
        Address storedAddress;
        List<Address> duplicateCheck = addressRepository.findAll(Specifications
                        .where(forUser(address.getResident()))
                        .and(matchesStreetArea(address.getHouse(), address.getStreet(), address.getNeighbourhood())),
                new Sort(Sort.Direction.DESC, "createdDateTime"));
        log.info("size of returned address: {}", duplicateCheck.size());
        storedAddress = duplicateCheck.isEmpty() ?
                addressRepository.save(address) : duplicateCheck.iterator().next();
        return storedAddress;
    }

    private URIBuilder invertGeoCodeRequestURI(GeoLocation location) throws URISyntaxException {
        URIBuilder uriBuilder = new URIBuilder(geocodingApiUrl);
        uriBuilder.addParameter("format", "json");
        uriBuilder.addParameter("lat", String.valueOf(location.getLatitude()));
        uriBuilder.addParameter("lon", String.valueOf(location.getLongitude()));
        uriBuilder.addParameter("zoom", "18");
        return uriBuilder;
    }

    @Override
    @Transactional
    public void confirmLocationAddress(String userUid, String addressUid,
                                       GeoLocation location, UserInterfaceType interfaceType) {
        User user = userRepository.findOneByUid(userUid);
        Address storedAddress = addressRepository.findOneByUid(addressUid);
        AddressLog log = new AddressLog.Builder()
                .address(storedAddress)
                .user(user)
                .type(AddressLogType.CONFIRMED_LOCATION)
                .location(location)
                .source(LocationSource.convertFromInterface(interfaceType))
                .description(null).build();
        addressLogRepository.save(log);
    }

    @Transactional
    public void reviseLocationAddress(String userUid, String addressUid, GeoLocation location,
                                      String description, UserInterfaceType interfaceType) {
        User user = userRepository.findOneByUid(userUid);
        Address storedAddress = addressRepository.findOneByUid(addressUid);
        AddressLog log = new AddressLog.Builder()
                .address(storedAddress)
                .user(user)
                .type(AddressLogType.REVISED_DESCRIPTION)
                .location(location)
                .source(LocationSource.convertFromInterface(interfaceType))
                .description(description).build();
        addressLogRepository.save(log);
    }

    @Override
    public List<TownLookupResult> lookupPostCodeOrTown(String postCodeOrTown, Province province) {
        try {
            URIBuilder uriBuilder = new URIBuilder(placeLookupLambda + "/lookup");
            uriBuilder.addParameter("searchTerm", postCodeOrTown.trim());
            if (province != null) {
                uriBuilder.addParameter("province", Province.CANONICAL_NAMES_ZA.getOrDefault(province, ""));
            }

            ResponseEntity<TownLookupResult[]> lookupResult = restTemplate.getForEntity(uriBuilder.build(), TownLookupResult[].class);
            log.info("lookup result: {}", lookupResult);
            return Arrays.asList(lookupResult.getBody());
        } catch (URISyntaxException|RestClientException e) {
            log.error("Error constructing or executing lookup URL: ", e);
            return new ArrayList<>();
        }
    }

    @Override
    @Transactional
    public void setUserArea(String userUid, String placeId, LocationSource locationAccuracy, boolean setPrimary) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(placeLookupLambda)
                    .pathSegment("details")
                    .pathSegment("{placeId}")
                    .buildAndExpand(placeId).toUri();
            ResponseEntity<TownLookupResult> responseEntity = restTemplate.getForEntity(uri, TownLookupResult.class);
            log.info("found place: {}", responseEntity.getBody());
            TownLookupResult place = responseEntity.getBody();

            User user = userRepository.findOneByUid(userUid);
            Address address = new Address(user, setPrimary);
            address.setTownOrCity(place.getTownName());
            address.setPostalCode(place.getPostalCode());
            if (place.getLatitude() != null) {
                address.setLocation(new GeoLocation(place.getLatitude(), place.getLongitude()));
                address.setLocationSource(locationAccuracy);
            }

            storeAddressRawAfterDuplicateCheck(address);
        } catch (RestClientException e) {
            log.error("Error constructing or executing lookup URL: {}", e);
        }
    }
}