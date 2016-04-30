package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.LogBookDTO;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.UidIdentifiableRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.async.GenericJmsTemplateProducerService;
import za.org.grassroot.services.enums.LogBookStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

@Service
public class LogBookBrokerImpl implements LogBookBroker {
	private final Logger logger = LoggerFactory.getLogger(LogBookBrokerImpl.class);

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UidIdentifiableRepository uidIdentifiableRepository;
	@Autowired
	private GroupRepository groupRepository;
	@Autowired
	private LogBookRepository logBookRepository;
	@Autowired
	private GenericJmsTemplateProducerService jmsTemplateProducerService;

	@Autowired
	PermissionBroker permissionBroker;

	@Override
	public LogBook load(String logBookUid) {
		return logBookRepository.findOneByUid(logBookUid);
	}

	@Override
	@Transactional
	public LogBook create(String userUid, JpaEntityType parentType, String parentUid, String message, LocalDateTime actionByDate, int reminderMinutes,
						  boolean replicateToSubgroups, Set<String> assignedMemberUids) {

		Objects.requireNonNull(userUid);
		Objects.requireNonNull(parentType);
		Objects.requireNonNull(parentUid);
		Objects.requireNonNull(message);
		Objects.requireNonNull(actionByDate);
		Objects.requireNonNull(assignedMemberUids);

		User user = userRepository.findOneByUid(userUid);

		LogBookContainer parent = uidIdentifiableRepository.findOneByUid(LogBookContainer.class, parentType, parentUid);

		logger.info("Creating new log book: userUid={}, parentType={}, parentUid={}, message={}, actionByDate={}, reminderMinutes={}, assignedMemberUids={}, replicateToSubgroups={}",
				userUid, parentType, parentUid, message, actionByDate, reminderMinutes, assignedMemberUids, replicateToSubgroups);

		LogBook logBook = createNewLogBook(user, parent, message, actionByDate, reminderMinutes, null);
		logBook.assignMembers(assignedMemberUids);

		if (replicateToSubgroups && parent.getJpaEntityType().equals(JpaEntityType.GROUP)) {
			Group group = logBook.resolveGroup();
			// note: getGroupAndSubGroups is a much faster method (a recursive query) than getSubGroups, hence use it and just skip parent
			List<Group> groupAndSubGroups = groupRepository.findGroupAndSubGroupsById(group.getId());
			for (Group subGroup : groupAndSubGroups) {
				if (!group.equals(subGroup)) {
					createNewLogBook(user, subGroup, message, actionByDate, reminderMinutes, group);
				}
			}
		}

		// todo: rethink sending out (tell group that to-do is recorded ... make it one reminder)

		return logBook;
	}

	@Override
	@Transactional
	public void assignMembers(String userUid, String logBookUid, Set<String> assignMemberUids) {
		Objects.requireNonNull(logBookUid);

		User user = userRepository.findOneByUid(userUid);
		LogBook logBook = logBookRepository.findOneByUid(logBookUid);

		logBook.assignMembers(assignMemberUids);
	}

	@Override
	@Transactional
	public void removeAssignedMembers(String userUid, String logBookUid, Set<String> memberUids) {
		Objects.requireNonNull(logBookUid);

		User user = userRepository.findOneByUid(userUid);
		LogBook logBook = logBookRepository.findOneByUid(logBookUid);

		logBook.removeAssignedMembers(memberUids);
	}

	private LogBook createNewLogBook(User user, LogBookContainer parent, String message, LocalDateTime actionByDate, int reminderMinutes,
									 Group replicatedGroup) {
		int numberOfRemindersLeftToSend = 0;
		if (numberOfRemindersLeftToSend == 0) {
			numberOfRemindersLeftToSend = 3; // todo: replace with a logic based on group paid / not paid
		}

		Instant convertedActionByDate = convertToSystemTime(actionByDate, getSAST());
		LogBook logBook = new LogBook(user, parent, message, convertedActionByDate, reminderMinutes, replicatedGroup, numberOfRemindersLeftToSend);
		logBook = logBookRepository.save(logBook);
		jmsTemplateProducerService.sendWithNoReply("new-logbook", new LogBookDTO(logBook));
		return logBook;
	}

	@Override
	@Transactional
	public boolean complete(String userUid, String logBookUid, LocalDateTime completionTime, String completedByUserUid) {
		Objects.requireNonNull(logBookUid);

		User completedByUser = completedByUserUid == null ? null : userRepository.findOneByUid(completedByUserUid);
		Instant completionInstant = completionTime == null ? Instant.now() : convertToSystemTime(completionTime, getSAST());

		LogBook logBook = logBookRepository.findOneByUid(logBookUid);

		logger.info("Completing logbook={}, with completion time={}", logBook.getMessage(), completionInstant);

		if (logBook.isCompleted()) {
			return false;
		}

		// todo : create a logbook log, once safe to do so (i.e., after refactor
		logBook.setCompleted(true);
		logBook.setCompletedDate(completionInstant);
		logBook.setCompletedByUser(completedByUser);
		return true;
	}

	@Override
	@Transactional(readOnly = true)
	public Page<LogBook> retrieveGroupLogBooks(String userUid, String groupUid, boolean entriesComplete, int pageNumber, int pageSize) {
		Objects.requireNonNull(userUid);

		Page<LogBook> page;
		PageRequest pgr = new PageRequest(pageNumber, pageSize);
		User user = userRepository.findOneByUid(userUid);

		if (groupUid != null) {
			Group group = groupRepository.findOneByUid(groupUid);
			permissionBroker.validateGroupPermission(user, group, null); // make sure user is part of group
			page = logBookRepository.findByGroupAndCompletedOrderByActionByDateDesc(group, entriesComplete, pgr);
		} else {
			page = logBookRepository.findByGroupMembershipsUserAndCompletedOrderByActionByDateDesc(user, entriesComplete, pgr);
		}

		return page;
	}

	@Override
	public List<Group> retrieveGroupsFromLogBooks(List<LogBook> logBooks) {
		return logBooks.stream()
				.filter(logBook -> logBook.getParent().getJpaEntityType().equals(JpaEntityType.GROUP))
				.map(logBook -> (Group) logBook.getParent())
				.collect(Collectors.toList());
	}

	@Override
	public List<LogBook> loadGroupLogBooks(String groupUid, boolean futureLogBooksOnly, LogBookStatus status) {
		Objects.requireNonNull(groupUid);

		Group group = groupRepository.findOneByUid(groupUid);
		Instant start = futureLogBooksOnly ? Instant.now() : DateTimeUtil.getEarliestInstant();
		List<LogBook> logBooks;

		switch (status) {
			case COMPLETE:
				logBooks = logBookRepository.findByGroupAndCompletedAndActionByDateGreaterThan(group, true, start);
				break;
			case INCOMPLETE:
				logBooks = logBookRepository.findByGroupAndCompletedAndActionByDateGreaterThan(group, false, start);
				break;
			case BOTH:
				logBooks = logBookRepository.findByGroupAndActionByDateGreaterThan(group, start);
				break;
			default:
				logBooks = logBookRepository.findByGroupAndActionByDateGreaterThan(group, start);
		}

		return logBooks;
	}

	@Override
	@Transactional(readOnly = true)
	public List<LogBook> loadUserLogBooks(String userUid, boolean assignedLogBooksOnly, boolean futureLogBooksOnly, LogBookStatus status) {
		Objects.requireNonNull(userUid);

		User user = userRepository.findOneByUid(userUid);
		Instant start = futureLogBooksOnly ? Instant.now() : DateTimeUtil.getEarliestInstant();
		Sort sort = new Sort(Sort.Direction.DESC, "createdDateTime");
		List<LogBook> logbooks;
		if (!assignedLogBooksOnly) {
			switch(status) {
				case COMPLETE:
					logbooks = logBookRepository.findByGroupMembershipsUserAndActionByDateBetweenAndCompleted(user, start, Instant.MAX, true, sort);
					break;
				case INCOMPLETE:
					logbooks = logBookRepository.findByGroupMembershipsUserAndActionByDateBetweenAndCompleted(user, start, Instant.MAX, false, sort);
					break;
				case BOTH:
					logbooks = logBookRepository.findByGroupMembershipsUserAndActionByDateGreaterThan(user, start);
					break;
				default:
					logbooks = logBookRepository.findByGroupMembershipsUserAndActionByDateGreaterThan(user, start);
			}
		} else {
			switch (status) {
				case COMPLETE:
					logbooks = logBookRepository.findByAssignedMembersAndActionByDateBetweenAndCompleted(user, start, Instant.MAX, true, sort);
					break;
				case INCOMPLETE:
					logbooks = logBookRepository.findByAssignedMembersAndActionByDateBetweenAndCompleted(user, start, Instant.MAX, false, sort);
					break;
				case BOTH:
					logbooks = logBookRepository.findByAssignedMembersAndActionByDateGreaterThan(user, start);
					break;
				default:
					logbooks = logBookRepository.findByGroupMembershipsUserAndActionByDateGreaterThan(user, start);
			}
		}
		return logbooks;
	}

	@Override
	@Transactional(readOnly = true)
	public LogBook fetchLogBookForUserResponse(String userUid, long daysInPast, boolean assignedLogBooksOnly) {
		LogBook lbToReturn;
		User user = userRepository.findOneByUid(userUid);
		Instant end = Instant.now();
		Instant start = Instant.now().minus(daysInPast, ChronoUnit.DAYS);
		Sort sort = new Sort(Sort.Direction.ASC, "actionByDate"); // so the most overdue come up first

		if (!assignedLogBooksOnly) {
			List<LogBook> userLbs = logBookRepository.
					findByGroupMembershipsUserAndActionByDateBetweenAndCompleted(user, start, end, false, sort);
			lbToReturn = (userLbs.isEmpty()) ? null : userLbs.get(0);
		} else {
			List<LogBook> userLbs = logBookRepository.
					findByAssignedMembersAndActionByDateBetweenAndCompleted(user, start, end, false, sort);
			lbToReturn = (userLbs.isEmpty()) ? null : userLbs.get(0);
		}
		return lbToReturn;
	}

	/*@Override
	@Transactional(readOnly = true)
	public List<LogBook> retrieveParentLogBooks(String userUid, String parentUid, JpaEntityType parentType) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(parentUid);
		Objects.requireNonNull(parentType);

		User user = userRepository.findOneByUid(userUid);
		AssignedMembersContainer parent = uidIdentifiableRepository.findOneByUid(AssignedMembersContainer.class,
																				 parentType, parentUid);

		// todo: decide if, say, group organizers on ultimate group should be able to access this
		if (!parent.getAssignedMembers().contains(user))
			throw new AccessDeniedException("Member is not assigned to this parent, so does not have read access");

		return logBookRepository.findByGroupUidOrEventUid(parentUid, parentUid);

	}*/
}
