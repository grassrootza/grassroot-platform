package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBookRequest;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.LogBookRequestRepository;
import za.org.grassroot.core.repository.UserRepository;

import java.util.Objects;

@Service
public class LogBookRequestBrokerImpl implements LogBookRequestBroker {
	private final Logger logger = LoggerFactory.getLogger(LogBookRequestBrokerImpl.class);

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private GroupRepository groupRepository;
	@Autowired
	private LogBookBroker logBookBroker;
	@Autowired
	private LogBookRequestRepository logBookRequestRepository;

	@Override
	@Transactional
	public LogBookRequest create(String userUid, String groupUid) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		LogBookRequest request = LogBookRequest.makeEmpty(user, group);

		logBookRequestRepository.save(request);

		return request;
	}

	@Override
	@Transactional
	public void finish(String logBookUid) {
		Objects.requireNonNull(logBookUid);

		LogBookRequest logBookRequest = logBookRequestRepository.findOneByUid(logBookUid);

		String assignedToUserUid = logBookRequest.getAssignedToUser() == null ? null : logBookRequest.getAssignedToUser().getUid();
		logBookBroker.create(logBookRequest.getCreatedByUser().getUid(), logBookRequest.getGroup().getUid(), logBookRequest.isReplicateToSubgroups(),
				logBookRequest.getMessage(), logBookRequest.getActionByDate(), assignedToUserUid, logBookRequest.getReminderMinutes());

		logBookRequestRepository.delete(logBookRequest);
	}
}
