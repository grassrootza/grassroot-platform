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
	public LogBook create(String userUid, String groupUid, String message, Timestamp actionByDate, int reminderMinutes, String assignedToUserUid, boolean replicateToSubgroups) {

		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);
		Objects.requireNonNull(message);
		Objects.requireNonNull(actionByDate);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		logger.info("Creating new log book: userUid={}, groupUid={}, message={}, actionByDate={}, reminderMinutes={}, assignedToUserUid={}, replicateToSubgroups={}",
				userUid, groupUid, message, actionByDate, reminderMinutes, assignedToUserUid, replicateToSubgroups);

		User assignedToUser = null;
		if (assignedToUserUid != null) {
			assignedToUser = userRepository.findOneByUid(assignedToUserUid);
		}

		LogBook logBook = createNewLogBook(user, group, message, actionByDate, reminderMinutes, assignedToUser, null);

		// note: getGroupAndSubGroups is a much faster method (a recursive query) than getSubGroups, hence use it and just skip parent
		if (replicateToSubgroups) {
			for (Group subGroup : groupRepository.findGroupAndSubGroupsById(group.getId())) {
				if (!group.equals(subGroup)) {
					createNewLogBook(user, subGroup, message, actionByDate, reminderMinutes, assignedToUser, group);
				}
			}
		}

		return logBook;
	}

	private LogBook createNewLogBook(User user, Group group, String message, Timestamp actionByDate, int reminderMinutes,
									 User assignedToUser, Group replicatedGroup) {
		int numberOfRemindersLeftToSend = 0;
		if (numberOfRemindersLeftToSend == 0) {
			numberOfRemindersLeftToSend = 3; // todo: replace with a logic based on group paid / not paid
		}

		LogBook logBook = new LogBook(user, group, message, actionByDate, reminderMinutes, assignedToUser, replicatedGroup, numberOfRemindersLeftToSend);
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
