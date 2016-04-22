package za.org.grassroot.services.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.LogBookLogRepository;
import za.org.grassroot.core.repository.UserLogRepository;

import java.util.Objects;
import java.util.Set;

@Service
public class AsyncActionLoggerImpl implements AsyncActionLogger {
	private final Logger logger = LoggerFactory.getLogger(AsyncActionLoggerImpl.class);

	@Autowired
	private GroupLogRepository groupLogRepository;

	@Autowired
	private UserLogRepository userLogRepository;

	@Autowired
	private EventLogRepository eventLogRepository;

	@Autowired
	private LogBookLogRepository logBookLogRepository;

	@Override
	@Transactional
	@Async
	public void logGroupEvents(Set<ActionLog> actionLogs) {
		Objects.requireNonNull(actionLogs);

		logger.info("Saving {} action logs", actionLogs.size());
		for (ActionLog actionLog : actionLogs) {
			if (actionLog instanceof GroupLog) {
				groupLogRepository.save((GroupLog) actionLog);
			}
			if (actionLog instanceof UserLog) {
				userLogRepository.save((UserLog) actionLog);
			}
			if (actionLog instanceof EventLog) {
				eventLogRepository.save((EventLog) actionLog);
			}
			if (actionLog instanceof LogBookLog) {
				logBookLogRepository.save((LogBookLog) actionLog);
			}
		}
	}
}
