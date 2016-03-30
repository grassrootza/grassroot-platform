package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.UserLogRepository;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Created by luke on 2016/02/22.
 */
@Service
public class AsyncUserLoggerImpl implements AsyncUserLogger {

    private static final Logger log = LoggerFactory.getLogger(AsyncUserLoggerImpl.class);

    @Autowired
    UserLogRepository userLogRepository;

    @Async
    @Override
    @Transactional
    public void recordUserLog(String userUid, UserLogType userLogType, String description) {
        UserLog userLog = new UserLog(userUid, userLogType, description);
        userLogRepository.saveAndFlush(userLog);
    }

    @Async
    @Override
    @Transactional
    public void logUserCreation(Set<String> userUids, String description) {
        Objects.requireNonNull(userUids);
        log.info("Saving this many user logs: {}", userUids.size());
        Set<UserLog> logs = new HashSet<>();
        for (String uid : userUids) {
            logs.add(new UserLog(uid, UserLogType.CREATED_IN_DB, description));
        }
        userLogRepository.save(logs);
    }
}
