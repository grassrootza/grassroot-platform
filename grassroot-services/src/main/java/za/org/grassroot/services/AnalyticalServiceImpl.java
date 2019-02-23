package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.services.geo.GeoLocationBroker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.springframework.data.jpa.domain.Specification.where;
import static za.org.grassroot.core.specifications.UserSpecifications.*;
import static za.org.grassroot.core.util.DateTimeUtil.*;

/**
 * Created by luke on 2016/12/12.
 */
@Service
public class AnalyticalServiceImpl implements AnalyticalService {

    private final UserRepository userRepository;
    private final UserLogRepository userLogRepository;
    private final GroupRepository groupRepository;
    private final VoteRepository voteRepository;
    private final MeetingRepository meetingRepository;
    private final TodoRepository todoRepository;
    private final GeoLocationBroker geoLocationBroker;
    private final SafetyEventRepository safetyEventRepository;

    @Autowired
    public AnalyticalServiceImpl(UserRepository userRepository, UserLogRepository userLogRepository, GroupRepository groupRepository, VoteRepository voteRepository, MeetingRepository meetingRepository,
                                 TodoRepository todoRepository, GeoLocationBroker geoLocationBroker, SafetyEventRepository safetyRepository) {
        this.userRepository = userRepository;
        this.userLogRepository = userLogRepository;
        this.groupRepository = groupRepository;
        this.voteRepository = voteRepository;
        this.meetingRepository = meetingRepository;
        this.todoRepository = todoRepository;
        this.geoLocationBroker = geoLocationBroker;
        this.safetyEventRepository = safetyRepository;
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
    public int countGroupsWithGeoLocationData() {
        return geoLocationBroker.fetchGroupsWithRecordedAverageLocations().size();
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsersThatHaveAndroidProfile(){
        return (int) userRepository.count(hasAndroidProfile());
    }

    @Override
    public int countUsersThatHaveUsedWhatsApp() {
        return (int) userLogRepository.countDistinctUsersHavingLogOnChannel(UserLogType.USER_SESSION, UserInterfaceType.WHATSAPP);
    }

    @Override
    public int countUsersWithWhatsAppOptIn() {
        return (int) userRepository.count(hasWhatsAppOptIn());
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
}
