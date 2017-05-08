package za.org.grassroot.services.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.LiveWireAlertRepository;
import za.org.grassroot.core.repository.MeetingRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Created by luke on 2017/05/06.
 */
@Service
public class LiveWireAlertBrokerImpl implements LiveWireAlertBroker {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireAlertBroker.class);

    private final LiveWireAlertRepository alertRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MeetingRepository meetingRepository;
    private final EntityManager entityManager;

    private UssdLocationServicesBroker locationServicesBroker;

    @Value("${grassroot.livewire.instant.minsize:100}")
    private int minGroupSizeForInstantAlert;

    @Value("${grassroot.livewire.instant.mintasks:5}")
    private int minGroupTasksForInstantAlert;

    @Autowired
    public LiveWireAlertBrokerImpl(LiveWireAlertRepository alertRepository, UserRepository userRepository, GroupRepository groupRepository, MeetingRepository meetingRepository, EntityManager entityManager) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.meetingRepository = meetingRepository;
        this.entityManager = entityManager;
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
        return (long) groupsForInstantAlertQuery(user, true).getFirstResult();
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
                "select e from Event e inner join e.ancestorGroup.memberships m " +
                "where e.eventStartDateTime > :earliestTime " +
                "and type(e) = Meeting " +
                "and e.canceled = FALSE " +
                "and (e.createdByUser = :user or (m.user = :user and m.role.name = 'ROLE_GROUP_ORGANIZER'))")
                .setParameter("earliestTime", LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC))
                .setParameter("user", user)
                .getResultList();
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

        User contactUser = userRepository.findOneByUid(userUid);
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
    public void updateSentStatus(String alertUid, boolean sent) {
        Objects.requireNonNull(alertUid);

        LiveWireAlert alert = alertRepository.findOneByUid(alertUid);
        alert.setSent(sent);
    }

    @Override
    public List<LiveWireAlert> findAlertsPendingSend() {
        Instant end = Instant.now();
        Instant start = end.minus(1L, ChronoUnit.HOURS);
        return alertRepository.findBySendTimeBetweenAndSentFalse(start, end);
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
