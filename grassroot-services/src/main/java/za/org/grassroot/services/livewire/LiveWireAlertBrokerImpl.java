package za.org.grassroot.services.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;
import za.org.grassroot.services.geo.GeoLocationUtils;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.services.specifications.UserSpecifications;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.validation.constraints.NotNull;
import java.security.InvalidParameterException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Created by luke on 2017/05/06.
 */
@Service
public class LiveWireAlertBrokerImpl implements LiveWireAlertBroker {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireAlertBroker.class);

    // todo : consolidate several of these using entity manager
    private final LiveWireAlertRepository alertRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MeetingRepository meetingRepository;
    private final EntityManager entityManager;
    private final DataSubscriberRepository dataSubscriberRepository;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    private UssdLocationServicesBroker locationServicesBroker;

    @Value("${grassroot.livewire.instant.minsize:100}")
    private int minGroupSizeForInstantAlert;

    @Value("${grassroot.livewire.instant.mintasks:5}")
    private int minGroupTasksForInstantAlert;

    @Autowired
    public LiveWireAlertBrokerImpl(LiveWireAlertRepository alertRepository, UserRepository userRepository, GroupRepository groupRepository, MeetingRepository meetingRepository, EntityManager entityManager, DataSubscriberRepository dataSubscriberRepository, ObjectLocationBroker objectLocationBroker, LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.meetingRepository = meetingRepository;
        this.entityManager = entityManager;
        this.dataSubscriberRepository = dataSubscriberRepository;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
    }

    @Autowired(required = false)
    public void setLocationServicesBroker(UssdLocationServicesBroker locationServicesBroker) {
        this.locationServicesBroker = locationServicesBroker;
    }

    @Override
    @Transactional(readOnly = true)
    public LiveWireAlert load(String alertUid) {
        Objects.requireNonNull(alertUid);
        return alertRepository.findOneByUid(alertUid);
    }

    @Override
    @Transactional(readOnly = true)
    public long countGroupsForInstantAlert(String userUid) {
        User user = userRepository.findOneByUid(userUid);
        return (long) groupsForInstantAlertQuery(user, true).getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> groupsForInstantAlert(String userUid, Integer pageNumber, Integer pageSize) {
        User user = userRepository.findOneByUid(userUid);
        @SuppressWarnings("unchecked")
        TypedQuery<Group> query = (TypedQuery<Group>) groupsForInstantAlertQuery(user, false);

        if (pageSize != null) {
            query.setMaxResults(pageSize);

            // page number is expected to be zero indexed
            if (pageNumber != null) {
                query.setFirstResult(pageNumber * pageSize);
            }
        }

        return query.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public @NotNull List<Meeting> meetingsForAlert(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        return (List<Meeting>) entityManager.createQuery("" +
                "select distinct e from Event e inner join e.ancestorGroup.memberships m " +
                "where e.eventStartDateTime > :earliestTime " +
                "and type(e) = Meeting " +
                "and e.canceled = FALSE " +
                "and (e.createdByUser = :user or (m.user = :user and m.role.name = 'ROLE_GROUP_ORGANIZER'))")
                .setParameter("earliestTime", LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC))
                .setParameter("user", user)
                .getResultList();
    }

    @Override
    public List<User> fetchLiveWireContactsNearby(String queryingUserUid, GeoLocation location, Integer radius) {
        Objects.requireNonNull(queryingUserUid);
        Objects.requireNonNull(location);

        List<String> userUidsWithAccess = dataSubscriberRepository.userUidsOfDataSubscriberUsers();

        if (!userUidsWithAccess.contains(queryingUserUid)) {
            throw new AccessDeniedException("Error! Querying user is not authorized");
        }

        TypedQuery<User> query = entityManager.createQuery("select u from Membership m " +
                "inner join m.user u " +
                "inner join m.group g " +
                "inner join g.locations l " +
                "where g.discoverable = true and " +
                "m.role.name = 'ROLE_GROUP_ORGANIZER' and " +
                "l.localDate = (SELECT MAX(ll.localDate) FROM GroupLocation ll WHERE ll.group = l.group) and " +
                GeoLocationUtils.locationFilterSuffix("l.location"), User.class);
        GeoLocationUtils.addLocationParamsToQuery(query, location, radius);

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

        // todo : think about how to sort
        Set<User> users = new HashSet<>();
        users.addAll(query.getResultList());

        List<String> userUids = locationQuery.getResultList();
        if (userUids != null && !userUids.isEmpty()) {
            users.addAll(userRepository.findAll(Specifications
                    .where(UserSpecifications.isLiveWireContact())
                    .and(UserSpecifications.uidIn(locationQuery.getResultList()))));
        }

        return new ArrayList<>(users);
    }

    @Override
    @Transactional
    public String create(String userUid, LiveWireAlertType type, String entityUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(type);
        Objects.requireNonNull(entityUid);

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert.Builder builder = new LiveWireAlert.Builder()
                .creatingUser(user)
                .type(type);

        if (LiveWireAlertType.INSTANT.equals(type)) {
            Group group = groupRepository.findOneByUid(entityUid);
            builder = builder.group(group);
        } else {
            Meeting meeting = meetingRepository.findOneByUid(entityUid);
            builder = builder.meeting(meeting);
        }

        LiveWireAlert alert = builder.build();

        alertRepository.save(alert);
        return alert.getUid();
    }

    @Override
    @Transactional
    public void updateContactUser(String userUid, String alertUid, String contactUserUid, String contactName) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);
        Objects.requireNonNull(contactUserUid);

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        validateCreatingUser(user, alert);

        User contactUser = userRepository.findOneByUid(contactUserUid);
        alert.setContactUser(contactUser);
        alert.setContactName(contactName);
    }

    @Override
    @Transactional
    public void updateDescription(String userUid, String alertUid, String description) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);
        Objects.requireNonNull(description);

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        validateCreatingUser(user, alert);

        alert.setDescription(description);
    }

    @Override
    @Transactional
    public void setAlertToSend(String userUid, String alertUid, Instant timeToSend) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        validateCreatingUser(user, alert);

        alert.setSendTime(timeToSend == null ? Instant.now() : timeToSend);
    }

    @Async
    @Override
    @Transactional
    public void addLocationToAlert(String userUid, String alertUid, GeoLocation location, UserInterfaceType interfaceType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        validateCreatingUser(user, alert);

        logger.info("Going to add a location to LiveWire alert, should be off main thread ...");

        if (location != null) {
            alert.setLocation(location);
            alert.setLocationSource(LocationSource.convertFromInterface(interfaceType));
        } else if (locationServicesBroker != null && UserInterfaceType.USSD.equals(interfaceType)) {
            locationServicesBroker.addUssdLocationLookupAllowed(userUid, UserInterfaceType.USSD);
            GeoLocation ussdLocation = locationServicesBroker.getUssdLocationForUser(userUid);
            alert.setLocation(ussdLocation);
            alert.setLocationSource(LocationSource.LOGGED_APPROX);
        }
    }

    @Override
    @Transactional
    public void updateUserLiveWireContactStatus(String userUid, boolean addingPermission, UserInterfaceType interfaceType) {
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
        User user = userRepository.findOneByUid(userUid);
        if (!user.isLiveWireContact()) {
            logger.info("Error! Location track called for user not yet set as contact");
        } else if (locationServicesBroker != null) {
            locationServicesBroker.addUssdLocationLookupAllowed(userUid, type);
            locationServicesBroker.getUssdLocationForUser(userUid); // will save a user log (though prev period will wait)
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LiveWireAlert> loadAlerts(String userUid, boolean unreviewedOnly, Pageable pageable) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(pageable);

        return unreviewedOnly ? alertRepository.findAll(pageable) : alertRepository.findByReviewed(false, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserTag(String userUid) {
        Objects.requireNonNull(userUid);
        return dataSubscriberRepository.findSubscriberHoldingUser(userUid)
                .stream()
                .anyMatch(DataSubscriber::isCanTag);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserRelease(String userUid) {
        Objects.requireNonNull(userUid);
        return dataSubscriberRepository.findSubscriberHoldingUser(userUid)
                .stream()
                .anyMatch(DataSubscriber::isCanRelease);
    }

    @Override
    @Transactional
    public void addTagsToAlert(String userUid, String alertUid, List<String> tags) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);

        if (!canUserTag(userUid)) {
            throw new AccessDeniedException("This user does not have permission to tag");
        }

        if (tags == null) {
            throw new InvalidParameterException("Error! No tags provided");
        }

        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        alert.addTags(tags);
    }

    @Override
    @Transactional
    public void releaseAlert(String userUid, String alertUid, List<String> tags) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);

        if (!canUserRelease(userUid)) {
            throw new AccessDeniedException("This user does not have permission to release");
        }

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        if (tags != null) {
            alert.addTags(tags);
        }

        alert.setReviewed(true);
        alert.setReviewedByUser(user);
        alert.setSendTime(Instant.now());
    }

    private void validateCreatingUser(User user, LiveWireAlert alert) throws AccessDeniedException {
        Objects.requireNonNull(user);
        Objects.requireNonNull(alert);
        if (!alert.getCreatingUser().equals(user)) {
            throw new AccessDeniedException("Only the user creating the alert can perform that action");
        }
    }

    private Query groupsForInstantAlertQuery(User user, boolean countOnly) {
        String firstLine = countOnly ? "select count(g)" : "select g";
        return entityManager.createQuery(firstLine + " from Group g " +
                "inner join g.memberships m " +
                "where g.active = true and " +
                "g.createdDateTime >= :createdDateTime and " +
                "size(g.memberships) >= :minMembership and " +
                "(size(g.descendantEvents) + size(g.descendantTodos)) >= :minTasks and m.user = :user")
                .setParameter("user", user)
                .setParameter("createdDateTime", DateTimeUtil.getEarliestInstant())
                .setParameter("minMembership", minGroupSizeForInstantAlert)
                .setParameter("minTasks", minGroupTasksForInstantAlert);
    }

}
