package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.KeywordDTO;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.geo.GeoLocationBroker;

import javax.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Created by luke on 2016/02/04.
 */
@Service
@Transactional
public class AdminManager implements AdminService {

    // private static final Logger logger = LoggerFactory.getLogger(AdminManager.class);

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

    /*
    Helper functions to mask a list of entities
     */
    private List<MaskedUserDTO> maskListUsers(List<User> users) {
        List<MaskedUserDTO> maskedUsers = new ArrayList<>();
        for (User user : users)
            maskedUsers.add(new MaskedUserDTO(user));
        return maskedUsers;
    }

    @Override
    public Long countAllUsers() {
        return userRepository.count();
    }

    @Override
    public List<MaskedUserDTO> searchByInputNumberOrDisplayName(String inputNumber) {
        return maskListUsers(userRepository.findByDisplayNameContainingOrPhoneNumberContaining(inputNumber, inputNumber));
    }

    @Override
    public int countUsersCreatedInInterval(LocalDateTime start, LocalDateTime end) {
        return userRepository.countByCreatedDateTimeBetween(Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    @Override
    public int countUsersThatHaveInitiatedSession() {
        return userRepository.countByHasInitiatedSession(true);
    }

    @Override
    public int countUsersCreatedAndInitiatedInPeriod(LocalDateTime start, LocalDateTime end) {
        return userRepository.
                countByCreatedDateTimeBetweenAndHasInitiatedSession(Timestamp.valueOf(start), Timestamp.valueOf(end), true);
    }

    @Override
    public int countUsersThatHaveWebProfile() {
        return userRepository.countByHasWebProfile(true);
    }

    @Override
    public int countUsersCreatedWithWebProfileInPeriod(LocalDateTime start, LocalDateTime end) {
        return userRepository.
                countByCreatedDateTimeBetweenAndHasWebProfile(Timestamp.valueOf(start), Timestamp.valueOf(end), true);
    }

    @Override
    public int countUsersWithGeoLocationData() {
        return geoLocationBroker.fetchUsersWithRecordedAverageLocations(LocalDate.now()).size();
    }

    @Override
    public int countGroupsWithGeoLocationData() {
        return geoLocationBroker.fetchGroupsWithRecordedAverageLocations().size();
    }

    /**
     * SECTION: METHODS TO HANDLE GROUPS
     * */

    @Override
    public Long countActiveGroups() {
        return groupRepository.countByActive(true);
    }

    @Override
    public int countGroupsCreatedInInterval(LocalDateTime start, LocalDateTime end) {
        return groupRepository.countByCreatedDateTimeBetweenAndActive(Timestamp.valueOf(start), Timestamp.valueOf(end), true);
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
    public void removeMemberFromGroup(String adminUserUid, String groupUid, String memberMsisdn) {
        // this is a 'quiet' operation, since only ever triggered if receive an "OPT OUT" notification ... hence don't group log etc
        validateAdminRole(adminUserUid);
        Group group = groupRepository.findOneByUid(groupUid);
        User member = userRepository.findByPhoneNumber(memberMsisdn);
        Membership membership = group.getMembership(member);
        group.removeMembership(membership);
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
    public Long countAllEvents(EventType eventType) {
        if (eventType.equals(EventType.MEETING)) {
            return meetingRepository.count();
        } else {
            return voteRepository.count();
        }
    }

    @Override
    public int countEventsCreatedInInterval(LocalDateTime start, LocalDateTime end, EventType eventType) {
        Instant intervalStart = DateTimeUtil.convertToSystemTime(start, DateTimeUtil.getSAST());
        Instant intervalEnd = DateTimeUtil.convertToSystemTime(end, DateTimeUtil.getSAST());
        if (eventType.equals(EventType.MEETING)) {
            return meetingRepository.countByCreatedDateTimeBetween(intervalStart, intervalEnd);
        } else {
            return voteRepository.countByCreatedDateTimeBetween(intervalStart, intervalEnd);
        }
    }

    /**
     * SECTION: Methods to analyze logbook entries
     */

    @Override
    public Long countAllLogBooks() {
        return todoRepository.count();
    }

    @Override
    public Long countLogBooksRecordedInInterval(LocalDateTime start, LocalDateTime end) {
        return todoRepository.countByCreatedDateTimeBetween(start.toInstant(ZoneOffset.UTC),
                                                               end.toInstant(ZoneOffset.UTC));
    }

    @Override
    public List<KeywordDTO> getMostFrequentKeyWords() {

        List list = entityManager.createNativeQuery("SELECT word as keyword, group_name_count, event_name_count, todo_count, nentry " +
                "  as total_occurence FROM ts_stat(\'SELECT to_tsvector(keyword)  FROM (SELECT g.name as keyword FROM group_profile " +
                "g where g.created_date_time > " +
                "CURRENT_DATE - INTERVAL \'\'3 months\'\'UNION ALL  SELECT e.name FROM event e where e.created_date_time > CURRENT_DATE - INTERVAL \'\'3 months\'\' UNION ALL Select t.message " +
                "from log_book t where t.created_date_time > CURRENT_DATE - INTERVAL \'\'3 months\'\') as keywords\')left outer join (select word as group_name,nentry as group_name_count " +
                "FROM ts_stat(\'SELECT to_tsvector(keyword) FROM (SELECT g.name as keyword " +
                " FROM group_profile g where g.created_date_time > CURRENT_DATE - INTERVAL \'\'3 months\'\')  as keywords\'))" +
                " as groups on(word=group_name) left outer join (select word as event_name,nentry as event_name_count FROM ts_stat(\'SELECT to_tsvector(keyword) " +
                "  FROM ( SELECT e.name as keyword  FROM event e where e.created_date_time > CURRENT_DATE - INTERVAL \'\'3 months\'\' ) as keywords\')) as events on(word=event_name)" +
                " left outer join (select word as action_name,nentry  as todo_count FROM ts_stat(\'SELECT to_tsvector(keyword) from(select t.message as keyword from log_book " +
                "t where t.created_date_time > CURRENT_DATE - INTERVAL \'\'3 months\'\') " +
                " as keywords\')) as todos on(word=action_name) ORDER BY nentry DESC, ndoc DESC, word limit 50", KeywordDTO.class)
                .getResultList();

        return list;
    }

    /**
	 * SECTION : Methods to analyze use stats
     */

    @Override
    public long getMaxSessionsInLastMonth() {
        Instant end = Instant.now();
        Instant start = end.minus(30, ChronoUnit.DAYS);
        List<Long> result = userLogRepository.getMaxNumberLogsInInterval(start, end, UserLogType.USER_SESSION);
        return (result == null || result.isEmpty()) ? 0 : result.get(0);
    }

    @Override
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

}
