package za.org.grassroot.services.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.repository.GroupLogRepository;

import java.util.Objects;
import java.util.Set;

@Service
public class AsyncGroupEventLoggerImpl implements AsyncGroupEventLogger {
    private final Logger logger = LoggerFactory.getLogger(AsyncGroupEventLoggerImpl.class);

    @Autowired
    private GroupLogRepository groupLogRepository;

    @Override
    @Transactional
    @Async
    public void logGroupEvents(Set<GroupLog> groupLogs) {
        Objects.requireNonNull(groupLogs);

        logger.info("Saving {} group log events", groupLogs.size());
        groupLogRepository.save(groupLogs);
    }
}
