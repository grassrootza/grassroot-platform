package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
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

	@Override
	@Transactional
	public LogBook create(String userUid, String groupUid, boolean replicateToSubgroups, String message, Timestamp actionByDate,
						  String assignedToUserUid, int reminderMinutes) {

		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);
		Objects.requireNonNull(message);
		Objects.requireNonNull(actionByDate);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		User assignedToUser = null;
		if (assignedToUserUid != null) {
			assignedToUser = userRepository.findOneByUid(assignedToUserUid);
		}

		LogBook logBook = new LogBook(user, group, null, message, actionByDate, assignedToUser, reminderMinutes);
		logBook = logBookRepository.save(logBook);

		// note: getGroupAndSubGroups is a much faster method (a recursive query) than getSubGroups, hence use it and just skip parent
		if (replicateToSubgroups) {
			for (Group subGroup : groupRepository.findGroupAndSubGroupsById(group.getId())) {
				if (!group.equals(subGroup)) {
					LogBook subGroupLogBook = new LogBook(user, subGroup, group, message, actionByDate, assignedToUser, reminderMinutes);
					logBookRepository.save(subGroupLogBook);
				}
			}
		}

		return logBook;
	}

	@Override
	@Transactional
	public void complete(String logBookUid, Timestamp completionTime, String completedByUserUid) {
		Objects.requireNonNull(logBookUid);

		User completedByUser = completedByUserUid == null ? null : userRepository.findOneByUid(completedByUserUid);

		LogBook logBook = logBookRepository.findOneByUid(logBookUid);

		if (logBook.isCompleted()) {
			throw new IllegalStateException("Logbook already completed: " + logBook);
		}

		logBook.setCompleted(true);
		logBook.setCompletedDate(completionTime);
		logBook.setCompletedByUser(completedByUser);
	}
}
