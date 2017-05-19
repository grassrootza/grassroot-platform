package za.org.grassroot.services;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.dto.KeywordDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.specifications.NotificationSpecifications;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.util.DateTimeUtil.*;
import static za.org.grassroot.services.specifications.UserSpecifications.*;

/**
 * Created by luke on 2016/12/12.
 */
@Service
public class AnalyticalServiceImpl implements AnalyticalService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticalServiceImpl.class);

    @Value("${grassroot.keywords.excluded:''}")
    String listOfWordsToExcludeFromStat;

    private final UserRepository userRepository;
    private final UserLogRepository userLogRepository;
    private final GroupRepository groupRepository;
    private final VoteRepository voteRepository;
    private final MeetingRepository meetingRepository;
    private final TodoRepository todoRepository;
    private final GeoLocationBroker geoLocationBroker;
    private final EntityManager entityManager;
    private final SafetyEventRepository safetyEventRepository;
    private final LiveWireAlertRepository liveWireAlertRepository;
    private final NotificationRepository notificationRepository;

    @Autowired
    public AnalyticalServiceImpl(UserRepository userRepository, UserLogRepository userLogRepository, GroupRepository groupRepository,
                                 VoteRepository voteRepository, MeetingRepository meetingRepository, EntityManager entityManager,
                                 TodoRepository todoRepository, GeoLocationBroker geoLocationBroker, SafetyEventRepository safetyRepository, LiveWireAlertRepository liveWireAlertRepository, NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.userLogRepository = userLogRepository;
        this.groupRepository = groupRepository;
        this.voteRepository = voteRepository;
        this.meetingRepository = meetingRepository;
        this.entityManager = entityManager;
        this.todoRepository = todoRepository;
        this.geoLocationBroker = geoLocationBroker;
        this.safetyEventRepository = safetyRepository;
        this.liveWireAlertRepository = liveWireAlertRepository;
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Long countAllUsers() {
        return userRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsersCreatedInInterval(LocalDateTime start, LocalDateTime end) {
        return (int) userRepository.count(
                where(createdAfter(convertToSystemTime(start, getSAST())))
                .and(createdBefore(convertToSystemTime(end, getSAST()))));
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsersThatHaveInitiatedSession() {
        return (int) userRepository.count(hasInitiatedSession());
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsersCreatedAndInitiatedInPeriod(LocalDateTime start, LocalDateTime end) {
        return (int) userRepository.count(where(hasInitiatedSession())
                .and(createdAfter(convertToSystemTime(start, getSAST())))
                .and(createdBefore(convertToSystemTime(end, getSAST()))));
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsersThatHaveWebProfile() {
        return (int) userRepository.count(hasWebProfile());
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsersCreatedWithWebProfileInPeriod(LocalDateTime start, LocalDateTime end) {
        return (int) userRepository.count(where(hasWebProfile())
                .and(createdAfter(convertToSystemTime(start, getSAST())))
                .and(createdBefore(convertToSystemTime(end, getSAST()))));
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsersWithGeoLocationData() {
        return geoLocationBroker.fetchUsersWithRecordedAverageLocations(LocalDate.now()).size();
    }

    @Override
    @Transactional(readOnly = true)
    public int countGroupsWithGeoLocationData() {
        return geoLocationBroker.fetchGroupsWithRecordedAverageLocations().size();
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsersThatHaveAndroidProfile(){
        return (int) userRepository.count(hasAndroidProfile());
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsersCreatedWithAndroidProfileInPeriod(LocalDateTime start, LocalDateTime end) {
        return (int) userRepository.count(where(hasAndroidProfile())
                .and(createdAfter(convertToSystemTime(start, getSAST())))
                .and(createdBefore(convertToSystemTime(end, getSAST()))));
    }

    @Override
    @Transactional(readOnly = true)
    public Long countActiveGroups() {
        return groupRepository.count(where(GroupSpecifications.isActive()));
    }

    @Override
    @Transactional(readOnly = true)
    public int countGroupsCreatedInInterval(LocalDateTime start, LocalDateTime end) {
        return (int) groupRepository.count(where(
                GroupSpecifications.createdBetween(convertToSystemTime(start, getSAST()), convertToSystemTime(end, getSAST())))
                .and(GroupSpecifications.isActive()));
    }

    @Override
    @Transactional(readOnly = true)
    public Long countAllEvents(EventType eventType) {
        if (eventType.equals(EventType.MEETING)) {
            return meetingRepository.count();
        } else {
            return voteRepository.count();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int countEventsCreatedInInterval(LocalDateTime start, LocalDateTime end, EventType eventType) {
        Instant intervalStart = convertToSystemTime(start, getSAST());
        Instant intervalEnd = convertToSystemTime(end, getSAST());
        if (eventType.equals(EventType.MEETING)) {
            return meetingRepository.countByCreatedDateTimeBetween(intervalStart, intervalEnd);
        } else {
            return voteRepository.countByCreatedDateTimeBetween(intervalStart, intervalEnd);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Long countAllTodos() {
        return todoRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public Long countTodosRecordedInInterval(LocalDateTime start, LocalDateTime end) {
        return todoRepository.countByCreatedDateTimeBetween(start.toInstant(ZoneOffset.UTC), end.toInstant(ZoneOffset.UTC));
    }

    @Override
    @Transactional(readOnly = true)
    public int countSafetyEventsInInterval(LocalDateTime start, LocalDateTime end) {
        Instant startInstant = start == null ? getEarliestInstant() : convertToSystemTime(start, getSAST());
        Instant endInstant = end == null ? getVeryLongAwayInstant() : convertToSystemTime(end, getSAST());
        return (int) safetyEventRepository.countByCreatedDateTimeBetween(startInstant, endInstant);
    }

    @Override
    @Transactional
    public List<KeywordDTO> getKeywordStats(LocalDateTime localDate) {
        List<KeywordDTO> rawStats = processRawStats(DateTimeUtil.convertToSystemTime(localDate, DateTimeUtil.getSAST()));
        List<String> excludedWords = Lists.newArrayList(Splitter.on(",").split(listOfWordsToExcludeFromStat));
        logger.info("got raw stats, now applying these strings as filter: " + excludedWords);

        List<KeywordDTO> filteredTerms = new ArrayList<>();
        rawStats.stream()
                .filter(w -> !excludedWords.contains(w.getKeyword()))
                .forEach(filteredTerms::add);

        return filteredTerms;
    }

    @Override
    @Transactional(readOnly = true)
    public long getMaxSessionsInLastMonth() {
        Instant end = Instant.now();
        Instant start = end.minus(30, ChronoUnit.DAYS);
        List<Long> result = userLogRepository.getMaxNumberLogsInInterval(start, end, UserLogType.USER_SESSION);
        return (result == null || result.isEmpty()) ? 0 : result.get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, Integer> getSessionHistogram(Instant start, Instant end, int interval) {
        Map<Integer, Integer> data = new LinkedHashMap<>();
        final int max = getMaxSessionsInPeriod(start, end);
        data.put(1, countSessionsInPeriod(start, end, 1, 1));
        data.put(2, countSessionsInPeriod(start, end, 2, 2));
        data.put(10, countSessionsInPeriod(start, end, 3, 10));
        for (int i = 10; i <= max; i += interval) {
            data.put(i + interval, countSessionsInPeriod(start, end, i + 1, i + interval));
        }
        return data;
    }

    @Override
    public long countLiveWireAlertsInInterval(Instant start, Instant end) {
        return liveWireAlertRepository.countByCreationTimeBetween(start, end);
    }

    @Override
    public long countNotificationsInInterval(Instant start, Instant end) {
        return notificationRepository.count(NotificationSpecifications.createdTimeBetween(start, end));
    }

    private int getMaxSessionsInPeriod(Instant start, Instant end) {
        List<Long> result = userLogRepository.getMaxNumberLogsInInterval(start, end, UserLogType.USER_SESSION);
        return (result == null || result.isEmpty()) ? 0 : (int) (long) result.get(0);
    }

    private int countSessionsInPeriod(Instant start, Instant end, int low, int high) {
        List<String> uids = userLogRepository.fetchUserUidsHavingUserLogTypeCountBetween(start, end, UserLogType.USER_SESSION, low, high);
        return (uids == null || uids.isEmpty()) ? 0 : uids.size();
    }

    @SuppressWarnings("unchecked")
    private List<KeywordDTO> processRawStats(Instant fromDate) {
        return entityManager.createNativeQuery("SELECT word as keyword, group_name_count, meeting_name_count, " +
                "vote_name_count, todo_count, nentry AS total_occurence " +
                "FROM ts_stat(\'SELECT to_tsvector(keyword) " +
                "FROM (SELECT g.name as keyword FROM group_profile g where g.created_date_time > '\'" +fromDate + "\'\'" +
                "UNION ALL SELECT e.name FROM event e where e.created_date_time > '\'" +fromDate + "\'\' " +
                "UNION ALL SELECT t.message from action_todo t where t.created_date_time > '\'" +fromDate  +"\'\') as keywords\')" +
                "LEFT OUTER JOIN (SELECT word AS group_name,nentry AS group_name_count " +
                "FROM ts_stat(\'SELECT to_tsvector(keyword) " +
                "FROM (SELECT g.name as keyword FROM group_profile g where g.created_date_time > '\'" + fromDate + "\'\') as keywords\'))" +
                " AS groups ON (word=group_name) LEFT OUTER JOIN (SELECT word as meeting_name,nentry as meeting_name_count " +
                "FROM ts_stat(\'SELECT to_tsvector(keyword) FROM (SELECT e.name as keyword  FROM event e " +
                "where e.created_date_time > '\'" +fromDate+"\'\' and e.type=\'\'MEETING\'\' ) as keywords\')) as meetings on(word=meeting_name)" +
                "left outer join (select word as vote_name,nentry as vote_name_count FROM ts_stat(\'SELECT to_tsvector(keyword) " +
                "FROM (SELECT e.name as keyword  FROM event e where e.created_date_time > '\'"+fromDate + "\'\' and e.type=\'\'VOTE\'\' )" +
                " as keywords\')) as votes on (word=vote_name) " +
                "left outer join (select word as action_name,nentry  as todo_count FROM ts_stat(\'SELECT to_tsvector(keyword) " +
                "from(select t.message as keyword from action_todo t where t.created_date_time > '\'" +fromDate + "\'\') " +
                "as keywords\')) as todos on(word=action_name) " +
                "ORDER BY total_occurence DESC, word limit 100", KeywordDTO.class)
                .getResultList();
    }

}
