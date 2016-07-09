package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DateTimeUtil;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by luke on 2016/02/04.
 */
@Service
@Transactional
public class AdminManager implements AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private LogBookRepository logBookRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private GroupBroker groupBroker;

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
        User user = userRepository.findOneByUid(adminUserUid);
        Role adminRole = roleRepository.findByName(BaseRoles.ROLE_SYSTEM_ADMIN).get(0);
	    if (!user.getStandardRoles().contains(adminRole)) {
		    throw new AccessDeniedException("Error! User does not have admin role");
	    }

	    Group group = groupRepository.findOneByUid(groupUid);
        group.setActive(false);
        // todo : should probably save a grouplog for this, etc
    }

	@Override
	@Transactional
	public void addMemberToGroup(String adminUserUid, String groupUid, MembershipInfo membershipInfo) {
		groupBroker.addMembers(adminUserUid, groupUid, Collections.singleton(membershipInfo), true);
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
        return logBookRepository.count();
    }

    @Override
    public Long countLogBooksRecordedInInterval(LocalDateTime start, LocalDateTime end) {
        return logBookRepository.countByCreatedDateTimeBetween(start.toInstant(ZoneOffset.UTC),
                                                               end.toInstant(ZoneOffset.UTC));
    }


}
