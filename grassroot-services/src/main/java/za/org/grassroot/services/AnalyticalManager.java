package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MaskedEventDTO;
import za.org.grassroot.core.dto.MaskedGroupDTO;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.UserRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by luke on 2016/02/04.
 */
@Service
@Transactional
public class AnalyticalManager implements AnalyticalService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticalManager.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private LogBookRepository logBookRepository;

    /*
    Helper functions to mask a list of entities
     */
    private List<MaskedUserDTO> maskListUsers(List<User> users) {
        List<MaskedUserDTO> maskedUsers = new ArrayList<>();
        for (User user : users)
            maskedUsers.add(new MaskedUserDTO(user));
        return maskedUsers;
    }

    private List<MaskedGroupDTO> maskListGroups(List<Group> groups) {
        List<MaskedGroupDTO> maskedGroups = new ArrayList<>();
        for (Group group : groups)
            maskedGroups.add(new MaskedGroupDTO(group));
        return maskedGroups;
    }

    private List<MaskedEventDTO> maskEventDTOs(List<Event> events) {
        List<MaskedEventDTO> maskedEventDTOs = new ArrayList<>();
        for (Event event : events)
            maskedEventDTOs.add(new MaskedEventDTO(event));
        return maskedEventDTOs;
    }

    @Override
    public MaskedUserDTO loadMaskedUser(Long userId) {
        return new MaskedUserDTO(userRepository.findOne(userId));
    }

    @Override
    public List<MaskedUserDTO> loadAllUsersMasked() {
        return maskListUsers(userRepository.findAll());
    }

    @Override
    public Long countAllUsers() {
        return userRepository.count();
    }

    @Override
    public List<MaskedUserDTO> loadSubsetUsersMasked(List<Long> ids) {
        return maskListUsers(userRepository.findAll(ids));
    }

    @Override
    public List<MaskedUserDTO> searchByInputNumberOrDisplayName(String inputNumber) {
        return maskListUsers(userRepository.findByDisplayNameContainingOrPhoneNumberContaining(inputNumber, inputNumber));
    }

    @Override
    public List<MaskedUserDTO> loadUsersCreatedInInterval(LocalDateTime start, LocalDateTime end) {
        return maskListUsers(userRepository.
                findByCreatedDateTimeBetweenOrderByCreatedDateTimeDesc(Timestamp.valueOf(start), Timestamp.valueOf(end)));
    }

    @Override
    public int countUsersCreatedInInterval(LocalDateTime start, LocalDateTime end) {
        return userRepository.countByCreatedDateTimeBetween(Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    @Override
    public List<MaskedUserDTO> loadUsersCreatedAndInitiatedSessionInPeriod(LocalDateTime start, LocalDateTime end) {
        return maskListUsers(userRepository.
                findByCreatedDateTimeBetweenAndHasInitiatedSession(Timestamp.valueOf(start), Timestamp.valueOf(end), true));
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
    public List<MaskedUserDTO> loadUsersCreatedInPeriodWithWebProfile(LocalDateTime start, LocalDateTime end) {
        return maskListUsers(userRepository.
                findByCreatedDateTimeBetweenAndHasWebProfile(Timestamp.valueOf(start), Timestamp.valueOf(end), true));
    }

    /**
     * SECTION: METHODS TO HANDLE GROUPS
     * */

    @Override
    public MaskedGroupDTO loadMaskedGroup(Long groupId) {
        return new MaskedGroupDTO(groupRepository.findOne(groupId));
    }

    @Override
    public Long countActiveGroups() {
        return groupRepository.countByActive(true);
    }

    @Override
    public List<MaskedGroupDTO> loadGroupsCreatedInInterval(LocalDateTime start, LocalDateTime end) {
        return maskListGroups(groupRepository.
                findByCreatedDateTimeBetweenAndActive(Timestamp.valueOf(start), Timestamp.valueOf(end), true));
    }

    @Override
    public int countGroupsCreatedInInterval(LocalDateTime start, LocalDateTime end) {
        return groupRepository.countByCreatedDateTimeBetweenAndActive(Timestamp.valueOf(start), Timestamp.valueOf(end), true);
    }

    @Override
    public MaskedEventDTO loadMaskedEvent(Long eventId) {
        return new MaskedEventDTO(eventRepository.findOne(eventId));
    }

    @Override
    public Long countAllEvents(EventType eventType) {
        return eventRepository.countByEventTypeAndEventStartDateTimeNotNullAndSendBlockedFalse(eventType);
    }

    @Override
    public List<MaskedEventDTO> loadEventsCreatedInInterval(LocalDateTime start, LocalDateTime end, EventType eventType) {
        return maskEventDTOs(eventRepository.
                findByEventTypeAndCreatedDateTimeBetweenAndSendBlockedAndEventStartDateTimeNotNull(eventType, Timestamp.valueOf(start),
                                                                                                   Timestamp.valueOf(end), false));
    }

    @Override
    public int countEventsCreatedInInterval(LocalDateTime start, LocalDateTime end, EventType eventType) {
        return eventRepository.countByEventTypeAndCreatedDateTimeBetweenAndSendBlockedFalseAndEventStartDateTimeNotNull(
                eventType, Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    /**
     * SECTION: Methods to analyze logbook entries
     */

    @Override
    public Long countAllLogBooks() {
        return logBookRepository.countByRecordedTrue();
    }

    @Override
    public Long countLogBooksRecordedInInterval(LocalDateTime start, LocalDateTime end) {
        return logBookRepository.countByCreatedDateTimeBetweenAndRecordedTrue(Timestamp.valueOf(start), Timestamp.valueOf(end));
    }


}
