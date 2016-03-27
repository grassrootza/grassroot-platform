package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.LogBookDTO;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.UidIdentifiableRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
	public LogBook create(String userUid, JpaEntityType parentType, String parentUid, String message, Timestamp actionByDate, int reminderMinutes,
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

	private LogBook createNewLogBook(User user, LogBookContainer parent, String message, Timestamp actionByDate, int reminderMinutes,
									 Group replicatedGroup) {
		int numberOfRemindersLeftToSend = 0;
		if (numberOfRemindersLeftToSend == 0) {
			numberOfRemindersLeftToSend = 3; // todo: replace with a logic based on group paid / not paid
		}

		LogBook logBook = new LogBook(user, parent, message, actionByDate, reminderMinutes, replicatedGroup, numberOfRemindersLeftToSend);
		logBook = logBookRepository.save(logBook);
		jmsTemplateProducerService.sendWithNoReply("new-logbook", new LogBookDTO(logBook));
		return logBook;
	}

	@Override
	@Transactional
	public void complete(String logBookUid, LocalDateTime completionTime, String completedByUserUid) {
		Objects.requireNonNull(logBookUid);

		User completedByUser = completedByUserUid == null ? null : userRepository.findOneByUid(completedByUserUid);

		LogBook logBook = logBookRepository.findOneByUid(logBookUid);

		logger.info("Completing logbook: " + logBook);

		if (logBook.isCompleted()) {
			throw new IllegalStateException("Logbook already completed: " + logBook);
		}

		logBook.setCompleted(true);
		logBook.setCompletedDate(Timestamp.valueOf(completionTime));
		logBook.setCompletedByUser(completedByUser);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<LogBook> retrieveGroupLogBooks(String userUid, String groupUid, boolean entriesComplete, int pageNumber, int pageSize) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		permissionBroker.validateGroupPermission(user, group, null); // make sure user is part of group

		return logBookRepository.findByGroupUidAndCompletedOrderByActionByDateDesc(groupUid, entriesComplete, new PageRequest(pageNumber, pageSize));
	}
}
