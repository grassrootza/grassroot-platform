package za.org.grassroot.services.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.geo.Address;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.dto.LiveWireContactDTO;
import za.org.grassroot.core.enums.LiveWireContactType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.DataSubscriberRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.UserSpecifications;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.GeoLocationUtils;
import za.org.grassroot.services.user.AddressBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LiveWireContactBrokerImpl implements LiveWireContactBroker {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireContactBrokerImpl.class);

    @Value("${grassroot.livewire.contacts.expansive:false}")
    private boolean expansiveContactFind;

    @Value("${grassroot.livewire.contacts.mingroup:10}")
    private int minGroupSizeForExpansiveContactFind;

    private final EntityManager entityManager;
    private final UserRepository userRepository;
    private final DataSubscriberRepository dataSubscriberRepository;

    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final GeoLocationBroker geoLocationBroker;
    private final AddressBroker addressBroker;

    private UssdLocationServicesBroker locationServicesBroker;

    @Autowired
    public LiveWireContactBrokerImpl(UserRepository userRepository, DataSubscriberRepository dataSubscriberRepository, LogsAndNotificationsBroker logsAndNotificationsBroker, EntityManager entityManager, GeoLocationBroker geoLocationBroker, AddressBroker addressBroker) {
        this.userRepository = userRepository;
        this.dataSubscriberRepository = dataSubscriberRepository;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.entityManager = entityManager;
        this.geoLocationBroker = geoLocationBroker;
        this.addressBroker = addressBroker;
    }

    @Autowired(required = false)
    public void setLocationServicesBroker(UssdLocationServicesBroker locationServicesBroker) {
        this.locationServicesBroker = locationServicesBroker;
    }


    @Override
    public Page<LiveWireContactDTO> loadLiveWireContacts(String userUid, String filterTerm, Pageable pageable) {
        Objects.requireNonNull(userUid);
        if (!dataSubscriberRepository.userUidsOfDataSubscriberUsers().contains(userUid)) {
            throw new AccessDeniedException("Error! Querying user is not authorized");
        }

        // todo : a lot of work on the filtering, including incorporating the location
        Specifications<User> lwireContactSpecs = Specifications.where(UserSpecifications.isLiveWireContact());
        if (!StringUtils.isEmpty(filterTerm)) {
            lwireContactSpecs = lwireContactSpecs.and(UserSpecifications.nameContains(filterTerm));
        }

        Set<String> userUids = userRepository.findAll(lwireContactSpecs).stream()
                .map(User::getUid)
                .collect(Collectors.toSet());

        if (expansiveContactFind) {
            userUids.addAll(fetchOrganizerUidsOfLargePublicGroups());
        }

        long firstElement = (long) pageable.getOffset();
        long lastElement = firstElement + pageable.getPageSize();

        logger.info("firstElement = {}, lastElement = {}", firstElement, lastElement);

        List<LiveWireContactDTO> contactsTotal = userRepository
                .findAll(Specifications.where(UserSpecifications.uidIn(userUids)))
                .stream()
                .map(this::generateFromUser)
                .filter(c -> StringUtils.isEmpty(filterTerm) || c.matchesFilter(filterTerm))
                .collect(Collectors.toList());

        int totalRecords = contactsTotal.size();
        logger.info("total number of contacts: {}", totalRecords);

        List<LiveWireContactDTO> contactsPage = contactsTotal
                .stream()
                .sorted(sortComparator(pageable.getSort()))
                .skip(firstElement)
                .limit(lastElement)
                .collect(Collectors.toList());

        return new PageImpl<>(contactsPage, pageable, totalRecords);
    }

    @Override
    public List<LiveWireContactDTO> loadLiveWireContacts(String userUid) {
        Objects.requireNonNull(userUid);
        if (!dataSubscriberRepository.userUidsOfDataSubscriberUsers().contains(userUid)) {
            throw new AccessDeniedException("Error! Querying user is not authorized");
        }

        Specifications<User> lwireContactSpecs = Specifications.where(UserSpecifications.isLiveWireContact());
        Set<String> userUids = userRepository.findAll(lwireContactSpecs).stream()
                .map(User::getUid)
                .collect(Collectors.toSet());
        if (expansiveContactFind) {
            userUids.addAll(fetchOrganizerUidsOfLargePublicGroups());
        }

        return userRepository
                .findAll(Specifications.where(UserSpecifications.uidIn(userUids)))
                .stream()
                .map(this::generateFromUser)
                .sorted(Comparator.comparing(LiveWireContactDTO::getContactName))
                .collect(Collectors.toList());
    }

    private LiveWireContactDTO generateFromUser(User user) {
        return new LiveWireContactDTO(user.getName(),
                user.getNationalNumber(),
                getAddressOfUser(user),
                getUserGraphSize(user),
                user.isLiveWireContact() ? LiveWireContactType.REGISTERED : LiveWireContactType.PUBLIC_MEETING);
    }

    // todo: use method that defaults to looking up group location too
    private String getAddressOfUser(User user) {
        String unknown = "Unknown";
        PreviousPeriodUserLocation location = geoLocationBroker.fetchUserLocation(user.getUid());
        if (location != null) {
            Address address = addressBroker.fetchNearestAddress(user.getUid(), location.getLocation(), 1, true);
            return address == null ? unknown : address.getStreet() + ", " + address.getNeighbourhood();
        } else {
            return unknown;
        }
    }

    @Transactional(readOnly = true)
    public int getUserGraphSize(User user) {
        return user.getMemberships().size() == 0 ? 0 : (int) userRepository.count(UserSpecifications.inGroups(user.getGroups()));
    }

    private Comparator<LiveWireContactDTO> sortComparator(Sort sort) {
        if (sort == null || !sort.iterator().hasNext()) {
            return Comparator.comparing(LiveWireContactDTO::getContactName);
        } else {
            Sort.Order order = sort.iterator().next();
            if (order.getProperty().equals("addressDescription")) {
                return Comparator.comparing(LiveWireContactDTO::getAddressDescription);
            } else if (order.getProperty().equals("graphSize")) {
                return Comparator.comparing(LiveWireContactDTO::getGroupSize);
            } else {
                return Comparator.comparing(LiveWireContactDTO::getContactName);
            }
        }
    }

    private Set<String> fetchOrganizerUidsOfLargePublicGroups() {
        TypedQuery<String> query = entityManager.createQuery("select u.uid from Membership m " +
                "inner join m.user u " +
                "inner join m.group g " +
                "where g.discoverable = true and " +
                "size(g.memberships) >= :minMembership and " +
                "m.role.name = 'ROLE_GROUP_ORGANIZER'", String.class);
        query.setParameter("minMembership", minGroupSizeForExpansiveContactFind);
        return new HashSet<>(query.getResultList());
    }

    @Override
    public List<LiveWireContactDTO> fetchLiveWireContactsNearby(String queryingUserUid, GeoLocation location, Integer radius) {
        Objects.requireNonNull(queryingUserUid);
        Objects.requireNonNull(location);

        List<String> userUidsWithAccess = dataSubscriberRepository.userUidsOfDataSubscriberUsers();

        if (!userUidsWithAccess.contains(queryingUserUid)) {
            throw new AccessDeniedException("Error! Querying user is not authorized");
        }

        Set<User> users = new HashSet<>();

        // note: there may be a way to do this in one using HQL, but can't figure out all mapping, hence just
        // using a second one, which anyway as a straight select on unique key will have little performance problem
        // note: also not restricting to single calculated location, in case user has been moving around (at present
        // prefer too many records rather than too few)
        TypedQuery<String> locationQuery = entityManager.createQuery("" +
                "select distinct ppl.key.userUid from PreviousPeriodUserLocation ppl where " +
                "ppl.key.localDate >= :oldestRecord and " +
                GeoLocationUtils.locationFilterSuffix("ppl.location"), String.class);
        locationQuery.setParameter("oldestRecord", LocalDate.now().minus(3, ChronoUnit.MONTHS));
        GeoLocationUtils.addLocationParamsToQuery(locationQuery, location, radius);

        List<String> userUids = locationQuery.getResultList();
        if (userUids != null && !userUids.isEmpty()) {
            users.addAll(userRepository.findAll(Specifications
                    .where(UserSpecifications.isLiveWireContact())
                    .and(UserSpecifications.uidIn(locationQuery.getResultList()))));
        }

        if (expansiveContactFind) {
            users.addAll(fetchOrganizersOfPublicGroupsNearby(location, radius));
        }

        logger.debug("hunted livewire contacts, found {}", users.size());
        return users.stream().map(LiveWireContactDTO::new).collect(Collectors.toList());
    }

    private Set<User> fetchOrganizersOfPublicGroupsNearby(GeoLocation location, Integer radius) {
        TypedQuery<User> query = entityManager.createQuery("select u from Membership m " +
                "inner join m.user u " +
                "inner join m.group g " +
                "inner join g.locations l " +
                "where g.discoverable = true and " +
                "size(g.memberships) >= :minMembership and " +
                "m.role.name = 'ROLE_GROUP_ORGANIZER' and " +
                "l.localDate = (SELECT MAX(ll.localDate) FROM GroupLocation ll WHERE ll.group = l.group) and " +
                GeoLocationUtils.locationFilterSuffix("l.location"), User.class);
        query.setParameter("minMembership", minGroupSizeForExpansiveContactFind);
        GeoLocationUtils.addLocationParamsToQuery(query, location, radius);

        return new HashSet<>(query.getResultList());
    }

    @Override
    @Transactional
    public void updateUserLiveWireContactStatus(String userUid, boolean addingPermission,
                                                UserInterfaceType interfaceType) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        user.setLiveWireContact(addingPermission);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(new UserLog(userUid,
                addingPermission ? UserLogType.LIVEWIRE_CONTACT_GRANTED : UserLogType.LIVEWIRE_CONTACT_REVOKED,
                (addingPermission ? "added " : "removed ") + " livewire contact status",
                interfaceType));
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Async
    @Override
    @Transactional
    public void trackLocationForLiveWireContact(String userUid, UserInterfaceType type) {
        logger.info("tracking user location for livewire contact");
        User user = userRepository.findOneByUid(userUid);
        if (!user.isLiveWireContact()) {
            logger.info("Error! Location track called for user not yet set as contact");
        } else if (locationServicesBroker != null) {
            locationServicesBroker.addUssdLocationLookupAllowed(userUid, type);
            locationServicesBroker.getUssdLocationForUser(userUid); // will save a user log (though prev period will wait)
        }
    }

}
