package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.LogBookDTO;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.Set;

@Service
public class LogBookBrokerImpl implements LogBookBroker {
	private final Logger logger = LoggerFactory.getLogger(LogBookBrokerImpl.class);

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private GroupRepository groupRepository;
	@Autowired
	private LogBookRepository logBookRepository;
	@Autowired
	private GenericJmsTemplateProducerService jmsTemplateProducerService;

	@Override
	@Transactional
	public LogBook create(String userUid, String groupUid, String message, Timestamp actionByDate, int reminderMinutes,
						  boolean replicateToSubgroups, Set<String> assignedMemberUids) {

		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);
		Objects.requireNonNull(message);
		Objects.requireNonNull(actionByDate);
		Objects.requireNonNull(assignedMemberUids);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		logger.info("Creating new log book: userUid={}, groupUid={}, message={}, actionByDate={}, reminderMinutes={}, assignedMemberUids={}, replicateToSubgroups={}",
				userUid, groupUid, message, actionByDate, reminderMinutes, assignedMemberUids, replicateToSubgroups);

		LogBook logBook = createNewLogBook(user, group, message, actionByDate, reminderMinutes, null);
		logBook.assignMembers(assignedMemberUids);

		// note: getGroupAndSubGroups is a much faster method (a recursive query) than getSubGroups, hence use it and just skip parent
		if (replicateToSubgroups) {
			for (Group subGroup : groupRepository.findGroupAndSubGroupsById(group.getId())) {
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

	private LogBook createNewLogBook(User user, Group group, String message, Timestamp actionByDate, int reminderMinutes,
									 Group replicatedGroup) {
		int numberOfRemindersLeftToSend = 0;
		if (numberOfRemindersLeftToSend == 0) {
			numberOfRemindersLeftToSend = 3; // todo: replace with a logic based on group paid / not paid
		}

		LogBook logBook = new LogBook(user, group, message, actionByDate, reminderMinutes, replicatedGroup, numberOfRemindersLeftToSend);
		logBook = logBookRepository.save(logBook);
		jmsTemplateProducerService.sendWithNoReply("new-logbook", new LogBookDTO(logBook));
		return logBook;
	}

	@Override
	@Transactional
	public void complete(String logBookUid, Timestamp completionTime, String completedByUserUid) {
		Objects.requireNonNull(logBookUid);

		User completedByUser = completedByUserUid == null ? null : userRepository.findOneByUid(completedByUserUid);

		LogBook logBook = logBookRepository.findOneByUid(logBookUid);

		logger.info("Completing logbook: " + logBook);

		if (logBook.isCompleted()) {
			throw new IllegalStateException("Logbook already completed: " + logBook);
		}

		logBook.setCompleted(true);
		logBook.setCompletedDate(completionTime);
		logBook.setCompletedByUser(completedByUser);
	}
}
