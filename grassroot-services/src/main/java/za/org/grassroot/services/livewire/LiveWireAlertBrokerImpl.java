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
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.livewire.LiveWireLog;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.notification.LiveWireAlertReleasedNotification;
import za.org.grassroot.core.domain.notification.LiveWireMadeContactNotification;
import za.org.grassroot.core.domain.notification.LiveWireToReviewNotification;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;
import za.org.grassroot.services.geo.GeographicSearchType;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

    private final EntityManager entityManager;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    private MessageSourceAccessor messageSource;
    private UssdLocationServicesBroker locationServicesBroker;

    protected final static double KM_PER_DEGREE = 111.045;

    @Value("${grassroot.livewire.instant.minsize:100}")
    private int minGroupSizeForInstantAlert;

    @Value("${grassroot.livewire.instant.mintasks:5}")
    private int minGroupTasksForInstantAlert;

    @Autowired
    public LiveWireAlertBrokerImpl(LiveWireAlertRepository alertRepository, UserRepository userRepository, GroupRepository groupRepository, MeetingRepository meetingRepository, EntityManager entityManager, DataSubscriberRepository dataSubscriberRepository, ApplicationEventPublisher applicationEventPublisher, LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.meetingRepository = meetingRepository;
        this.entityManager = entityManager;
        this.dataSubscriberRepository = dataSubscriberRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
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
    public boolean canUserCreateAlert(String userUid) {
        return countGroupsForInstantAlert(userUid) > 0 || !meetingsForAlert(userUid).isEmpty();
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
                "and m.user = :user")
                .setParameter("earliestTime", LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC))
                .setParameter("user", user)
                .getResultList();
    }

    @Override
    @Transactional
    public String createAsComplete(String userUid, String headline, String description,
                                   LiveWireAlertType type, String entityUid,
                                   String contactUserUid, String contactName,
                                   String contactNumber, LiveWireAlertDestType destType, DataSubscriber destSubscriber,
                                   List<MediaFileRecord> mediaFiles) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(type);
        Objects.requireNonNull(entityUid);

        logger.info("contact user UID = {}", contactUserUid);

        if (destType != null && !LiveWireAlertDestType.PUBLIC_LIST.equals(destType)) {
            Objects.requireNonNull(destSubscriber);
        }

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert.Builder builder = new LiveWireAlert.Builder()
                .creatingUser(user)
                .type(type)
                .destType(destType == null ? LiveWireAlertDestType.PUBLIC_LIST : destType)
                .destSubscriber(destSubscriber)
                .headline(headline);

        builder = setTypeAndEntity(type, entityUid, builder);

        if (!StringUtils.isEmpty(description)) {
            builder.description(description);
        }

        if (StringUtils.isEmpty(contactUserUid)) {
            builder.contactUser(user);
        } else {
            User contactUser = userRepository.findOneByUid(contactUserUid);
            if (contactUser == null) {
                throw new IllegalArgumentException("Contact user must have been created before entering here");
            }
            logger.info("setting contact user with number, {}", contactUser.getPhoneNumber());
            builder.contactUser(contactUser);
        }

        if (!StringUtils.isEmpty(contactName)) {
            builder.contactName(contactName);
        }

        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            builder.mediaFiles(new HashSet<>(mediaFiles));
        }

        builder.complete(true);
        LiveWireAlert alert = alertRepository.save(builder.build());

        final LogsAndNotificationsBundle bundle = alertCompleteBundle(user, alert);

        AfterTxCommitTask afterTxCommitTask = () -> logsAndNotificationsBroker.asyncStoreBundle(bundle);
        applicationEventPublisher.publishEvent(afterTxCommitTask);

        return alert.getUid();
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

        LiveWireAlert alert = setTypeAndEntity(type, entityUid, builder)
                .build();

        alertRepository.save(alert);
        return alert.getUid();
    }

    private LiveWireAlert.Builder setTypeAndEntity(LiveWireAlertType type, String entityUid, LiveWireAlert.Builder builder) {
        if (LiveWireAlertType.INSTANT.equals(type)) {
            Group group = groupRepository.findOneByUid(entityUid);
            return builder.group(group);
        } else {
            Meeting meeting = meetingRepository.findOneByUid(entityUid);
            return builder.meeting(meeting);
        }
    }

    @Override
    @Transactional
    public String createAsComplete(String userUid, LiveWireAlert.Builder builder) {
        Objects.requireNonNull(builder);
        builder.validateSufficientFields();
        LiveWireAlert alert = alertRepository.save(builder.build());
        alert.setComplete(true);

        LogsAndNotificationsBundle bundle = alertCompleteBundle(userRepository.findOneByUid(userUid), alert);
        logger.info("bundle notifications: {}", bundle.getNotifications());

        AfterTxCommitTask task = () -> logsAndNotificationsBroker.asyncStoreBundle(bundle);
        applicationEventPublisher.publishEvent(task);

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
    public void updateHeadline(String userUid, String alertUid, String headline) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);
        Objects.requireNonNull(headline);

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        validateCreatingOrReviewUser(user, alert);

        alert.setHeadline(headline);
    }

    @Override
    @Transactional
    public void updateDescription(String userUid, String alertUid, String description) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);
        Objects.requireNonNull(description);

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        validateCreatingOrReviewUser(user, alert);

        alert.setDescription(description);
    }

    @Override
    @Transactional
    public void addMediaFile(String userUid, String alertUid, MediaFileRecord mediaFileRecord) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertUid);
        Objects.requireNonNull(mediaFileRecord);

        User user = userRepository.findOneByUid(userUid);
        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        validateCreatingOrReviewUser(user, alert);

        alert.addMediaFile(mediaFileRecord);
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

        alert.setComplete(true);
        LogsAndNotificationsBundle bundle = alertCompleteBundle(user, alert);

        logger.info("bundle notifications: {}", bundle.getNotifications());
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    private LogsAndNotificationsBundle alertCompleteBundle(User user, LiveWireAlert alert) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
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
        return bundle;
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

        logger.info("set tags to: {}", alert.getTagList());
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
            alert.addTags(tags);
        }

        logger.debug("here are the alert tags: {}", tags);

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
            alert.revisePublicLists(publicListUids);
            alert.setSendTime(Instant.now());
            logger.info("set public list UIDs to: {}", alert.getPublicListsUids());
            bundle.addNotification(new LiveWireAlertReleasedNotification(
                    alert.getCreatingUser(),
                    messageSource.getMessage("livewire.alert.released"),
                    log));
        }

        alertRepository.save(alert);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    private void validateCreatingUser(User user, LiveWireAlert alert) throws AccessDeniedException {
        Objects.requireNonNull(user);
        Objects.requireNonNull(alert);
        if (!alert.getCreatingUser().equals(user)) {
            throw new AccessDeniedException("Only the user creating the alert can perform that action");
        }
    }

    private void validateCreatingOrReviewUser(User user, LiveWireAlert alert) throws AccessDeniedException {
        logger.info("user UID = {}, alert creating user = {}", user, alert);
        if (!alert.getCreatingUser().equals(user) && !canUserRelease(user.getUid())) {
            throw new AccessDeniedException("Only the user creating the alert can do that, or a reviewer");
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

    public List<LiveWireAlert> fetchAlertsNearUser(String userUid, GeoLocation location,
                                                   int radius, GeographicSearchType searchType) {

        User user = userRepository.findOneByUid(userUid);

        if(location == null || !location.isValid()){
            throw new InvalidParameterException("Invalid GeoLocation object.");
        }

        if(radius < 0){
            throw new InvalidParameterException("Invalid Radius,should be positive");
        }

        String mineFilter = searchType.equals(GeographicSearchType.PUBLIC) ? " AND l.creatingUser <>:user "
                : searchType.equals(GeographicSearchType.PRIVATE) ? " AND l.creatingUser = :user " : "";

        logger.info("searchType = {}, on whether mine? = {}", searchType, mineFilter);

        Instant lastWeekTime = getLastWeekTime();

        String query = "SELECT l FROM LiveWireAlert l" +
                " WHERE l.sent = true " +
                " AND creationTime > :lastWeekTime" +
                mineFilter +
                " AND l.location.latitude " +
                "      BETWEEN :latpoint  - (:radius / :distance_unit) " +
                "          AND :latpoint  + (:radius / :distance_unit) " +
                "  AND l.location.longitude " +
                "      BETWEEN :longpoint - (:radius / (:distance_unit * COS(RADIANS(:latpoint)))) " +
                "          AND :longpoint + (:radius / (:distance_unit * COS(RADIANS(:latpoint)))) " +
                "  AND :radius >= (:distance_unit " +
                "           * DEGREES(ACOS(COS(RADIANS(:latpoint)) " +
                "           * COS(RADIANS(l.location.latitude)) " +
                "           * COS(RADIANS(:longpoint - l.location.longitude)) " +
                "           + SIN(RADIANS(:latpoint)) " +
                "           * SIN(RADIANS(l.location.latitude))))) ";

        logger.debug("livewire alert location search = {}", query);

        TypedQuery<LiveWireAlert> typedQuery = entityManager.createQuery(query,LiveWireAlert.class)
                .setParameter("radius", (double)radius)
                .setParameter("distance_unit", KM_PER_DEGREE)
                .setParameter("latpoint",location.getLatitude())
                .setParameter("longpoint",location.getLongitude())
                .setParameter("lastWeekTime",lastWeekTime);

        if (!searchType.equals(GeographicSearchType.BOTH)) {
            typedQuery = typedQuery.setParameter("user",user);
        }

        return typedQuery.getResultList();
    }

    private Instant getLastWeekTime(){
        return Instant.now().minus(21, ChronoUnit.DAYS);
    }
}
