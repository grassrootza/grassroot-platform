package za.org.grassroot.services;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.KeywordDTO;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.exception.MemberNotPartOfGroupException;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.GroupBroker;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;
import static za.org.grassroot.services.specifications.UserSpecifications.*;

/**
 * Created by luke on 2016/02/04.
 */
@Service
public class AdminManager implements AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminManager.class);

    @Value("${grassroot.keywords.excluded:''}")
    String listOfWordsToExcludeFromStat;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserLogRepository userLogRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private GroupLogRepository groupLogRepository;

    @Autowired
    private GeoLocationBroker geoLocationBroker;

    @Autowired
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Long countAllUsers() {
        return userRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaskedUserDTO> searchByInputNumberOrDisplayName(String inputNumber) {
        return maskListUsers(userRepository.findByDisplayNameContainingOrPhoneNumberContaining(inputNumber, inputNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsersCreatedInInterval(LocalDateTime start, LocalDateTime end) {
        return (int) userRepository.count(Specifications
                .where(createdAfter(convertToSystemTime(start, getSAST())))
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
        return (int) userRepository.count(Specifications.where(hasInitiatedSession())
                .and(createdAfter(convertToSystemTime(start, getSAST())))
                .and(createdBefore(convertToSystemTime(end, getSAST()))));
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsersThatHaveWebProfile() {
        return (int) userRepository.count(hasAndroidProfile());
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsersCreatedWithWebProfileInPeriod(LocalDateTime start, LocalDateTime end) {
        return (int) userRepository.count(Specifications.where(hasWebProfile())
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
        return (int) userRepository.count(Specifications.where(hasAndroidProfile())
                        .and(createdAfter(convertToSystemTime(start, getSAST())))
                        .and(createdBefore(convertToSystemTime(end, getSAST()))));
    }

    /**
     * SECTION: METHODS TO HANDLE GROUPS
     */

    @Override
    @Transactional(readOnly = true)
    public Long countActiveGroups() {
        return groupRepository.countByActive(true);
    }

    @Override
    @Transactional(readOnly = true)
    public int countGroupsCreatedInInterval(LocalDateTime start, LocalDateTime end) {
        return groupRepository.countByCreatedDateTimeBetweenAndActive(convertToSystemTime(start, getSAST()),
                convertToSystemTime(end, getSAST()), true);
    }

    @Override
    @Transactional
    public void deactiveGroup(String adminUserUid, String groupUid) {
        validateAdminRole(adminUserUid);

        User user = userRepository.findOneByUid(adminUserUid);
        Group group = groupRepository.findOneByUid(groupUid);
        group.setActive(false);

        groupLogRepository.save(new GroupLog(group, user, GroupLogType.GROUP_REMOVED, null, "Deactivated by system admin"));
    }

    @Override
    @Transactional
    public void addMemberToGroup(String adminUserUid, String groupUid, MembershipInfo membershipInfo) {
        validateAdminRole(adminUserUid);
        groupBroker.addMembers(adminUserUid, groupUid, Collections.singleton(membershipInfo), true);
    }

    @Override
    @Transactional
    public void removeMemberFromGroup(String adminUserUid, String groupUid, String memberMsisdn) {
        // this is a 'quiet' operation, since only ever triggered if receive an "OPT OUT" notification ... hence don't group log etc
        validateAdminRole(adminUserUid);
        Group group = groupRepository.findOneByUid(groupUid);
        User member = userRepository.findByPhoneNumber(memberMsisdn);
        if (member == null) {
            throw new NoSuchUserException("User with that phone number does not exist");
        }
        Membership membership = group.getMembership(member);
        if (membership == null) {
            throw new MemberNotPartOfGroupException();
        } else {
            group.removeMembership(membership);
        }
    }

    @Override
    @Transactional
    public void removeUserFromAllGroups(String adminUserUid, String userUid) {
        validateAdminRole(adminUserUid);
        User user = userRepository.findOneByUid(userUid);
        Set<Membership> memberships = user.getMemberships();
        logger.info("admin user now removing user from {} groups", memberships.size());
        for (Membership membership : memberships) {
            Group group = membership.getGroup();
            group.removeMembership(membership); // concurrency?
        }
    }

    private void validateAdminRole(String adminUserUid) {
        User admin = userRepository.findOneByUid(adminUserUid);
        Role adminRole = roleRepository.findByName(BaseRoles.ROLE_SYSTEM_ADMIN).get(0);
        if (!admin.getStandardRoles().contains(adminRole)) {
            throw new AccessDeniedException("Error! User does not have admin role");
        }
    }

    /**
     * SECTION: METHODS TO HANDLE EVENTS
     */

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

    /**
     * SECTION: Methods to analyze action to-do entries
     */

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

    /**
     * SECTION : Methods to analyze use stats
     */

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

    private int getMaxSessionsInPeriod(Instant start, Instant end) {
        List<Long> result = userLogRepository.getMaxNumberLogsInInterval(start, end, UserLogType.USER_SESSION);
        return (result == null || result.isEmpty()) ? 0 : (int) (long) result.get(0);
    }

    private int countSessionsInPeriod(Instant start, Instant end, int low, int high) {
        List<String> uids = userLogRepository.fetchUserUidsHavingUserLogTypeCountBetween(start, end, UserLogType.USER_SESSION, low, high);
        return (uids == null || uids.isEmpty()) ? 0 : uids.size();
    }

    /*
   Helper functions to mask a list of entities
    */
    private List<MaskedUserDTO> maskListUsers(List<User> users) {
        List<MaskedUserDTO> maskedUsers = new ArrayList<>();
        for (User user : users)
            maskedUsers.add(new MaskedUserDTO(user));
        return maskedUsers;
    }

}
