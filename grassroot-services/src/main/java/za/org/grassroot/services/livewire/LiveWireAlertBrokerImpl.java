package za.org.grassroot.services.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.notification.LiveWireAlertReleasedNotification;
import za.org.grassroot.core.domain.notification.LiveWireMadeContactNotification;
import za.org.grassroot.core.domain.notification.LiveWireToReviewNotification;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.PhoneNumberUtil;
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
import java.util.stream.Collectors;

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
    private final DataSubscriberRepository dataSubscriberRepository;

    private final LiveWireSendingBroker liveWireSendingBroker;
    private final EntityManager entityManager;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    private MessageSourceAccessor messageSource;
    private UssdLocationServicesBroker locationServicesBroker;

    @Value("${grassroot.livewire.instant.minsize:100}")
    private int minGroupSizeForInstantAlert;

    @Value("${grassroot.livewire.instant.mintasks:5}")
    private int minGroupTasksForInstantAlert;

    @Value("${grassroot.livewire.contacts.expansive:false}")
    private boolean expansiveContactFind;

    @Value("${grassroot.livewire.contacts.mingroup:10}")
    private int mingGroupSizeForExpansiveContactFind;

    @Autowired
    public LiveWireAlertBrokerImpl(LiveWireAlertRepository alertRepository, UserRepository userRepository, GroupRepository groupRepository, MeetingRepository meetingRepository, EntityManager entityManager, DataSubscriberRepository dataSubscriberRepository, ObjectLocationBroker objectLocationBroker, LogsAndNotificationsBroker logsAndNotificationsBroker, ApplicationEventPublisher applicationEventPublisher, LiveWireSendingBroker liveWireSendingBroker) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.meetingRepository = meetingRepository;
        this.entityManager = entityManager;
        this.dataSubscriberRepository = dataSubscriberRepository;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.liveWireSendingBroker = liveWireSendingBroker;
    }

    @Autowired
    public void setMessageSource(@Qualifier("servicesMessageSourceAccessor") MessageSourceAccessor messageSource) {
        this.messageSource = messageSource;
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

        // todo : think about how to sort
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

        logger.info("hunted livewire contacts, found {}", users.size());

        return new ArrayList<>(users);
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
        query.setParameter("minMembership", mingGroupSizeForExpansiveContactFind);
        GeoLocationUtils.addLocationParamsToQuery(query, location, radius);

        return new HashSet<>(query.getResultList());
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
                .type(type)
                .destType(LiveWireAlertDestType.PUBLIC_LIST); // default to public

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

        if (!contactUser.hasName()) {
            contactUser.setDisplayName(contactName);
        }
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
    public void updateAlertDestination(String userUid, String alertUid, String subscriberUid, LiveWireAlertDestType destType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        validateCreatingUser(user, alert);

        if (!LiveWireAlertDestType.PUBLIC_LIST.equals(destType) && subscriberUid == null) {
            throw new IllegalArgumentException("Error! If destination type is not public list, must pass a subscriber UID");
        }

        alert.setDestinationType(destType);
        if (subscriberUid != null) {
            DataSubscriber targetSubscriber = dataSubscriberRepository.findOneByUid(subscriberUid);
            alert.setTargetSubscriber(targetSubscriber);
        }
    }

    @Override
    @Transactional
    public void setAlertComplete(String userUid, String alertUid, Instant soonestTimeToSend) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        validateCreatingUser(user, alert);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        alert.setComplete(true);

        LiveWireLog completedLog = new LiveWireLog.Builder()
                .alert(alert)
                .userTakingAction(user)
                .type(LiveWireLogType.ALERT_COMPLETED)
                .notes("alert completed and out for review")
                .build();
        bundle.addLog(completedLog);
        bundle.addNotifications(generateToReviewNotifications(completedLog));

        if (alert.getContactUser() != null && !alert.getContactUser().equals(alert.getCreatingUser())) {
            LiveWireLog contactLog = new LiveWireLog.Builder()
                    .alert(alert)
                    .userTakingAction(user)
                    .userTargeted(alert.getContactUser())
                    .type(LiveWireLogType.USER_SET_AS_CONTACT)
                    .notes("user made a contact but not creating user")
                    .build();
            bundle.addLog(contactLog);
            Notification notification = generateMadeContactNotification(contactLog);
            logger.info("adding other contact notification: {}", notification);
            bundle.addNotification(notification);
        }

        logger.info("bundle notifications: {}", bundle.getNotifications());
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
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

    @Override
    @Transactional(readOnly = true)
    public Page<LiveWireAlert> loadAlerts(String userUid, boolean unreviewedOnly, Pageable pageable) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(pageable);

        return unreviewedOnly ? alertRepository.findByCompleteTrueAndReviewedFalse(pageable)
                : alertRepository.findByCompleteTrue(pageable);
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
    public void setTagsForAlert(String userUid, String alertUid, List<String> tags) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);

        logger.info("tags received: {}", tags);

        if (!canUserTag(userUid)) {
            throw new AccessDeniedException("This user does not have permission to tag");
        }

        if (listIsNullEmptyOrAllBlank(tags)) {
            logger.info("tags are empty!");
            throw new InvalidParameterException("Error! No tags provided");
        }

        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        alert.addTags(tags);
    }

    private boolean listIsNullEmptyOrAllBlank(List<String> list) {
        return list == null || list.isEmpty() ||
                list.stream().allMatch(StringUtils::isEmpty);
    }

    @Override
    @Transactional
    public void reviewAlert(String userUid, String alertUid, List<String> tags, boolean send, List<String> publicListUids) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);

        if (!canUserRelease(userUid)) {
            throw new AccessDeniedException("This user does not have permission to release");
        }

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        if (tags != null && !tags.isEmpty()) {
            alert.reviseTags(tags);
        }

        alert.setReviewed(true);
        alert.setReviewedByUser(user);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        LiveWireLog log = new LiveWireLog.Builder()
                .alert(alert)
                .userTakingAction(user)
                .type(send ? LiveWireLogType.ALERT_RELEASED : LiveWireLogType.ALERT_BLOCKED)
                .notes("tags added: " + tags + ", lists: " + publicListUids)
                .build();
        bundle.addLog(log);

        if (send) {
            alert.setPublicListUids(publicListUids);
            alert.setSendTime(Instant.now());
            logger.debug("set public list UIDs to: {}", alert.getPublicListUids());
            bundle.addNotification(new LiveWireAlertReleasedNotification(
                    alert.getCreatingUser(),
                    messageSource.getMessage("livewire.alert.released"),
                    log));
        }
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
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

    private Set<Notification> generateToReviewNotifications(LiveWireLog log) {
        final String message = messageSource.getMessage("livewire.alert.toreview",
                new String[] { log.getAlert().getCreatingUser().getName() });
        List<String> userUids = dataSubscriberRepository.fetchUserUidsOfReviewingUsers();
        List<User> users = userRepository.findByUidIn(new HashSet<>(userUids));
        return users.stream()
                .map(u -> new LiveWireToReviewNotification(u, message, log))
                .collect(Collectors.toSet());
    }

    private Notification generateMadeContactNotification(LiveWireLog log) {
        final String[] fields = new String[] { log.getAlert().getCreatingUser().getName(), log.getAlert().getDescription(),
                PhoneNumberUtil.invertPhoneNumber(log.getAlert().getCreatingUser().getPhoneNumber())};
        return new LiveWireMadeContactNotification(
                log.getAlert().getContactUser(),
                messageSource.getMessage("livewire.alert.madecontact", fields),
                log);
    }

}
